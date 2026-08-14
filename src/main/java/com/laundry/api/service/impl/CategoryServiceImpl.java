package com.laundry.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laundry.api.dto.response.CategoryResponse;
import com.laundry.api.entity.ClothesCategory;
import com.laundry.api.mapper.ClothesCategoryMapper;
import com.laundry.api.service.CategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 衣物类别服务实现
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryServiceImpl.class);

    /** 板块分组 中文标签映射（前端显示用） */
    private static final Map<String, String> GROUP_LABEL_MAP = new LinkedHashMap<>();
    static {
        GROUP_LABEL_MAP.put("CLOTHES", "衣物类");
        GROUP_LABEL_MAP.put("SHOES",   "鞋类");
        GROUP_LABEL_MAP.put("HOME",    "家纺卧室");
        GROUP_LABEL_MAP.put("IRON",    "单烫类");
        GROUP_LABEL_MAP.put("LEATHER", "皮衣/奢饰品");
        GROUP_LABEL_MAP.put("BAG",     "包包类");
    }

    @Autowired
    private ClothesCategoryMapper categoryMapper;

    @Override
    public List<CategoryResponse> listAll() {
        LambdaQueryWrapper<ClothesCategory> qw = new LambdaQueryWrapper<>();
        qw.eq(ClothesCategory::getStatus, 1);
        // 先按group顺序，再按sort_order，最后按id
        List<ClothesCategory> list = categoryMapper.selectList(qw);

        return list.stream()
                .sorted(Comparator
                        .comparingInt((ClothesCategory c) -> getGroupOrder(c.getCategoryGroup()))
                        .thenComparing(ClothesCategory::getSortOrder)
                        .thenComparing(ClothesCategory::getId))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, List<CategoryResponse>> listGrouped() {
        List<CategoryResponse> all = listAll();
        // 保持GROUP_LABEL_MAP的顺序
        Map<String, List<CategoryResponse>> result = new LinkedHashMap<>();
        for (String key : GROUP_LABEL_MAP.keySet()) {
            result.put(key, new ArrayList<>());
        }
        for (CategoryResponse r : all) {
            result.computeIfAbsent(r.getCategoryGroup(), k -> new ArrayList<>()).add(r);
        }
        // 去掉空列表（如果有）
        result.entrySet().removeIf(e -> e.getValue().isEmpty());
        return result;
    }

    @Override
    public List<CategoryResponse> search(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return listAll();
        }
        String kw = keyword.trim();
        LambdaQueryWrapper<ClothesCategory> qw = new LambdaQueryWrapper<>();
        qw.eq(ClothesCategory::getStatus, 1);
        qw.and(w -> w.like(ClothesCategory::getCategoryLevel2, kw)
                .or().like(ClothesCategory::getCategoryLevel1, kw)
                .or().like(ClothesCategory::getRemark, kw));
        List<ClothesCategory> list = categoryMapper.selectList(qw);
        return list.stream()
                .sorted(Comparator
                        .comparingInt((ClothesCategory c) -> getGroupOrder(c.getCategoryGroup()))
                        .thenComparing(ClothesCategory::getSortOrder))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /** 板块排序号（越小越靠前，Tab顺序） */
    private int getGroupOrder(String group) {
        int i = 0;
        for (String key : GROUP_LABEL_MAP.keySet()) {
            if (key.equals(group)) return i;
            i++;
        }
        return 999;
    }

    private CategoryResponse toResponse(ClothesCategory c) {
        CategoryResponse r = new CategoryResponse();
        r.setId(c.getId());
        r.setCategoryGroup(c.getCategoryGroup());
        r.setGroupLabel(GROUP_LABEL_MAP.getOrDefault(c.getCategoryGroup(), c.getCategoryLevel1()));
        r.setCategoryLevel1(c.getCategoryLevel1());
        r.setCategoryLevel2(c.getCategoryLevel2());
        r.setPrice(c.getPrice());
        r.setOriginalPrice(c.getPrice());
        r.setUnit(c.getUnit());
        r.setPriceMode(c.getPriceMode());
        r.setMemberPrice300(c.getMemberPrice300());
        r.setMemberPrice500(c.getMemberPrice500());
        r.setRemark(c.getRemark());
        r.setSortOrder(c.getSortOrder());
        return r;
    }
}
