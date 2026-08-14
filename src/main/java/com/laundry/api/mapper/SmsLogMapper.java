package com.laundry.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laundry.api.entity.SmsLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 短信记录 Mapper
 */
@Mapper
public interface SmsLogMapper extends BaseMapper<SmsLog> {
}
