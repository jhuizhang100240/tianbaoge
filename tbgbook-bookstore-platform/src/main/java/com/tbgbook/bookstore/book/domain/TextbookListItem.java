package com.tbgbook.bookstore.book.domain;

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

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 教材清单明细对象 biz_textbook_list_item（页面 A-13 清单详情）
 *
 * <p><b>为什么不继承 {@code BaseEntity}</b>：本表只有 {@code create_time} /
 * {@code update_time}，**没有** {@code store_id} / {@code create_by} / {@code update_by} /
 * {@code remark} / {@code del_flag} —— 继承会让 MP 去写不存在的列。
 * 代价是 {@code BaseEntity} 的两样东西要自己补（阶段二步骤② 已踩过这两个坑）：
 * <ol>
 *   <li>审计时间的 {@code @JsonFormat} —— 不补的话 Jackson 输出 ISO-8601 带时区的
 *       {@code 2026-09-22T11:45:05.000+08:00}，违反根 CLAUDE.md §6.1</li>
 *   <li>瞬态 {@code params} map —— 列表的通用日期区间筛选读
 *       {@code params[beginTime]} / {@code params[endTime]}，不补则 MyBatis 抛
 *       {@code There is no getter for property named 'params'} 并让列表 500</li>
 * </ol>
 *
 * <p><b>三类由服务端维护的字段</b>（接口文档「教材清单明细」业务约束）：
 * <ul>
 *   <li>{@code isbn} / {@code title} / {@code price} —— 列注释写明「冗余」，
 *       由服务端按 {@code bookId} 查 {@code biz_book} **回填**，客户端传值被忽略
 *       （沿用 {@code publisherName} 按 {@code publisherId} 回填的既有做法）</li>
 *   <li>{@code pickedQty} —— 由取书核销累减（阶段四），当前一律为 0</li>
 *   <li>{@code remainQty} —— 固定 {@code arrivedQty - pickedQty}</li>
 *   <li>{@code status} —— 由数量关系派生，见 {@code TextbookListItemServiceImpl#deriveStatus}</li>
 * </ul>
 *
 * @author tbgbook
 */
@Data
@TableName("biz_textbook_list_item")
public class TextbookListItem implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 明细ID */
    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    /** 清单ID */
    @NotNull(message = "清单ID不能为空")
    @TableField("list_id")
    private Long listId;

    /** 图书ID */
    @NotNull(message = "图书ID不能为空")
    @TableField("book_id")
    private Long bookId;

    /** ISBN（冗余，服务端按 bookId 回填） */
    @Size(max = 20, message = "ISBN 不能超过 20 个字符")
    @TableField("isbn")
    private String isbn;

    /** 书名（冗余，服务端按 bookId 回填） */
    @Size(max = 200, message = "书名不能超过 200 个字符")
    @TableField("title")
    private String title;

    /** 定价（服务端按 bookId 回填） */
    @TableField("price")
    private BigDecimal price;

    /** 适用专业 */
    @Size(max = 100, message = "适用专业不能超过 100 个字符")
    @TableField("major")
    private String major;

    /** 适用学期 */
    @Size(max = 20, message = "适用学期不能超过 20 个字符")
    @TableField("term")
    private String term;

    /** 开课院系 */
    @Size(max = 100, message = "开课院系不能超过 100 个字符")
    @TableField("college")
    private String college;

    /** 预订数 */
    @Min(value = 0, message = "预订数不能为负")
    @TableField("reserve_qty")
    private Integer reserveQty;

    /** 到货数 */
    @Min(value = 0, message = "到货数不能为负")
    @TableField("arrived_qty")
    private Integer arrivedQty;

    /** 已取数（服务端由取书核销累减，不接受传入） */
    @TableField("picked_qty")
    private Integer pickedQty;

    /** 剩余数（服务端计算 = arrivedQty - pickedQty） */
    @TableField("remain_qty")
    private Integer remainQty;

    /** 状态（1待到货 2已到货待取书 3部分取书 4已取完，服务端派生） */
    @TableField("status")
    private Integer status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 请求参数（瞬态，不落库）。
     * 见类注释第 2 条：不补这个 map，列表的日期区间筛选会让接口 500。
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
