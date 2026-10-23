package com.tbgbook.bookstore.analysis.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 报表推送记录（只读）。
 */
@Data
@TableName("biz_report_push_log")
public class ReportPushLog
{
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    private Long subscribeId;

    private LocalDateTime pushTime;

    private Integer pushChannel;

    private Integer targetCount;

    private Integer isTest;

    /** 1成功 2失败 */
    private Integer status;

    private String failReason;

    private LocalDateTime createTime;
}
