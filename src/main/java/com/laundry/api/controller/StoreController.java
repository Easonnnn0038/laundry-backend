package com.laundry.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laundry.api.common.Result;
import com.laundry.api.entity.Store;
import com.laundry.api.entity.User;
import com.laundry.api.mapper.StoreMapper;
import com.laundry.api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 店面控制器
 * 提供门店信息相关接口
 */
@Tag(name = "门店管理", description = "门店信息查询相关接口")
@RestController
@RequestMapping("/api/store")
public class StoreController {

    @Autowired
    private StoreMapper storeMapper;

    @Autowired
    private UserService userService;

    /**
     * 获取当前登录用户信息
     */
    @Operation(summary = "获取当前用户信息", description = "根据JWT令牌获取当前登录用户的详细信息")
    @GetMapping("/current-user")
    public Result<Map<String, Object>> currentUser(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByUsername(userDetails.getUsername());

        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("realName", user.getRealName());
        info.put("role", user.getRole());
        info.put("phone", user.getPhone());
        info.put("storeId", user.getStoreId());

        return Result.success(info);
    }

    /**
     * 获取所有门店列表
     */
    @Operation(summary = "获取门店列表", description = "查询所有门店信息")
    @GetMapping("/list")
    public Result<List<Store>> list() {
        List<Store> stores = storeMapper.selectList(
                new LambdaQueryWrapper<Store>().orderByAsc(Store::getStoreCode));
        return Result.success(stores);
    }

    /**
     * 根据门店编号获取门店详情
     */
    @Operation(summary = "获取门店详情", description = "根据门店编号查询门店详细信息")
    @GetMapping("/{storeCode}")
    public Result<Store> getByStoreCode(@PathVariable String storeCode) {
        Store store = storeMapper.selectOne(
                new LambdaQueryWrapper<Store>().eq(Store::getStoreCode, storeCode));
        if (store == null) {
            return Result.error(404, "门店不存在");
        }
        return Result.success(store);
    }

    /**
     * 系统信息（用于健康检查）
     */
    @Operation(summary = "系统信息", description = "获取系统基本信息，可用于健康检查")
    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "小木棒洗衣管理系统");
        info.put("version", "1.0.0");
        info.put("description", "洗衣门店管理系统后端服务");
        return Result.success(info);
    }
}
