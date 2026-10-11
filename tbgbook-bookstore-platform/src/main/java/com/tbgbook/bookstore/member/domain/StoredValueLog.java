package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 储值流水对象 biz_stored_value_log（只增不删）。
 *
 * <p>金额类型 6 种：1本金充值 2赠送金 3消费扣本金 4消费扣赠送金 5退款返本金 6退款返赠送金
 * —— **本金与赠送金的每一次变动分开记账**。消费时优先扣赠送金，赠送金不足再扣本金。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_stored_value_log")
public class StoredValueLog implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 流水ID */
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    /** 会员ID */
    @TableField("member_id")
    private Long memberId;

    /** 金额类型（1本金充值 2赠送金 3消费扣本金 4消费扣赠送金 5退款返本金 6退款返赠送金） */
    @TableField("amount_type")
    private Integer amountType;

    /** 变动金额（正增负减） */
    @TableField("change_amount")
    private BigDecimal changeAmount;

    /** 变动后本金余额 */
    @TableField("principal_after")
    private BigDecimal principalAfter;

    /** 变动后赠送金余额 */
    @TableField("gift_after")
    private BigDecimal giftAfter;

    /** 关联单号（订单号/充值单号/退款单号） */
    @TableField("ref_no")
    private String refNo;

    /** 操作人 */
    @TableField("create_by")
    private String createBy;

    /** 发生时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 备注 */
    @TableField("remark")
    private String remark;
}
