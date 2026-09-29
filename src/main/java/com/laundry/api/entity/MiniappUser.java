package com.laundry.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("miniapp_user")
public class MiniappUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String openid;
    private String unionid;
    private String phone;
    private LocalDateTime bindTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
