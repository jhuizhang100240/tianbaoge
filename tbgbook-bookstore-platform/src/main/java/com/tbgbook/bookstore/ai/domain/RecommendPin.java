package com.tbgbook.bookstore.ai.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 推荐位人工置顶（biz_recommend_pin；整体替换保存，下架图书拒绝）。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_recommend_pin")
public class RecommendPin
{
    @TableId(value = "pin_id", type = IdType.AUTO)
    private Long pinId;

    private Long configId;

    private Long bookId;

    private Integer pinOrder;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
