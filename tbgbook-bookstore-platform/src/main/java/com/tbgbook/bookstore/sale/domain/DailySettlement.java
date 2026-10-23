package com.tbgbook.bookstore.sale.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.tbgbook.common.core.domain.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 日结对账对象 biz_daily_settlement（A-33 日结对账，FR-4.3.12）。
 *
 * <p><b>各金额列一律由订单聚合生成，不接受传入</b>（契约明写）。唯一的维护点是
 * {@code POST /sales/settlement/generate}：按门店 + 日期 + 班次汇总，重复生成覆盖未锁定的单。
 *
 * <p><b>锁定的意义</b>：{@code POST /sales/settlement/{settleId}/lock} 之后该批次订单
 * **不可再修改**（调整必须走红冲，§4.3 规则 6）—— 落地方式是给窗口内的订单置
 * {@code biz_order.is_settled=1}，订单的改删校验本来就看这一列。
 *
 * <p><b>对账差异 {@code diffAmount} 的口径是本次自定</b>（见 `plan/待确认项.md` A31）：
 * 只在**给了 {@code shiftId} 且该班次已交班**时计算，取「交班实点现金 − 系统现金收款」；
 * 其余情况记 0。理由是差异说明（{@code diffReason}）要求的是**现金实点与账面的差**，
 * 而实点数只有交班时才有。
 *
 * <p>⚠️ 状态三态里的「2已对账」**没有任何端点能到达**（只有生成与锁定两个动作）——
 * 与换货单的「3已作废」同类，已登记缺口。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_daily_settlement")
public class DailySettlement extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态：待对账 */
    public static final int STATUS_PENDING = 1;

    /** 状态：已对账（**当前无端点可达**） */
    public static final int STATUS_RECONCILED = 2;

    /** 状态：已锁定 */
    public static final int STATUS_LOCKED = 3;

    @TableId(value = "settle_id", type = IdType.AUTO)
    private Long settleId;

    @TableField("store_id")
    private Long storeId;

    /** 日结单号（JS+yyyyMMdd+流水），**服务端生成** */
    @TableField("settle_no")
    private String settleNo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("settle_date")
    private LocalDate settleDate;

    /** 班次（如 早班 08:00-15:00） */
    @TableField("shift")
    private String shift;

    @TableField("order_count")
    private Integer orderCount;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("cash_amount")
    private BigDecimal cashAmount;

    @TableField("wechat_amount")
    private BigDecimal wechatAmount;

    @TableField("alipay_amount")
    private BigDecimal alipayAmount;

    @TableField("stored_amount")
    private BigDecimal storedAmount;

    @TableField("refund_amount")
    private BigDecimal refundAmount;

    @TableField("point_amount")
    private BigDecimal pointAmount;

    /** 对账差异（**服务端算**，口径见类注释） */
    @TableField("diff_amount")
    private BigDecimal diffAmount;

    /** 差异说明（{@code diffAmount != 0} 时锁定时必填） */
    @TableField("diff_reason")
    private String diffReason;

    @TableField("status")
    private Integer status;

    @TableField("settle_by")
    private String settleBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("settle_time")
    private LocalDateTime settleTime;
}
