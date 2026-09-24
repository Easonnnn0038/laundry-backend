package com.laundry.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laundry.api.entity.MemberCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

/**
 * 会员卡 Mapper
 */
@Mapper
public interface MemberCardMapper extends BaseMapper<MemberCard> {
    @Select("SELECT * FROM member_card WHERE id=#{id} FOR UPDATE")
    MemberCard selectForUpdate(@Param("id") Long id);
}
