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
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * 库存对象 biz_stock（正价书，按「图书 + 库位」一行；页面 A-14 库存总览 / A-15 库存明细）。
 *
 * <p><b>四类数量全部由库存流水推导，不接受直接写入</b>（FR-4.2 规则 1）：
 * 任何数量变更必须走入库 / 出库 / 盘点 / 调拨 / 报损等单据。
 * 因此请求体只收 {@code bookId} 与 {@code locationId} 两个字段，四类数量与
 * {@code safetyQty} / {@code version} 都在接口文档的 `skip` 里 —— 客户端传了会被忽略。
 *
 * <p><b>四类互斥恒等式</b>（FR-4.2 规则 2）：
 * {@code qtyAvailable + qtyLocked + qtyBorrowed + qtyDamaged = 实物总量}。
 * 这个不变量由各单据的服务端逻辑共同保证，**本实体不做校验**。
 *
 * <p><b>{@code version} 是乐观锁版本号</b>（FR-4.2 规则 3）：扣减时校验、冲突重试。
 * 这是阶段一 1.5 顺延过来的验证点 —— {@code biz_publisher} 没有 {@code version} 列，
 * 当时无法验证 {@code @Version} 是否真的生效，此处（以及后续的调拨/报损扣减）才是它的实测现场。
 *
 * <p><b>为什么不继承 {@code BaseEntity}</b>：本表只有 {@code create_time} / {@code update_time}，
 * 没有 {@code create_by} / {@code update_by} / {@code remark} / {@code del_flag}。
 * 代价是审计时间的 {@code @JsonFormat} 与瞬态 {@code params} 要自己补
 * （阶段二步骤② 已踩过这两个坑，见类内注释）。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_stock")
public class Stock implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 库存ID */
    @TableId(value = "stock_id", type = IdType.AUTO)
    private Long stockId;

    /** 门店ID（单店部署恒为 1，由数据库默认值填充） */
    @TableField("store_id")
    private Long storeId;

    /**
     * 图书ID。
     *
     * <p>⚠️ **不加 {@code @NotNull}**：本实体同时用于 POST 与 PUT，而 PUT 只允许改库位、
     * 请求体里**不带** {@code bookId}。在实体上标注解会让「改库位」被参数校验拦成 400
     * （实测踩到）。必填性由 {@code StockServiceImpl#addStock} 按操作判定、抛 400。
     */
    @TableField("book_id")
    private Long bookId;

    /**
     * 库位ID（0=未分配库位）。
     *
     * <p>同理不加 {@code @NotNull}，在 {@code addStock} 里显式校验。
     */
    @TableField("location_id")
    private Long locationId;

    /** 可售库存（服务端由单据维护，不接受传入） */
    @TableField("qty_available")
    private Integer qtyAvailable;

    /** 锁定库存（线上下单未取货、预订；服务端维护） */
    @TableField("qty_locked")
    private Integer qtyLocked;

    /** 借出库存（服务端维护） */
    @TableField("qty_borrowed")
    private Integer qtyBorrowed;

    /** 残损库存（服务端维护） */
    @TableField("qty_damaged")
    private Integer qtyDamaged;

    /** 安全库存（近30天日均销量×补货周期×安全系数；服务端维护） */
    @TableField("safety_qty")
    private Integer safetyQty;

    /**
     * 乐观锁版本号。
     *
     * <p>⚠️ 带 {@code @Version} 的实体在 {@code updateById} 时由
     * {@code OptimisticLockerInnerInterceptor} 自动加 {@code and version = ?}；
     * **影响 0 行时 MP 不抛异常**，必须由调用方检查影响行数（{@code ReferenceGuard#requireAffected}），
     * 否则并发修改会静默丢失。
     */
    @Version
    @TableField("version")
    private Integer version;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ------------------------------------------------------------------
    // 以下为瞬态字段：只用于查询条件，不落库
    // ------------------------------------------------------------------

    /** ISBN（筛选用 + 展示用；JOIN `biz_book` 精确匹配） */
    @TableField(exist = false)
    private String isbn;

    /** 书名（筛选用 + 展示用；JOIN `biz_book` 模糊匹配） */
    @TableField(exist = false)
    private String title;

    /**
     * 分类名称（展示用，JOIN `biz_category`）。
     *
     * <p>原型 A-14 的表格要展示「分类」列，而本表只有 {@code category_id} 在图书上 ——
     * 补这个响应字段是为了让前端不必按行回查（N+1）。
     */
    @TableField(exist = false)
    private String categoryName;

    /** 库位编码（展示用，JOIN `biz_location`；原型 A-14 的「库位」列） */
    @TableField(exist = false)
    private String locationCode;

    /** 库位名称（展示用，同上） */
    @TableField(exist = false)
    private String locationName;

    /** 是否只看有可售库存（{@code qty_available > 0}，计算标志，非表字段） */
    @TableField(exist = false)
    private Boolean hasAvailable;

    /**
     * 分类ID（查询与响应展示用，非本表字段）。
     *
     * <p>库存行上没有分类，分类挂在图书上；总览与库存明细都 JOIN {@code biz_book} 后用
     * {@code b.category_id} 过滤。
     */
    @TableField(exist = false)
    private Long categoryId;

    /**
     * 库存状态（计算字段：normal / low / zero / slow）。
     *
     * <p>零库存沿用以「存在库存流水」为前提，避免把刚建档、从未入库的图书误标断货；
     * 低库存按 {@code 0 < 可售 < 安全库存} 派生；滞销来自待处理库存预警。
     */
    @TableField(exist = false)
    private String stockStatus;

    /** 近 30 天日均销量（来自待处理库存预警，扫描任务按日汇总）。 */
    @TableField(exist = false)
    private BigDecimal avgSale30d;

    /**
     * 请求参数（瞬态，不落库）。
     *
     * <p>本实体不继承 {@code BaseEntity}，但列表的通用日期区间筛选走
     * {@code params[beginTime]} / {@code params[endTime]}，由 XML 读取 —— 故补同形 map。
     * ⚠️ 不补会让列表接口 500（{@code There is no getter for property named 'params'}，实测踩到）。
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
