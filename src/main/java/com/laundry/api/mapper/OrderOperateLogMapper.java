package com.laundry.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laundry.api.entity.OrderOperateLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单操作日志 Mapper
 */
@Mapper
public interface OrderOperateLogMapper extends BaseMapper<OrderOperateLog> {
}
