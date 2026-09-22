package com.laundry.api.controller;

import com.laundry.api.common.Result;
import com.laundry.api.dto.response.CategoryResponse;
import com.laundry.api.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 衣物类别控制器
 */
@Tag(name = "衣物类别", description = "衣物类别查询、搜索（按价目表6大板块）")
@RestController
@RequestMapping("/api/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

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
}
