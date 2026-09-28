package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.dto.response.CategoryResponse;
import com.laundry.api.service.CategoryService;
import com.laundry.api.utils.CurrentUserUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 衣物类别控制器
 */
@Tag(name = "衣物类别", description = "衣物类别查询、搜索（按价目表6大板块）")
@RestController
@RequestMapping("/api/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CurrentUserUtil user;

    public record CategorySaveRequest(Long id,@NotBlank @Size(max=50) String categoryGroup,
        @NotBlank @Size(max=100) String name,@NotNull @DecimalMin("0") BigDecimal price,
        @NotBlank @Size(max=10) String unit,Integer sortOrder,Integer status) {}

    @Operation(summary = "获取全部类别（按分组排序）", description = "返回所有启用的类别列表，按衣物类、鞋类、家纺卧室、单烫类、皮衣奢饰品、包包类顺序排列")
    @GetMapping("/list")
    public Result<List<CategoryResponse>> list() {
        return Result.success(categoryService.listAll());
    }

    @Operation(summary = "关键字搜索类别", description = "按项目名称（如衬衫、运动鞋）模糊匹配搜索")
    @GetMapping("/search")
    public Result<List<CategoryResponse>> search(
            @Parameter(description = "关键字") @RequestParam(required = false) String keyword) {
        return Result.success(categoryService.search(keyword));
    }

    @GetMapping("/admin-list")
    public Result<List<Map<String,Object>>> adminList() { requireAdmin(); return Result.success(jdbc.queryForList("""
        SELECT id,category_group AS categoryGroup,category_level2 AS name,price,unit,sort_order AS sortOrder,status,update_time AS updateTime
        FROM clothes_category ORDER BY category_group,sort_order,id
        """)); }

    @PostMapping("/save")
    @Transactional
    public Result<Map<String,Object>> save(@Valid @RequestBody CategorySaveRequest req) {
        requireAdmin();
        if(!Set.of("CLOTHES","SHOES","HOME","IRON","LEATHER","BAG").contains(req.categoryGroup())) throw new IllegalArgumentException("衣物分组不正确");
        LocalDateTime now=LocalDateTime.now(); int status=req.status()==null?1:req.status(); int sort=req.sortOrder()==null?0:req.sortOrder();
        if(!Set.of(0,1).contains(status)) throw new IllegalArgumentException("状态不正确");
        if(req.id()==null){jdbc.update("""
            INSERT INTO clothes_category(category_group,category_level1,category_level2,price,unit,price_mode,sort_order,status,create_time,update_time)
            VALUES (?,?,?,?,?,1,?,?,?,?)
            """,req.categoryGroup(),req.name().trim(),req.name().trim(),req.price(),req.unit().trim(),sort,status,now,now);
            Long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);return Result.success(Map.of("id",id));}
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM clothes_category WHERE id=? FOR UPDATE",req.id());
        if(rows.size()!=1||"CUSTOM".equals(rows.get(0).get("category_group"))) throw new IllegalArgumentException("类别不存在或为系统保留类别");
        Map<String,Object> old=rows.get(0);jdbc.update("""
            UPDATE clothes_category SET category_group=?,category_level1=?,category_level2=?,price=?,unit=?,sort_order=?,status=?,update_time=? WHERE id=?
            """,req.categoryGroup(),req.name().trim(),req.name().trim(),req.price(),req.unit().trim(),sort,status,now,req.id());
        if(((BigDecimal)old.get("price")).compareTo(req.price())!=0)jdbc.update("""
            INSERT INTO category_price_log(category_id,category_name,old_price,new_price,operator_id,operator_name,operate_time)
            VALUES (?,?,?,?,?,?,?)
            """,req.id(),req.name().trim(),old.get("price"),req.price(),user.getOperatorId(),user.getOperatorName(),now);
        return Result.success(Map.of("id",req.id()));
    }

    private void requireAdmin(){if(!"ADMIN".equals(user.getUser().getRole()))throw new org.springframework.security.access.AccessDeniedException("仅管理员可维护衣物定价");}
}
