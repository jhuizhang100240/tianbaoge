package com.tbgbook.bookstore.usedbook.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 回收结算结果（`POST /usedbook/recycle/{recycleId}/settle` 的 `data`）。
 *
 * @author tbgbook
 */
@Data
public class SettleResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 回收单ID */
    private Long recycleId;

    /** 回收单号 */
    private String recycleNo;

    /** 结算方式 */
    private Integer settleType;

    /** 结算金额 */
    private BigDecimal settleAmount;

    /** 结算时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime settleTime;

    /** 结算后状态（3已完成） */
    private Integer status;

    /** 积分/储值入账说明（现金结算时为 null） */
    private String linkageNote;
}
