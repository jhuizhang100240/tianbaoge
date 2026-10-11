package com.tbgbook.bookstore.member.domain.aggregate;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 会员统一时间轴条目（`GET /member/member/{memberId}/timeline`，FR-4.4.9）。
 *
 * <p>四类记录统一投影：订单 / 借阅 / 积分变动 / 储值变动。
 * {@code amount} 统一折算为**元**（积分按 100 积分 = 1 元），出账为负。
 *
 * @author tbgbook
 */
@Data
public class MemberTimelineEntry implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 记录类型：order / borrow / point / stored */
    private String type;

    /** 业务单号（订单号 / 借阅号 / 积分与储值流水的 refNo） */
    private String bizNo;

    /** 摘要（自解释文案，前端直接展示） */
    private String title;

    /** 金额（元，正负） */
    private BigDecimal amount;

    /** 发生时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime time;

    /** 详情深链（订单 / 借阅详情页） */
    private String linkUrl;
}
