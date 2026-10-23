package com.tbgbook.bookstore.analysis.domain;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 报表订阅配置（FR-4.6.11 / A-69）。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_report_subscribe")
public class ReportSubscribe
{
    @TableId(value = "subscribe_id", type = IdType.AUTO)
    private Long subscribeId;

    private Long storeId;

    private String subscribeName;

    /** 1日报 2周报 3月报 */
    private Integer reportType;

    private String reportCode;

    private String dataScope;

    /** 接收角色ID，逗号分隔 */
    private String targetRoles;

    /** 接收邮箱，逗号分隔 */
    private String receiveEmails;

    /** 如 每日08:30 / 每周1 09:00 */
    private String pushTime;

    /** 1站内信 2邮件 3站内信+邮件 */
    private Integer pushChannel;

    /** 1启用 2停用 */
    private Integer status;

    private String failReason;

    @TableLogic(value = "0", delval = "2")
    private String delFlag;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;

    private String remark;
}
