package com.tbgbook.bookstore.usedbook.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tbgbook.common.core.domain.BaseEntity;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 二手书回收单对象 biz_recycle（页面 A-41、B-14，FR-4.5.7 / 流程二）。
 *
 * <p>会员端提交申请生成 {@code status=1 待验收}；店员现场验货走
 * {@code POST /usedbook/recycle/{id}/accept}。{@code estimatedAmount} 是预估回收价合计
 * （**会员端展示必须标注「预估」**），{@code finalAmount} 以到店验收为准。
 * {@code hasMismatch=1} 表示存在成色判定不一致的行（页面需并排高亮对比）。
 *
 * <p>结算方式：1现金 2储值账户 3积分；选储值或积分时联动写
 * {@code biz_stored_value_log} / {@code biz_point_log}（流程二）。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_recycle")
public class Recycle extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 状态：1待验收 */
    public static final int STATUS_PENDING = 1;

    /** 状态：2验收中（已验收、待结算） */
    public static final int STATUS_ACCEPTED = 2;

    /** 状态：3已完成（已结算） */
    public static final int STATUS_DONE = 3;

    /** 状态：4已拒绝 */
    public static final int STATUS_REJECTED = 4;

    /** 状态：5已取消 */
    public static final int STATUS_CANCELLED = 5;

    /** 结算方式：1现金 */
    public static final int SETTLE_CASH = 1;

    /** 结算方式：2储值账户 */
    public static final int SETTLE_BALANCE = 2;

    /** 结算方式：3积分 */
    public static final int SETTLE_POINTS = 3;

    /** 回收单ID */
    @TableId(value = "recycle_id", type = IdType.AUTO)
    private Long recycleId;

    /** 门店ID */
    @TableField("store_id")
    private Long storeId;

    /** 回收单号（RC+yyyyMMddHHmmss+4 位流水） */
    @TableField("recycle_no")
    private String recycleNo;

    /** 会员ID */
    @NotNull(message = "会员ID不能为空")
    @TableField("member_id")
    private Long memberId;

    /** 会员昵称（冗余快照） */
    @TableField("member_name")
    private String memberName;

    /** 图书品种数 */
    @TableField("book_kind")
    private Integer bookKind;

    /** 图书总册数 */
    @TableField("total_qty")
    private Integer totalQty;

    /** 预估回收价合计（会员端须标注「预估」） */
    @TableField("estimated_amount")
    private BigDecimal estimatedAmount;

    /** 最终回收价合计（以到店验收为准） */
    @TableField("final_amount")
    private BigDecimal finalAmount;

    /** 是否存在成色判定不一致（0否 1是） */
    @TableField("has_mismatch")
    private Integer hasMismatch;

    /** 预约到店日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField("appointment_date")
    private LocalDate appointmentDate;

    /** 预约时间段 */
    @TableField("appointment_slot")
    private String appointmentSlot;

    /** 结算方式（1现金 2储值账户 3积分） */
    @TableField("settle_type")
    private Integer settleType;

    /** 结算金额 */
    @TableField("settle_amount")
    private BigDecimal settleAmount;

    /** 结算时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("settle_time")
    private LocalDateTime settleTime;

    /** 状态（1待验收 2验收中 3已完成 4已拒绝 5已取消） */
    @TableField("status")
    private Integer status;

    /** 拒收原因 */
    @TableField("reject_reason")
    private String rejectReason;

    /** 提交时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("submit_time")
    private LocalDateTime submitTime;

    /** 验收时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("accept_time")
    private LocalDateTime acceptTime;

    /** 验收人 */
    @TableField("accept_by")
    private String acceptBy;

    /** 子表明细（与单据头同一事务内落库；非表字段） */
    @TableField(exist = false)
    private List<RecycleItem> items = new ArrayList<>();

    /** 查询用：预约到店日期区间 + 创建时间区间（契约 params[*]，不落库） */
    @JsonIgnore
    @TableField(exist = false)
    private Map<String, Object> params;

    /**
     * 通用查询参数（首次访问时初始化）。
     *
     * @return 参数映射（beginAppointmentDate / endAppointmentDate / beginTime / endTime）
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
