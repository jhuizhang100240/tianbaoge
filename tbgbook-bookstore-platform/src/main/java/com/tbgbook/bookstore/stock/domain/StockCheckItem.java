package com.tbgbook.bookstore.stock.domain;

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

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 库存盘点明细对象 biz_stock_check_item。
 *
 * <p><b>「账面数」的口径（本类最重要的一条，文档未定义，此处明确）</b>：
 * <pre>
 *   bookQty = qty_available + qty_locked + qty_damaged     ← **不含 qty_borrowed**
 * </pre>
 * 即「**在场实物量**」：架上能数到的书。三类计入的理由：
 * <ul>
 *   <li>{@code 可售} / {@code 锁定} / {@code 残损} 都**物理在店里**，盘点人数得到</li>
 *   <li>{@code 借出} **在读者手上**，架上数不到 —— 若计入账面，每本借出的书
 *       都会变成一条假盘亏，正是 §4.2 规则 5 要避免的「不该记在盘点人头上」的差异</li>
 * </ul>
 * 差异调整**只落在 {@code qty_available} 上**，因此盘点结束后
 * 「在场实物量 == 实盘数」这个不变量成立（自测的核心断言）。
 *
 * <p><b>为什么不能直接用 {@code qty_available} 当账面数</b>：若店里有 3 册残损在架上，
 * 账面只算可售，盘点人数到它们就成了「盘盈」，会把可售数虚增 —— 而实物总量没变。
 * （可售 + 锁定 + 残损 的口径下不会出现这个问题。）
 *
 * <p><b>「是否已录入」由 {@code checkTime} 判定</b>：本表没有独立的标记列，
 * 而 {@code actualQty} 默认 0 无法区分「数了是 0」与「还没数」。
 * 录入实盘数时写 {@code checkTime} + {@code checkedBy}，故
 * {@code checkTime IS NULL} 即「未录入」（结束盘点时的校验依据）。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：只有 {@code check_time}，
 * 没有 {@code update_*} / {@code remark} / {@code del_flag}。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_stock_check_item")
public class StockCheckItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 差异类型：无差异 */
    public static final int DIFF_NONE = 0;

    /** 差异类型：盘盈（实盘 > 账面） */
    public static final int DIFF_GAIN = 1;

    /** 差异类型：盘亏（实盘 < 账面） */
    public static final int DIFF_LOSS = 2;

    /** 差异类型：盘点期差异（盘点期间发生的库存变动） */
    public static final int DIFF_PERIOD = 3;

    /** 明细ID */
    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    /** 盘点单ID */
    @TableField("check_id")
    private Long checkId;

    /** 图书ID */
    @TableField("book_id")
    private Long bookId;

    /** ISBN（冗余，生成时回填） */
    @TableField("isbn")
    private String isbn;

    /** 书名（冗余，生成时回填） */
    @TableField("title")
    private String title;

    /** 库位ID（0=未分配库位） */
    @TableField("location_id")
    private Long locationId;

    /**
     * 账面数（**在场实物量** = 可售 + 锁定 + 残损，生成盘点单时**快照**）。
     *
     * <p>快照而不是实时算：盘点的语义就是「与开始那一刻的账面对比」。
     * 详见类注释的口径说明。
     */
    @TableField("book_qty")
    private Integer bookQty;

    /** 实盘数（盘点人录入） */
    @Min(value = 0, message = "实盘数不能为负")
    @TableField("actual_qty")
    private Integer actualQty;

    /** 差异数量（服务端算：actualQty - bookQty） */
    @TableField("diff_qty")
    private Integer diffQty;

    /** 差异金额（服务端算：diffQty × 成本价） */
    @TableField("diff_amount")
    private BigDecimal diffAmount;

    /** 差异类型（0无差异 1盘盈 2盘亏 3盘点期差异）。服务端算 */
    @TableField("diff_type")
    private Integer diffType;

    /**
     * 是否盘点期差异（1=是）。结束盘点时判定：该行图书的**当前在场实物量**
     * 与快照 {@code bookQty} 不一致，说明盘点期间发生了出入库。
     * 这类差异**单独列出**，不混入盘点人的责任差异（§4.2 规则 5）。
     */
    @TableField("is_period_diff")
    private Integer isPeriodDiff;

    /** 差异原因（差异行必填，由 service 在结束盘点前校验） */
    @Size(max = 200, message = "差异原因不能超过 200 个字符")
    @TableField("reason")
    private String reason;

    /** 处理建议 */
    @Size(max = 200, message = "处理建议不能超过 200 个字符")
    @TableField("suggestion")
    private String suggestion;

    /** 盘点人 */
    @TableField("checked_by")
    private String checkedBy;

    /** 盘点时间。**非空即「已录入」**（见类注释） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("check_time")
    private LocalDateTime checkTime;

    /**
     * 请求参数（瞬态，不落库）。
     *
     * <p>列表接口的通用日期区间筛选走 {@code params[beginTime]} / {@code params[endTime]}，
     * 不补这个 map 会让列表接口 500（{@code There is no getter for property named 'params'}）。
     */
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
