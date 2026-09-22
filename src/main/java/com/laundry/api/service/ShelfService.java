package com.laundry.api.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ShelfService {
    private final JdbcTemplate jdbc;
    public ShelfService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public Map<String,Object> overview(String store){ensureConfig(store,"系统");int max=max(store);Map<String,Object> r=new LinkedHashMap<>();r.put("maxShelfNo",max);
        r.put("occupied",count(store,"OCCUPIED"));r.put("reserved",count(store,"RESERVED"));r.put("disabled",count(store,"DISABLED"));
        r.put("free",max-((Number)r.get("occupied")).intValue()-((Number)r.get("reserved")).intValue()-((Number)r.get("disabled")).intValue());return r;}

    @Transactional
    public Map<String,Object> allocate(String barcode,Integer preferred,String mode,String store,Long operatorId,String operatorName){
        String normalized=mode==null?"SHELF":mode.trim().toUpperCase();if(!List.of("SHELF","MOVE").contains(normalized))throw new IllegalArgumentException("无效操作类型");
        jdbc.update("DELETE FROM shelf_position WHERE store_code=? AND status='RESERVED' AND reserved_until<?",store,LocalDateTime.now());
        Map<String,Object> item=item(barcode,store,true);int shelfStatus=((Number)item.get("shelf_status")).intValue();
        if(!"BACK_TO_STORE".equals(item.get("status")))throw new IllegalArgumentException("只有已完成回店签收的衣物可以上架");
        if("SHELF".equals(normalized)&&shelfStatus==1)throw new IllegalArgumentException("衣物已在 "+item.get("shelf_code")+" 号位置");
        if("MOVE".equals(normalized)&&shelfStatus!=1)throw new IllegalArgumentException("衣物尚未上架，不能移架");
        jdbc.update("DELETE FROM shelf_position WHERE store_code=? AND status='RESERVED' AND order_item_id=?",store,item.get("id"));
        int max=max(store);Set<Integer> unavailable=new HashSet<>(jdbc.query("SELECT shelf_no FROM shelf_position WHERE store_code=?",(rs,n)->rs.getInt(1),store));
        Integer chosen=preferred;
        if(chosen!=null&&(chosen<1||chosen>max))throw new IllegalArgumentException("货架号必须在1至"+max+"之间");
        if(chosen!=null&&unavailable.contains(chosen))throw new IllegalArgumentException(chosen+"号位置当前不可用");
        if(chosen==null){List<Integer> sameOrder=jdbc.query("SELECT shelf_no FROM shelf_position WHERE store_code=? AND order_id=? AND status='OCCUPIED' ORDER BY shelf_no",(rs,n)->rs.getInt(1),store,item.get("order_id"));
            int remaining=jdbc.queryForObject("SELECT COUNT(*) FROM order_item WHERE order_id=? AND status='BACK_TO_STORE' AND shelf_status<>1",Integer.class,item.get("order_id"));
            chosen=chooseShelf(max,unavailable,sameOrder,remaining,ThreadLocalRandom.current().nextInt(Math.max(1,max)));
        }
        if(chosen==null)throw new IllegalArgumentException("当前货架已满，请释放位置或由管理员扩大容量");
        try{LocalDateTime now=LocalDateTime.now();jdbc.update("""
            INSERT INTO shelf_position(store_code,shelf_no,status,order_item_id,barcode,order_id,reservation_mode,
              reserved_until,operator_id,operator_name,update_time) VALUES (?,?,'RESERVED',?,?,?,?,?,?,?,?)
            """,store,chosen,item.get("id"),item.get("barcode"),item.get("order_id"),normalized,now.plusMinutes(5),operatorId,operatorName,now);
        }catch(DuplicateKeyException e){throw new IllegalArgumentException("该位置刚被其他店员占用，请重新分配");}
        return Map.of("shelfNo",chosen,"barcode",item.get("barcode"),"orderNo",item.get("order_no"),"categoryName",item.get("category_name"),"mode",normalized,"expiresInSeconds",300);
    }

    @Transactional
    public Map<String,Object> confirm(String barcode,int shelfNo,String store,Long operatorId,String operatorName){
        List<Map<String,Object>> positions=jdbc.queryForList("SELECT * FROM shelf_position WHERE store_code=? AND shelf_no=? FOR UPDATE",store,shelfNo);
        if(positions.size()!=1||!"RESERVED".equals(positions.get(0).get("status")))throw new IllegalArgumentException("预留位置不存在或已经失效，请重新扫描");
        Map<String,Object> p=positions.get(0);if(!barcode.trim().equals(p.get("barcode")))throw new IllegalArgumentException("衣物码与预留位置不一致");
        if(toTime(p.get("reserved_until")).isBefore(LocalDateTime.now())){jdbc.update("DELETE FROM shelf_position WHERE store_code=? AND shelf_no=?",store,shelfNo);throw new IllegalArgumentException("预留已超时，请重新扫描");}
        Map<String,Object> item=item(barcode,store,true);String mode=String.valueOf(p.get("reservation_mode"));Integer old=parseShelf(item.get("shelf_code"));LocalDateTime now=LocalDateTime.now();
        if("MOVE".equals(mode)){if(old==null)throw new IllegalArgumentException("原货架位置不存在");jdbc.update("DELETE FROM shelf_position WHERE store_code=? AND shelf_no=? AND status='OCCUPIED' AND order_item_id=?",store,old,item.get("id"));}
        jdbc.update("UPDATE shelf_position SET status='OCCUPIED',reservation_mode=NULL,reserved_until=NULL,operator_id=?,operator_name=?,update_time=? WHERE store_code=? AND shelf_no=?",operatorId,operatorName,now,store,shelfNo);
        jdbc.update("UPDATE order_item SET shelf_status=1,shelf_code=?,on_shelf_time=?,off_shelf_time=NULL,update_time=? WHERE id=?",String.valueOf(shelfNo),now,now,item.get("id"));
        jdbc.update("""
          INSERT INTO shelf_operation_log(store_code,order_id,order_item_id,order_no,barcode,action,from_shelf_no,to_shelf_no,
            operator_id,operator_name,operate_time) VALUES (?,?,?,?,?,?,?,?,?,?,?)
          """,store,item.get("order_id"),item.get("id"),item.get("order_no"),item.get("barcode"),"MOVE".equals(mode)?"MOVE":"SHELF",old,shelfNo,operatorId,operatorName,now);
        return Map.of("barcode",barcode,"shelfNo",shelfNo,"status","OCCUPIED");
    }

    public List<Map<String,Object>> search(String keyword,String store){String t=keyword==null?"":keyword.trim();if(t.isEmpty())throw new IllegalArgumentException("请输入衣物码、订单号、手机号或货架号");Integer no=t.matches("\\d{1,4}")?Integer.valueOf(t):null;
        return jdbc.queryForList("""
          SELECT i.id,i.barcode,i.category_name AS categoryName,o.order_no AS orderNo,o.customer_phone AS phone,
            i.shelf_code AS shelfNo,i.on_shelf_time AS onShelfTime,i.status AS itemStatus
          FROM order_item i JOIN laundry_order o ON o.id=i.order_id
          WHERE o.store_code=? AND i.shelf_status=1 AND (i.barcode=? OR o.order_no=? OR o.customer_phone=? OR (? IS NOT NULL AND i.shelf_code=CAST(? AS CHAR)))
          ORDER BY CAST(i.shelf_code AS UNSIGNED)
          """,store,t,t,t,no,no);
    }

    @Transactional public void cancelReservation(String barcode,String store){jdbc.update("DELETE FROM shelf_position WHERE store_code=? AND barcode=? AND status='RESERVED'",store,barcode.trim());}
    @Transactional public void resize(int newMax,String store,String operator,boolean admin){if(!admin)throw new AccessDeniedException("仅管理员可调整货架容量");if(newMax<1||newMax>9999)throw new IllegalArgumentException("货架容量必须在1至9999之间");
        Integer used=jdbc.queryForObject("SELECT COUNT(*) FROM shelf_position WHERE store_code=? AND shelf_no>? AND status IN ('OCCUPIED','RESERVED')",Integer.class,store,newMax);if(used!=null&&used>0)throw new IllegalArgumentException("超出新容量的范围内仍有衣物或预留位置，请先移架");
        jdbc.update("DELETE FROM shelf_position WHERE store_code=? AND shelf_no>? AND status='DISABLED'",store,newMax);ensureConfig(store,operator);jdbc.update("UPDATE shelf_config SET max_shelf_no=?,update_operator=?,update_time=? WHERE store_code=?",newMax,operator,LocalDateTime.now(),store);}
    @Transactional public void setDisabled(int shelfNo,boolean disabled,String store,Long operatorId,String operatorName,boolean admin){if(!admin)throw new AccessDeniedException("仅管理员可停用货架位置");int max=max(store);if(shelfNo<1||shelfNo>max)throw new IllegalArgumentException("货架号超出当前容量");
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM shelf_position WHERE store_code=? AND shelf_no=? FOR UPDATE",store,shelfNo);if(disabled){if(!rows.isEmpty())throw new IllegalArgumentException("该位置已占用、预留或停用");jdbc.update("INSERT INTO shelf_position(store_code,shelf_no,status,operator_id,operator_name,update_time) VALUES (?,?,'DISABLED',?,?,?)",store,shelfNo,operatorId,operatorName,LocalDateTime.now());}else{int n=jdbc.update("DELETE FROM shelf_position WHERE store_code=? AND shelf_no=? AND status='DISABLED'",store,shelfNo);if(n!=1)throw new IllegalArgumentException("该位置没有停用");}}

    static Integer chooseShelf(int max,Set<Integer> unavailable,List<Integer> sameOrder,int groupSize,int seed){
        if(!sameOrder.isEmpty()){int high=Collections.max(sameOrder)+1,low=Collections.min(sameOrder)-1;if(high<=max&&!unavailable.contains(high))return high;if(low>=1&&!unavailable.contains(low))return low;}
        int need=Math.max(1,Math.min(groupSize,max));List<Integer> starts=new ArrayList<>();for(int start=1;start+need-1<=max;start++){boolean free=true;for(int n=start;n<start+need;n++)if(unavailable.contains(n)){free=false;break;}if(free)starts.add(start);}
        if(!starts.isEmpty())return starts.get(Math.floorMod(seed,starts.size()));for(int offset=0;offset<max;offset++){int n=1+Math.floorMod(seed+offset,max);if(!unavailable.contains(n))return n;}return null;
    }
    private Map<String,Object> item(String barcode,String store,boolean lock){List<Map<String,Object>> rows=jdbc.queryForList("""
      SELECT i.*,o.store_code FROM order_item i JOIN laundry_order o ON o.id=i.order_id WHERE i.barcode=? AND o.store_code=?
      """+(lock?" FOR UPDATE":""),barcode.trim(),store);if(rows.size()!=1)throw new IllegalArgumentException("衣物码不存在或不属于当前门店");return rows.get(0);}
    private void ensureConfig(String store,String operator){jdbc.update("INSERT IGNORE INTO shelf_config(store_code,max_shelf_no,update_operator,update_time) VALUES (?,1700,?,?)",store,operator,LocalDateTime.now());}
    private int max(String store){ensureConfig(store,"系统");return jdbc.queryForObject("SELECT max_shelf_no FROM shelf_config WHERE store_code=?",Integer.class,store);}
    private int count(String store,String status){return jdbc.queryForObject("SELECT COUNT(*) FROM shelf_position WHERE store_code=? AND status=?",Integer.class,store,status);}
    private Integer parseShelf(Object value){if(value==null||String.valueOf(value).isBlank())return null;try{return Integer.valueOf(String.valueOf(value));}catch(Exception e){return null;}}
    private LocalDateTime toTime(Object value){if(value instanceof LocalDateTime t)return t;if(value instanceof java.sql.Timestamp t)return t.toLocalDateTime();throw new IllegalArgumentException("预留时间格式异常");}
}
