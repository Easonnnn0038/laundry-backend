package com.laundry.api.service;

import com.laundry.api.dto.response.CategoryResponse;

import java.util.List;
import java.util.Map;

/**
 * 衣物类别服务接口
 */
public interface CategoryService {

    /**
     * 获取所有启用的类别列表（按分组+排序号）
     */
    List<CategoryResponse> listAll();

    /**
     * 按板块分组返回（Map key=板块group，value=该板块的类别列表）
     * 前端顶部Tab切换用
     */
    Map<String, List<CategoryResponse>> listGrouped();

    /**
     * 根据关键字搜索类别（匹配项目名称）
     */
    List<CategoryResponse> search(String keyword);
}
