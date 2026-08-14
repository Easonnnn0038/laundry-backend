package com.laundry.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laundry.api.entity.Store;
import org.apache.ibatis.annotations.Mapper;

/**
 * 门店 Mapper 接口
 */
@Mapper
public interface StoreMapper extends BaseMapper<Store> {
}
