package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import lombok.Data;

/**
 * 促销活动商品对象 biz_promotion_book（A-34 活动商品配置）。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：本表只有 {@code create_time}，
 * 没有 {@code create_by} / {@code update_*} / {@code remark} / {@code del_flag}
 * —— 关系行只有「在 / 不在」两种状态，移除即**物理删行**。
 * 代价是审计时间的 {@code @JsonFormat} 要自己补。
 *
 * <p><b>{@code activityPrice} 只对 4会员专享价 / 5限时特价 有意义</b>：
 * 其余活动类型（折扣/满减/满赠）的价格由活动本身的 {@code discountRate} /
 * {@code reduceAmount} 决定，本列留 0 即可。故这里**不做「必须大于 0」的校验**
 * —— 那会让 1/2/3 三类活动建不出商品行。
 *
 * <p><b>{@code isbn} / {@code title} 是瞬态展示字段</b>（契约的 `resp_extra`）：
 * 本表**是少数没有冗余 isbn/title 列的子表**（{@code biz_order_item} /
 * {@code biz_stock_in_item} 等都自带），不补的话前端只能按行回查（N+1）或渲染不出书名。
 * 由列表查询 JOIN {@code biz_book} 填充，**不落本表**。
 *
 * <p><b>瞬态 {@code params} 必须补</b>：列表的通用日期区间筛选走
 * {@code params[beginTime]} / {@code params[endTime]}（契约的列表筛选参数里就有），
 * 由 XML 读取 —— 不补会让列表接口 500
 * （{@code There is no getter for property named 'params'}，实测踩到）。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_promotion_book")
public class PromotionBook implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 关系ID */
    @TableId(value = "rel_id", type = IdType.AUTO)
    private Long relId;

    /** 活动ID */
    @TableField("promotion_id")
    private Long promotionId;

    /**
     * 图书ID。
     *
     * <p>⚠️ **不加 {@code @NotNull}**：本实体 POST/PUT 共用，而 PUT 允许只改活动价
     * （{@code bookId} 沿用原行）。在字段上标必填会让这种 PUT 被 {@code @Valid} 拦成 400
     * —— 与 {@code StockInItem#bookId} 上踩过的是同一个坑。
     * 必填性由 {@code PromotionBookServiceImpl#addPromotionBook} 按**操作**判定。
     */
    @TableField("book_id")
    private Long bookId;

    /** 活动价（仅 4会员专享价 / 5限时特价 使用；其余类型留 0） */
    @DecimalMin(value = "0", message = "活动价不能为负")
    @TableField("activity_price")
    private BigDecimal activityPrice;

    /** 限购数量（**0 = 不限购**） */
    @Min(value = 0, message = "限购数量不能为负")
    @TableField("limit_qty")
    private Integer limitQty;

    /**
     * 创建时间。
     *
     * <p>⚠️ 本实体不继承 {@code BaseEntity}，拿不到它对审计时间的 {@code @JsonFormat}，
     * 因此**必须自己声明** —— 否则 Jackson 会输出 ISO-8601 带时区的字符串，违反根 CLAUDE.md §6.1。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // ------------------------------------------------------------------
    // 瞬态展示字段（契约 resp_extra）：由列表查询 JOIN biz_book 填充，不落库
    // ------------------------------------------------------------------

    /** ISBN（JOIN `biz_book`） */
    @TableField(exist = false)
    private String isbn;

    /** 书名（JOIN `biz_book`） */
    @TableField(exist = false)
    private String title;

    /** 请求参数（瞬态，不落库；日期区间筛选走 {@code params[beginTime]}，理由见类注释） */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
