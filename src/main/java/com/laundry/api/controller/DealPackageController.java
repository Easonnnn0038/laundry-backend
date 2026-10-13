package com.laundry.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laundry.api.common.Result;
import com.laundry.api.utils.CurrentUserUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/deal-packages")
public class DealPackageController {
    private static final Set<String> GROUPS = Set.of("CLOTHES","SHOES","HOME","IRON","LEATHER","BAG");
    private static final Set<String> STATUSES = Set.of("ACTIVE","INACTIVE");
    private final JdbcTemplate jdbc; private final CurrentUserUtil user; private final ObjectMapper json;
    public DealPackageController(JdbcTemplate jdbc, CurrentUserUtil user, ObjectMapper json) {
        this.jdbc=jdbc; this.user=user; this.json=json;
    }
    public record SaveRequest(Long id,@NotBlank @Pattern(regexp="[A-Z0-9_]{2,40}") String code,
        @NotBlank @Size(max=100) String name,@Size(max=500) String description,
        @NotBlank @Size(max=30) String platform,@NotNull @DecimalMin("0") BigDecimal price,
        @NotNull @DecimalMin("0") BigDecimal originalPrice,@NotNull @Min(1) @Max(50) Integer requiredQuantity,
        LocalDateTime validFrom,LocalDateTime validTo,@NotBlank String status,boolean featured,
        @NotNull @Min(0) Integer sortOrder,@NotEmpty List<String> categoryGroups) {}

    @GetMapping public Result<List<Map<String,Object>>> list(){requireAdmin();List<Map<String,Object>> rows=jdbc.queryForList("""
        SELECT id,code,name,description,platform,price,original_price AS originalPrice,required_quantity AS requiredQuantity,
          valid_from AS validFrom,valid_to AS validTo,status,featured,sort_order AS sortOrder,update_time AS updateTime
        FROM deal_package ORDER BY sort_order,id
        """);for(Map<String,Object> row:rows)row.put("categoryGroups",jdbc.queryForList("SELECT category_group FROM deal_package_scope WHERE package_id=? ORDER BY category_group",String.class,row.get("id")));return Result.success(rows);}

    @PostMapping @Transactional public Result<Map<String,Object>> save(@Valid @RequestBody SaveRequest req)throws Exception{
        requireAdmin();validate(req);Map<String,Object> before=null;long id;
        if(req.id()==null){jdbc.update("""
            INSERT INTO deal_package(code,name,description,platform,price,original_price,required_quantity,valid_from,valid_to,status,featured,sort_order)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
            """,req.code(),req.name().trim(),blank(req.description()),req.platform().toUpperCase(),req.price(),req.originalPrice(),req.requiredQuantity(),req.validFrom(),req.validTo(),req.status(),req.featured(),req.sortOrder());id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);}
        else{id=req.id();List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM deal_package WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw new IllegalArgumentException("套餐不存在");before=rows.get(0);jdbc.update("""
            UPDATE deal_package SET code=?,name=?,description=?,platform=?,price=?,original_price=?,required_quantity=?,valid_from=?,valid_to=?,status=?,featured=?,sort_order=? WHERE id=?
            """,req.code(),req.name().trim(),blank(req.description()),req.platform().toUpperCase(),req.price(),req.originalPrice(),req.requiredQuantity(),req.validFrom(),req.validTo(),req.status(),req.featured(),req.sortOrder(),id);jdbc.update("DELETE FROM deal_package_scope WHERE package_id=?",id);}
        for(String group:req.categoryGroups())jdbc.update("INSERT INTO deal_package_scope(package_id,category_group) VALUES(?,?)",id,group);
        Map<String,Object> after=jdbc.queryForMap("SELECT * FROM deal_package WHERE id=?",id);jdbc.update("""
            INSERT INTO deal_package_audit(package_id,action,operator_id,operator_name,before_snapshot,after_snapshot)
            VALUES(?,?,?,?,CAST(? AS JSON),CAST(? AS JSON))
            """,id,req.id()==null?"CREATE":"UPDATE",user.getOperatorId(),user.getOperatorName(),before==null?null:json.writeValueAsString(before),json.writeValueAsString(after));return Result.success(Map.of("id",id));}

    private void validate(SaveRequest req){if(req.originalPrice().compareTo(req.price())<0)throw new IllegalArgumentException("原价不能低于套餐价");if(req.validFrom()!=null&&req.validTo()!=null&&!req.validTo().isAfter(req.validFrom()))throw new IllegalArgumentException("结束时间必须晚于开始时间");if(!STATUSES.contains(req.status()))throw new IllegalArgumentException("套餐状态不正确");if(req.categoryGroups().stream().anyMatch(g->!GROUPS.contains(g)))throw new IllegalArgumentException("适用品类不正确");}
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    private void requireAdmin(){if(!"ADMIN".equals(user.getUser().getRole()))throw new AccessDeniedException("仅管理员可维护团购套餐");}
}
