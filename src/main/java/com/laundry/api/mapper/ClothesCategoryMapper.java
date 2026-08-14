package com.laundry.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laundry.api.entity.ClothesCategory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 衣物类别 Mapper
 */
@Mapper
public interface ClothesCategoryMapper extends BaseMapper<ClothesCategory> {
}
