package com.tbgbook.bookstore.borrow.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

/**
 * 借阅押金流水对象 biz_deposit_log（**只增不删**，FR-4.5）。
 *
 * <p>{@code changeType}：1缴纳 2退还 3扣罚金 4转赔偿。{@code depositAfter} 为变动后
 * 押金余额。押金缴纳/退还走 {@code POST /borrow/deposit/pay} 与
 * {@code POST /borrow/deposit/refund}；扣罚金由罚金单生成动作联动写入。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_deposit_log")
public class DepositLog implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 变动类型：1缴纳 */
    public static final int TYPE_PAY = 1;

    /** 变动类型：2退还 */
    public static final int TYPE_REFUND = 2;

    /** 变动类型：3扣罚金 */
    public static final int TYPE_FINE = 3;

    /** 变动类型：4转赔偿 */
    public static final int TYPE_COMPENSATE = 4;

    /** 流水ID */
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    /** 门店ID */
    @TableField("store_id")
    private Long storeId;

    /** 会员ID */
    @TableField("member_id")
    private Long memberId;

    /** 变动类型（1缴纳 2退还 3扣罚金 4转赔偿） */
    @TableField("change_type")
    private Integer changeType;

    /** 变动金额（正入负出） */
    @TableField("amount_change")
    private BigDecimal amountChange;

    /** 变动后押金余额 */
    @TableField("deposit_after")
    private BigDecimal depositAfter;

    /** 支付方式（1现金 2微信 3支付宝 4储值余额） */
    @TableField("pay_method")
    private Integer payMethod;

    /** 关联单号（借阅单号 / 罚金单号） */
    @TableField("ref_no")
    private String refNo;

    /** 操作人 */
    @TableField("create_by")
    private String createBy;

    /** 操作时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 备注 */
    @TableField("remark")
    private String remark;

    /** 查询用：创建时间区间（契约 {@code params[beginTime]} / {@code params[endTime]}，不落库） */
    @JsonIgnore
    @TableField(exist = false)
    private Map<String, Object> params;

    /**
     * 通用查询参数（首次访问时初始化）。
     *
     * @return 参数映射（beginTime / endTime 等）
     */
    public Map<String, Object> getParams()
    {
        if (params == null)
        {
            params = new HashMap<>();
        }
        return params;
    }

    public void setParams(Map<String, Object> params)
    {
        this.params = params;
    }
}
