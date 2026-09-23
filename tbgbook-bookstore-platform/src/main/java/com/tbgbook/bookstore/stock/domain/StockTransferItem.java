package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Min;

import lombok.Data;

/**
 * 库存调拨明细对象 biz_stock_transfer_item。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：本表只有 {@code create_time}，
 * 没有 {@code create_by} / {@code update_*} / {@code remark} / {@code del_flag}。
 * 代价是审计时间的 {@code @JsonFormat} 与瞬态 {@code params} 要自己补。
 *
 * <p><b>{@code isbn} / {@code title} 是冗余列</b>：单据要能脱离图书表独立呈现
 * （图书改名后，历史单据应显示当时的书名）。由服务端按 {@code bookId} 回填。
 *
 * <p><b>调拨不需要成本列</b>：调拨不改变实物总量、也不改变图书的成本价
 * （移动加权平均按全库位的实物总量算，挪库位不改变这个量）。
 * 故本表没有 {@code unit_cost} / {@code amount} —— 与入库明细的差异，是有意的。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_stock_transfer_item")
public class StockTransferItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 明细ID */
    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    /** 调拨单ID */
    @TableField("transfer_id")
    private Long transferId;

    /**
     * 图书ID。
     *
     * <p>⚠️ **不加 {@code @NotNull}**：本实体 POST/PUT 共用，而 PUT 允许只改数量
     * （{@code bookId} 沿用原行）。在字段上标必填会让这种 PUT 被 {@code @Valid} 拦成 400
     * —— 与 {@code StockInItem#bookId} 上踩过的是同一个坑。
     * 必填性由 {@code StockTransferItemServiceImpl#addStockTransferItem} 按**操作**判定。
     */
    @TableField("book_id")
    private Long bookId;

    /** ISBN（冗余，服务端按 bookId 回填） */
    @TableField("isbn")
    private String isbn;

    /** 书名（冗余，服务端按 bookId 回填） */
    @TableField("title")
    private String title;

    /**
     * 调拨数量。
     *
     * <p>必填性由 service 按操作判定（新增必填、PUT 可沿用原值）。范围校验留着：
     * {@code null} 不会触发 {@code @Min}，所以它与「PUT 可以省略」不冲突。
     * 下界为 1（不是 0）—— 调拨 0 册没有任何意义，而 0 值行在确认时会白占一次
     * 库存校验与两条流水；确实无意义的行应当删掉。
     */
    @Min(value = 1, message = "调拨数量至少为 1")
    @TableField("qty")
    private Integer qty;

    /**
     * 创建时间。
     *
     * <p>⚠️ 本实体不继承 {@code BaseEntity}，拿不到它对审计时间的 {@code @JsonFormat}，
     * 因此**必须自己声明** —— 否则 Jackson 会输出 ISO-8601 带时区的
     * {@code 2026-09-23T11:45:05.000+08:00}，违反根 CLAUDE.md §6.1。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 请求参数（瞬态，不落库）。
     *
     * <p>列表接口的通用日期区间筛选走 {@code params[beginTime]} / {@code params[endTime]}，
     * 由 XML 读取 —— 不补这个 map 会让列表接口 500
     * （{@code There is no getter for property named 'params'}，实测踩到）。
     */
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
