package com.tbgbook.bookstore.sale.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.tbgbook.common.core.domain.BaseEntity;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 促销活动对象 biz_promotion（页面 A-32~A-34 促销管理，FR-4.3.7）。
 *
 * <p><b>五种活动类型，字段按类型生效</b>（契约的业务约束）：
 * <ul>
 *   <li>{@code 1折扣} —— 用 {@code discountRate}</li>
 *   <li>{@code 2满减} —— 用 {@code thresholdAmount} + {@code reduceAmount}</li>
 *   <li>{@code 3满赠} —— 用 {@code giftDesc}</li>
 *   <li>{@code 4会员专享价} / {@code 5限时特价} —— 用**明细里的** {@code activityPrice}
 *       （见 {@link PromotionBook}）</li>
 * </ul>
 * 服务端**不校验「本类型的必填字段是否给了」**：契约把「按类型生效」定为字段语义而非必填约束，
 * 而运营常常先建草稿再补参数。真正参与计算时（阶段四步骤②的优惠计算）才按类型取字段，
 * 缺字段即该活动不生效 —— 这比开单时硬拦更贴合实际用法。
 *
 * <p><b>{@code status} 只能由动作端点流转</b>（契约的 `skip`）：
 * {@code POST /sales/promotion/{promotionId}/status} 带两道校验（活动商品为空、时间区间冲突），
 * 若本实体的 POST/PUT 能直接写 {@code status}，客户端把状态直接置 2 就绕过了它们。
 * 故新增时**强制置 1 草稿**，修改时**忽略客户端传的 status**。
 *
 * <p><b>{@code priority} 数字大者优先，同一商品命中多个活动时按优先级取一、不相加</b>
 * —— 这是本项目促销的核心语义，写在这里以免后续实现优惠计算时误做成叠加。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_promotion")
