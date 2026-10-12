package com.tbgbook.bookstore.usedbook.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 回收结算请求（`POST /usedbook/recycle/{recycleId}/settle`）。
 *
 * @author tbgbook
 */
@Data
public class SettleRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 结算方式（1现金 2储值账户 3积分） */
    private Integer settleType;

    /** 结算金额（不传取最终回收价合计） */
    private BigDecimal settleAmount;
}
