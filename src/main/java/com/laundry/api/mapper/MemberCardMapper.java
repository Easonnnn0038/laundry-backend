package com.laundry.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laundry.api.entity.MemberCard;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员卡 Mapper
 */
@Mapper
public interface MemberCardMapper extends BaseMapper<MemberCard> {
}
