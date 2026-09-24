package com.laundry.api.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laundry.api.entity.Customer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

/**
 * 客户 Mapper
 */
@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
    @Select("SELECT * FROM customer WHERE id=#{id} FOR UPDATE")
    Customer selectForUpdate(@Param("id") Long id);
}
