package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 报损申请单对象 biz_loss_apply（页面 A-18 报损出库申请 / A-19 报损审批）。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：本表有 {@code create_time} / {@code update_time}，
 * 但**没有** {@code create_by} / {@code update_by} / {@code remark} —— 沿用会让 MP 去写
 * 不存在的列。操作人由业务列 {@code applyBy} / {@code auditBy} 承载（它们语义更准：
 * 申请人 ≠ 最后修改人，审批人 ≠ 更新者）。代价是审计时间的 {@code @JsonFormat}
 * 与瞬态 {@code params} 要自己补。
 *
 * <p><b>待审批期间不扣库存</b>（§4.2 规则 4）：开单只落单据与明细，
 * {@code approve} 才真正扣减可售库存并写 {@code biz_stock_flow}（{@code biz_type=6 报损}）。
 * 这与「调拨免审批、确认即生效」形成对照 —— 报损必然经过店长。
 *
 * <p><b>审批是独立权限</b>：{@code inventory:loss:approve} 仅授给店长（角色权限要点 1），
 * 而单据的增删改查走 {@code inventory:loss:audit}（库管员 + 店长）。
 * 即「能申请的人不一定能批」，这是本单据权限设计的要点。
 *
 * <p><b>服务端接管、客户端传了会被忽略的字段</b>（契约的 {@code skip}）：
 * {@code applyNo}（BS+yyyyMMdd+流水）、{@code bookKind} / {@code totalQty} / {@code totalAmount}
 * （由明细聚合）、{@code needConfirm}（按金额阈值算）、{@code status}（初始恒为 1 待审批）、
 * {@code applyBy} / {@code applyTime}（提交时取当前登录用户与时间）、
 * {@code auditBy} / {@code auditTime} / {@code auditRemark}（审批时才写）。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_loss_apply")
public class LossApply implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 状态：待审批。**库存尚未扣减** */
    public static final int STATUS_PENDING = 1;

    /** 状态：已通过。库存已扣减并写过流水 */
    public static final int STATUS_APPROVED = 2;

    /** 状态：已驳回。**库存不动** */
    public static final int STATUS_REJECTED = 3;

    /** 状态：已撤销。申请人自行撤回，**库存不动** */
    public static final int STATUS_REVOKED = 4;

    /** 报损类型：破损 */
    public static final int TYPE_DAMAGED = 1;

    /** 报损类型：丢失 */
    public static final int TYPE_LOST = 2;

    /** 报损类型：过期 */
    public static final int TYPE_EXPIRED = 3;

    /** 报损单ID */
    @TableId(value = "apply_id", type = IdType.AUTO)
    private Long applyId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 报损单号（BS+yyyyMMdd+流水）。**服务端生成** */
    @TableField("apply_no")
    private String applyNo;

    /**
     * 报损类型（1破损 2丢失 3过期）。
     *
     * <p>{@code null} 表示 PUT 未提交该字段（沿用原值），因此必填性只在**新增**路径上
     * 由 service 判定 —— 范围注解对 {@code null} 不触发，与「PUT 可省略」不冲突。
     */
    @Min(value = 1, message = "报损类型取值 1~3")
    @Max(value = 3, message = "报损类型取值 1~3")
    @TableField("loss_type")
    private Integer lossType;

    /** 品种数（由明细聚合，服务端维护） */
    @TableField("book_kind")
    private Integer bookKind;

    /** 总数量（由明细聚合，服务端维护） */
    @TableField("total_qty")
    private Integer totalQty;

    /** 报损总金额（**成本口径**，由明细聚合，服务端维护） */
    @TableField("total_amount")
    private BigDecimal totalAmount;

    /**
     * 是否触发二次确认（金额 &gt; {@code biz.loss.confirm.amount}，服务端维护）。
     *
     * <p>⚠️ 本标志由服务端计算、**供前端与审批页据此提示**。按原型 A-18，
     * 「超 500 元弹二次确认」发生在**提交环节**，故本接口只出标志、不做服务端阻断
     * （契约里也没有可供阻断的入参字段）。已登记 `plan/待确认项.md`。
     */
    @TableField("need_confirm")
    private Integer needConfirm;

    /** 报损事由 */
    @Size(max = 500, message = "报损事由不能超过 500 个字符")
    @TableField("reason")
    private String reason;

    /** 附件URL（逗号分隔，破损照片） */
    @Size(max = 1000, message = "附件URL不能超过 1000 个字符")
    @TableField("attachment_url")
    private String attachmentUrl;

    /**
     * 状态（1待审批 2已通过 3已驳回 4已撤销）。
     *
     * <p>⚠️ **刻意不加范围校验**：契约把 {@code status} 列在 `skip` 里
     * ——「客户端传了会被忽略」。标了范围注解会让客户端传 {@code status=9} 先被
     * {@code @Valid} 拦成 400，与「忽略」的语义相反（在 {@code StockIn} 上实测踩到）。
     */
    @TableField("status")
    private Integer status;

    /** 申请人（服务端取当前登录用户） */
    @TableField("apply_by")
    private String applyBy;

    /** 申请时间（提交时写入，可回填） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("apply_time")
    private LocalDateTime applyTime;

    /** 审批人（仅店长，角色权限要点 1） */
    @TableField("audit_by")
    private String auditBy;

    /** 审批时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("audit_time")
    private LocalDateTime auditTime;

    /** 审批意见（驳回时必填，由 service 在 reject 时判定） */
    @Size(max = 500, message = "审批意见不能超过 500 个字符")
    @TableField("audit_remark")
    private String auditRemark;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ------------------------------------------------------------------
    // 子表：与单据头**同事务**落库（契约「开单携带明细数组 items」）
    // ------------------------------------------------------------------

    /**
     * 明细数组（瞬态，不落本表）。
     *
     * <p>{@code @Valid} 会级联校验每个元素，明细的必填项因此也在「参数校验前置」
     * 的覆盖范围内（NFR-M2）。
     */
    @Valid
    @TableField(exist = false)
    private List<LossApplyItem> items;

    /**
     * 请求参数（瞬态，不落库）。
     *
     * <p>本实体不继承 {@code BaseEntity}，日期区间筛选走 {@code params[beginTime]}，故需自补
     * —— 不补会让列表接口 500（{@code There is no getter for property named 'params'}）。
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
