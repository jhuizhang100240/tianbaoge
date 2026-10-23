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
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * 库存预警对象 biz_stock_warning（页面 A-23 库存预警中心）。
 *
 * <p><b>由扫描任务生成，不提供手工新增/修改/删除</b>：本实体只用于读，
 * 唯一的写入口是 {@code PUT /inventory/warning/handle}（改处置状态）与扫描任务本身。
 * 这与快照明细同属「系统产出、人只读」的一类。
 *
 * <p><b>不继承 {@code BaseEntity}</b>：本表有 {@code create_time} / {@code update_time}，
 * 但**没有** {@code create_by} / {@code update_by} / {@code remark} / {@code del_flag}
 * —— 沿用会让 MP 去写不存在的列。处置人由 {@code handleBy} 承载（语义更准：
 * 处置人 ≠ 最后修改人，扫描任务也会改这一行）。代价是审计时间的 {@code @JsonFormat}
 * 与瞬态 {@code params} 要自己补。
 *
 * <p><b>四类的判定口径</b>（阈值全部来自 {@code sys_config}，NFR-M4）：
 * <ul>
 *   <li>{@code 1 补货} = {@code 0 < 可售 < 安全库存}；
 *       安全库存 = 近30天日均销量 × {@code biz.stock.replenish.days} ×
 *       {@code biz.stock.safety.factor}（§3.2 库存规则 2），由扫描任务写回
 *       {@code biz_stock.safety_qty}</li>
 *   <li>{@code 2 滞销} = 实物总量 &gt; 0 且距最后动销时间 ≥ {@code biz.stock.slowmoving.days} 天
 *       （从未动销的以首次入库时间为准）</li>
 *   <li>{@code 3 临期} = 图书被**已发布**的教材清单引用且该清单 {@code endTime} 距今 ≤
 *       {@code biz.stock.expiring.days} 天</li>
 *   <li>{@code 4 零库存} = 可售为 0 **且该图书有动销史**（有过库存流水）——
 *       刚建档从未进货的书不算断货</li>
 * </ul>
 * 前两类由「补货」页签合并展示（原型 A-23：补货预警（低库存 + 零库存）），
 * 故 1 与 4 的判定条件**刻意互斥**（一个要求可售 &gt; 0、一个要求 = 0），
 * 同一本书不会同时冒出两条同一页签的预警。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_stock_warning")
public class StockWarning implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 预警类型：补货（低库存） */
    public static final int TYPE_REPLENISH = 1;

    /** 预警类型：滞销 */
    public static final int TYPE_SLOW_MOVING = 2;

    /** 预警类型：临期（教材季末） */
    public static final int TYPE_EXPIRING = 3;

    /** 预警类型：零库存（断货） */
    public static final int TYPE_OUT_OF_STOCK = 4;

    /** 处理状态：待处理 */
    public static final int HANDLE_PENDING = 1;

    /** 处理状态：已处理 */
    public static final int HANDLE_DONE = 2;

    /** 处理状态：已忽略 */
    public static final int HANDLE_IGNORED = 3;

    /** 预警ID */
    @TableId(value = "warning_id", type = IdType.AUTO)
    private Long warningId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /** 图书ID */
    @TableField("book_id")
    private Long bookId;

    /** ISBN（冗余，扫描时回填） */
    @TableField("isbn")
    private String isbn;

    /** 书名（冗余，扫描时回填） */
    @TableField("title")
    private String title;

    /** 预警类型（1补货 2滞销 3临期 4零库存） */
    @TableField("warning_type")
    private Integer warningType;

    /** 当前可售数量（扫描时点） */
    @TableField("current_qty")
    private Integer currentQty;

    /** 安全库存（扫描时按 §3.2 库存规则 2 算出，同一份值也写回 `biz_stock.safety_qty`） */
    @TableField("safety_qty")
    private Integer safetyQty;

    /** 缺口数量 = max(安全库存 − 可售, 0) */
    @TableField("gap_qty")
    private Integer gapQty;

    /** 近30天日均销量 */
    @TableField("avg_sale_30d")
    private BigDecimal avgSale30d;

    /**
     * 预计可售天数 = 可售 ÷ 近30天日均销量（向下取整）。
     *
     * <p>日均销量为 0 时**填 0**，含义是「无销量数据、无法估算」——
     * 而不是「马上卖完」。前端按 0 展示为「—」而不是红色告警。
     */
    @TableField("sale_days_30d")
    private Integer saleDays30d;

    /** 建议补货量 = max(安全库存 − 可售, 0)（安全库存为 0 时也是 0，表示无数据可依据） */
    @TableField("suggest_qty")
    private Integer suggestQty;

    /** 最后动销时间（滞销判定用；从未动销时为 null） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("last_sale_time")
    private LocalDateTime lastSaleTime;

    /** 处理状态（1待处理 2已处理 3已忽略） */
    @TableField("handle_status")
    private Integer handleStatus;

    /** 处理人（服务端取当前登录用户） */
    @TableField("handle_by")
    private String handleBy;

    /** 处理时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("handle_time")
    private LocalDateTime handleTime;

    /** 处理说明 */
    @TableField("handle_remark")
    private String handleRemark;

    /** 生成时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ------------------------------------------------------------------
    // 瞬态：只用于查询条件
    // ------------------------------------------------------------------

    /**
     * 请求参数（瞬态，不落库）。
     * 本实体不继承 {@code BaseEntity}，日期区间筛选走 {@code params[beginTime]}，故需自补。
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
