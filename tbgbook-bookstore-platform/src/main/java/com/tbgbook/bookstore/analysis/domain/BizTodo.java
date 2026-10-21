package com.tbgbook.bookstore.analysis.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 待办中心（A-04）。跨模块待办聚合，仅状态可流转。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_todo")
public class BizTodo
{
    @TableId(value = "todo_id", type = IdType.AUTO)
    private Long todoId;

    private Long storeId;

    /** 1预警 2待审批 3待验收 4逾期未还 5系统通知 */
    private Integer todoType;

    /** 1高 2中 3低 */
    private Integer priority;

    private String sourceModule;

    private String sourceNo;

    private String targetValue;

    private String content;

    private String linkUrl;

    /** 1未读 2已读 3已处理 4已忽略 */
    private Integer status;

    private String readBy;

    private LocalDateTime readTime;

    private String handleBy;

    private LocalDateTime handleTime;

    private String remark;

    private LocalDateTime createTime;
}
