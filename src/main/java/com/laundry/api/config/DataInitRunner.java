package com.laundry.api.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laundry.api.entity.User;
import com.laundry.api.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.LocalDateTime;

/**
 * 仅在显式启用 app.seed-default-users 时运行的开发数据初始化器。
 * 解决 BCrypt 兼容性问题：
 *   init.sql / receive_clothes.sql 中手写的 $2a / $2b 前缀 hash 在不同 BCrypt 实现下可能不匹配，
 *   因此开发环境可对默认 3 个账号（admin / employee1 / employee2）
 *   用当前容器内 PasswordEncoder 重新生成密码并写回数据库，确保与登录校验算法完全一致。
 *   密码：admin -> admin123；employee1/employee2 -> emp123
 */
@Component
@ConditionalOnProperty(name = "app.seed-default-users", havingValue = "true")
public class DataInitRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitRunner.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        try {
            Long count = userMapper.selectCount(null);
            log.info("检测到 users 表记录数: {}", count);

            // 仅开发环境显式启用；生产环境绝不能重置账号密码。
            ensureUser("admin",     "admin123",   "王朋",   "ADMIN");
            ensureUser("employee1", "emp123",     "邵恒剑", "EMPLOYEE");
            ensureUser("employee2", "emp123",     "刘洪刚", "EMPLOYEE");

            log.info("开发账号初始化完成");
        } catch (Exception e) {
            log.error("初始化默认账号失败", e);
            log.error("请确认：1)是否已执行 receive_clothes.sql  2)数据库密码是否正确（application.yml）");
        }
    }

    private void ensureUser(String username, String rawPwd, String realName, String role) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getUsername, username);
        User user = userMapper.selectOne(qw);

        if (user == null) {
            user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(rawPwd));
            user.setRealName(realName);
            user.setRole(role);
            user.setPhone("13800000" + ("admin".equals(username) ? "001" : "employee1".equals(username) ? "002" : "003"));
            user.setStoreId("001");
            user.setStatus(1);
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            userMapper.insert(user);
            log.info("默认开发账号已创建: {}", username);
        } else {
            // 强制覆盖密码（不比对，避免 BCrypt 兼容性问题）
            user.setPassword(passwordEncoder.encode(rawPwd));
            user.setUpdateTime(LocalDateTime.now());
            userMapper.updateById(user);
            log.info("默认开发账号密码已同步: {}", username);
        }
    }
}
