package com.laundry.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laundry.api.entity.SeqCounter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SeqCounterMapper extends BaseMapper<SeqCounter> {

    @Insert("INSERT INTO seq_counter (counter_key, seq) VALUES (#{key}, 1) " +
            "ON DUPLICATE KEY UPDATE seq = seq + 1")
    void incrementSeq(@Param("key") String key);

    @Select("SELECT seq FROM seq_counter WHERE counter_key = #{key}")
    Integer getSeq(@Param("key") String key);
}
