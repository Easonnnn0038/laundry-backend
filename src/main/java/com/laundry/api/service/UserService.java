package com.laundry.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laundry.api.entity.User;
import com.laundry.api.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户服务
 */
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户实体，不存在返回 null
     */
    public User getByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    /**
     * 根据ID查询用户
     *
     * @param id 用户ID
     * @return 用户实体
     */
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    /**
     * 查询所有用户
     *
     * @return 用户列表
     */
    public List<User> listAll() {
        return userMapper.selectList(null);
    }

    /**
     * 根据门店ID查询用户列表
     *
     * @param storeId 门店ID
     * @return 用户列表
     */
    public List<User> listByStoreId(String storeId) {
        return userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getStoreId, storeId));
    }
}
