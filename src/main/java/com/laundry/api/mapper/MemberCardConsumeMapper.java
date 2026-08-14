package com.laundry.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laundry.api.entity.MemberCardConsume;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员卡消费记录 Mapper（对账用）
 */
@Mapper
public interface MemberCardConsumeMapper extends BaseMapper<MemberCardConsume> {
}
