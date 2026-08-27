package com.laundry.api.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seq_counter")
public class SeqCounter {

    @TableId
    private String counterKey;

    private Integer seq;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