public class Promotion extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    // ------------------------------------------------------------------
    // 活动类型（契约「促销活动」字段字典）
    // ------------------------------------------------------------------

    /** 类型：折扣 */
    public static final int TYPE_DISCOUNT = 1;

    /** 类型：满减 */
    public static final int TYPE_THRESHOLD_REDUCE = 2;

    /** 类型：满赠 */
    public static final int TYPE_THRESHOLD_GIFT = 3;

    /** 类型：会员专享价 */
    public static final int TYPE_MEMBER_PRICE = 4;

    /** 类型：限时特价 */
    public static final int TYPE_FLASH_PRICE = 5;

    // ------------------------------------------------------------------
    // 状态（契约「促销活动」字段字典）
    // ------------------------------------------------------------------

    /** 状态：草稿（**新增时恒为此态**） */
    public static final int STATUS_DRAFT = 1;

    /** 状态：进行中 */
    public static final int STATUS_RUNNING = 2;

    /** 状态：已结束 */
    public static final int STATUS_FINISHED = 3;

    /** 状态：已停用 */
    public static final int STATUS_DISABLED = 4;

    /** 适用范围：全场 */
    public static final int SCOPE_ALL = 1;

    /** 适用范围：指定分类 */
    public static final int SCOPE_CATEGORY = 2;

    /** 适用范围：指定图书 */
    public static final int SCOPE_BOOK = 3;

    /** 活动ID */
    @TableId(value = "promotion_id", type = IdType.AUTO)
    private Long promotionId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 活动编号（PM+yyyyMMdd+流水）。**服务端生成** */
    @TableField("promotion_no")
    private String promotionNo;

    /** 活动名称。必填性由 service 按**操作**判定（新增必填、PUT 可沿用） */
    @Size(max = 100, message = "活动名称不能超过 100 个字符")
    @TableField("promotion_name")
    private String promotionName;

    /**
     * 活动类型（1折扣 2满减 3满赠 4会员专享价 5限时特价）。
     *
     * <p>**不在** `skip` 里、由客户端决定，故有范围校验（对照 {@code status} 在 skip 里、不校验）。
     */
    @Min(value = 1, message = "活动类型取值 1~5")
    @Max(value = 5, message = "活动类型取值 1~5")
    @TableField("promotion_type")
    private Integer promotionType;

    /**
     * 折扣率（折扣型活动用）。
     *
     * <p>取值范围 {@code (0, 1]}：{@code 0.7} 即 7 折 —— 与
     * {@code biz.discount.approval.rate}（默认 0.7）同一口径（§3.2 价格规则 2）。
     */
    @DecimalMin(value = "0", message = "折扣率不能为负")
    @DecimalMax(value = "1", message = "折扣率不能大于 1（0.7 即 7 折）")
    @TableField("discount_rate")
    private BigDecimal discountRate;

    /** 满减门槛金额（满减型活动用） */
    @DecimalMin(value = "0", message = "门槛金额不能为负")
    @TableField("threshold_amount")
    private BigDecimal thresholdAmount;

    /** 减免金额（满减型活动用） */
    @DecimalMin(value = "0", message = "减免金额不能为负")
    @TableField("reduce_amount")
    private BigDecimal reduceAmount;

    /** 赠品说明（满赠型活动用） */
    @Size(max = 200, message = "赠品说明不能超过 200 个字符")
    @TableField("gift_desc")
    private String giftDesc;

    /** 适用范围（1全场 2指定分类 3指定图书） */
    @Min(value = 1, message = "适用范围取值 1~3")
    @Max(value = 3, message = "适用范围取值 1~3")
    @TableField("scope_type")
    private Integer scopeType;

    /** 范围值（分类ID/图书ID列表，逗号分隔；`scopeType=1` 时留空） */
    @Size(max = 1000, message = "范围值不能超过 1000 个字符")
    @TableField("scope_value")
    private String scopeValue;

    /** 适用会员等级ID（逗号分隔，**空 = 全部等级适用**） */
    @Size(max = 200, message = "适用会员等级不能超过 200 个字符")
    @TableField("member_level_ids")
    private String memberLevelIds;

    /**
     * 是否特价活动（特价商品**不参与优惠券叠加**，§3.2 价格规则 3）。
     *
     * <p>{@code tinyint(1)} 列按 0/1 存，故用 {@code Integer} 而非 {@code Boolean}
     * —— 与表结构一致，也避免 Jackson 对 {@code tinyint} 的 0/1 与 boolean 互转歧义。
     */
    @Min(value = 0, message = "是否特价只能填 0 或 1")
    @Max(value = 1, message = "是否特价只能填 0 或 1")
    @TableField("is_special_price")
    private Integer isSpecialPrice;

    /** 优先级（**数字大者优先**；同一商品命中多个活动时取一，不相加） */
    @Min(value = 0, message = "优先级不能为负")
    @Max(value = 9999, message = "优先级不能超过 9999")
    @TableField("priority")
    private Integer priority;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("end_time")
    private LocalDateTime endTime;

    /**
     * 状态（1草稿 2进行中 3已结束 4已停用）。
     *
     * <p>⚠️ **刻意不加范围校验**：契约把它列在 `skip` 里 ——「客户端传了会被忽略」。
     * 标了范围注解会让客户端传 {@code status=9} 先被 {@code @Valid} 拦成 400，
     * 与「忽略」的语义相反（在 {@code StockIn} 上实测踩到）。状态流转走动作端点。
     */
    @TableField("status")
    private Integer status;

    /**
     * 删除标志（0存在 2删除）。**基础数据表**，故有逻辑删除。
     *
     * <p>⚠️ <b>必须标 {@code READ_ONLY}</b>：契约把 {@code delFlag} 列在 `skip` 里
     * ——「客户端传了会被忽略」，而这个「忽略」**得有人真的做**。
     * 不标的话客户端 POST 一个 {@code delFlag="2"} 就能**创建一条立即不可见的记录**
     * （200 成功、但所有列表与详情都查不到，因为它已被逻辑删除），
     * PUT 时更能直接把行「删掉」。
     *
     * <p>用 {@code READ_ONLY} 而不是在 service 里手工 {@code setDelFlag(null)}：
     * 后者要在新增与修改两处各写一遍，漏一处就是同一个洞；
     * {@code READ_ONLY} 让 Jackson 在**反序列化时就丢掉**这个字段，只有一处、不可能漏。
     * 响应里仍会带 {@code delFlag}（序列化不受影响）。
     *
     * <p>⚠️ <b>这是全项目共性问题</b>：已交付的 {@code Book} / {@code Publisher} /
     * {@code Category} / {@code Location} 等带 {@code del_flag} 的实体**都没有这层防护**
     * （2026-09-23 实测：给 {@code POST /book/book} 传 {@code delFlag:"2"} 返回 200，
     * 但那条书记录 del_flag=2、列表里查不到）。已登记 `plan/待确认项.md`。
     */
    /** 该活动对**指定图书**的活动价（JOIN `biz_promotion_book`，仅 4/5 型有意义） */
    @TableField(exist = false)
    private java.math.BigDecimal activityPrice;

    /** 该活动对指定图书的限购数量（0=不限购） */
    @TableField(exist = false)
    private Integer limitQty;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @TableLogic(value = "0", delval = "2")
    private String delFlag;
}
