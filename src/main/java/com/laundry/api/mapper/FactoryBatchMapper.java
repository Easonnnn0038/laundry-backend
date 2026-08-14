package com.laundry.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laundry.api.entity.FactoryBatch;
import org.apache.ibatis.annotations.Mapper;

/**
 * 送厂批次 Mapper
 */
@Mapper
public interface FactoryBatchMapper extends BaseMapper<FactoryBatch> {
}
