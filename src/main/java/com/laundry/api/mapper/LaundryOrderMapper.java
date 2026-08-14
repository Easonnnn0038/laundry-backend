package com.laundry.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laundry.api.entity.LaundryOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 收衣订单 Mapper
 */
@Mapper
public interface LaundryOrderMapper extends BaseMapper<LaundryOrder> {
}
