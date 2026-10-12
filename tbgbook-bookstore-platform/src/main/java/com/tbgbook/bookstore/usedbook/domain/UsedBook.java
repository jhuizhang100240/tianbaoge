package com.tbgbook.bookstore.usedbook.domain;

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

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 二手书库存对象 biz_used_book（页面 A-42、B-15，FR-4.2.3）。
 *
 * <p><b>二手书按册管理</b>：独立库存池与独立 SKU，**禁止与正价书库存混算**
 * （§3.2 库存规则 5、§4.2 规则 6）。副本唯一条码 {@code uk_bar_code}。
 * 本资源的行**由回收验收自动生成**（阶段六第二批），{@code POST} 仅用于存量手工建档补录；
 * 上架定价走 {@code POST /usedbook/stock/{id}/shelf}。
 *
 * <p>{@code stockStatus}：1在售 2锁定 3已售 4下架 5转报损。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_used_book")
public class UsedBook extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 库存状态：1在售 */
    public static final int STATUS_ON_SALE = 1;

    /** 库存状态：2锁定 */
    public static final int STATUS_LOCKED = 2;

    /** 库存状态：3已售 */
    public static final int STATUS_SOLD = 3;

    /** 库存状态：4下架 */
    public static final int STATUS_OFF_SHELF = 4;

    /** 库存状态：5转报损 */
    public static final int STATUS_DAMAGED = 5;

    /** 二手书副本ID */
    @TableId(value = "used_book_id", type = IdType.AUTO)
    private Long usedBookId;

    /** 门店ID */
    @TableField("store_id")
    private Long storeId;

    /** 副本唯一条码（BC+yyyyMMdd+6 位流水，见 A44） */
    @TableField("bar_code")
    private String barCode;

    /** 独立 SKU 标识（与正价书隔离，见 A44） */
    @TableField("sku_code")
    private String skuCode;

    /** 来源回收单ID（手工补录为 0） */
    @TableField("recycle_id")
    private Long recycleId;

    /** 来源回收单明细ID（手工补录为 0） */
    @TableField("recycle_item_id")
    private Long recycleItemId;

    /** 关联正价书图书ID（0=未建档） */
    @TableField("book_id")
    private Long bookId;

    /** ISBN */
    @Size(max = 20, message = "ISBN 不能超过 20 个字符")
    @TableField("isbn")
    private String isbn;

    /** 书名 */
    @Size(max = 200, message = "书名不能超过 200 个字符")
    @TableField("title")
    private String title;

    /** 作者 */
    @Size(max = 200, message = "作者不能超过 200 个字符")
    @TableField("author")
    private String author;

    /** 出版社 */
    @Size(max = 100, message = "出版社不能超过 100 个字符")
    @TableField("publisher_name")
    private String publisherName;

    /** 封面地址 */
    @Size(max = 500, message = "封面地址不能超过 500 个字符")
    @TableField("cover_url")
    private String coverUrl;

    /** 成色等级ID */
    @TableField("condition_grade_id")
    private Long conditionGradeId;

    /** 成色名称（冗余，列表醒目展示） */
    @TableField("condition_grade_name")
    private String conditionGradeName;

    /** 原价（划线价） */
    @TableField("origin_price")
    private BigDecimal originPrice;

    /** 二手售价 */
    @TableField("used_price")
    private BigDecimal usedPrice;

    /** 回收成本（最终回收价） */
    @TableField("cost_price")
    private BigDecimal costPrice;

    /** 库位ID */
    @TableField("location_id")
    private Long locationId;

    /** 库存状态（1在售 2锁定 3已售 4下架 5转报损） */
    @TableField("stock_status")
    private Integer stockStatus;

    /** 锁定该副本的订单ID */
    @TableField("lock_order_id")
    private Long lockOrderId;

    /** 锁定时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("lock_time")
    private LocalDateTime lockTime;

    /** 上架时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("shelf_time")
    private LocalDateTime shelfTime;

    /** 售出时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("sold_time")
    private LocalDateTime soldTime;

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
