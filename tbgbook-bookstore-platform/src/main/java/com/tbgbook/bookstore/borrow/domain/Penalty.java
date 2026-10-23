package com.tbgbook.bookstore.borrow.domain;

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
import com.tbgbook.common.core.domain.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 罚金与赔偿单对象 biz_penalty（页面 A-56，FR-4.5.7）。
 *
 * <p>三种类型：1逾期罚金 / 2丢失赔偿 / 3严重破损赔偿。金额由服务端按规则计算
 * （{@code biz_borrow_rule}），不接受直接写入；{@code calcDetail} 存计算式说明，
 * 会员端可直接展示避免争议。**罚金优先从押金扣除**，押金不足时生成待缴账单
 * （{@code payStatus=1}），扣押金联动写 {@code biz_deposit_log}（changeType=3）。
 * 罚金属财务凭证，**不提供删除**。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_penalty")
public class Penalty extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 类型：1逾期罚金 */
    public static final int TYPE_OVERDUE = 1;

    /** 类型：2丢失赔偿 */
    public static final int TYPE_LOST = 2;

    /** 类型：3严重破损赔偿 */
    public static final int TYPE_DAMAGED = 3;

    /** 缴费状态：1待缴 */
    public static final int PAY_PENDING = 1;

    /** 缴费状态：2已缴 */
    public static final int PAY_PAID = 2;

    /** 缴费状态：3部分缴纳 */
    public static final int PAY_PARTIAL = 3;

    /** 缴费状态：4已减免 */
    public static final int PAY_REDUCED = 4;

    /** 罚金单ID */
    @TableId(value = "penalty_id", type = IdType.AUTO)
    private Long penaltyId;

    /** 门店ID */
    @TableField("store_id")
    private Long storeId;

    /** 罚金单号（PN+yyyyMMddHHmmss+4 位流水） */
    @TableField("penalty_no")
    private String penaltyNo;

    /** 会员ID */
    @TableField("member_id")
    private Long memberId;

    /** 关联借阅单ID（逾期罚金必填；丢失/破损可空） */
    @TableField("borrow_id")
    private Long borrowId;

    /** 图书ID */
    @TableField("book_id")
    private Long bookId;

    /** 类型（1逾期罚金 2丢失赔偿 3严重破损赔偿） */
    @TableField("penalty_type")
    private Integer penaltyType;

    /** 金额 */
    @TableField("amount")
    private BigDecimal amount;

    /** 计算基数（图书定价） */
    @TableField("calc_base")
    private BigDecimal calcBase;

    /** 计算式说明 */
    @TableField("calc_detail")
    private String calcDetail;

    /** 已从押金扣除金额 */
    @TableField("deduct_deposit")
    private BigDecimal deductDeposit;

    /** 缴费状态（1待缴 2已缴 3部分缴纳 4已减免） */
    @TableField("pay_status")
    private Integer payStatus;

    /** 缴费时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("pay_time")
    private LocalDateTime payTime;

    /** 缴费方式（1现金 2微信 3支付宝 4押金扣除） */
    @TableField("pay_method")
    private Integer payMethod;

    /** 减免审批人（店长） */
    @TableField("audit_by")
    private String auditBy;

    /** 减免审批时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("audit_time")
    private LocalDateTime auditTime;

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
