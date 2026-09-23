package com.tbgbook.bookstore.book.domain;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 图书价格版本历史对象 biz_book_price_history
 *
 * <p><b>只增不改不删</b>（需求 FR-4.1 规则 5：价格变动必须留痕，供成本毛利追溯）。
 * 表里**没有** {@code update_by} / {@code update_time} / {@code remark} / {@code del_flag}，
 * 因此**不继承 {@code BaseEntity}** —— 否则 MP 会去写不存在的列。
 *
 * <p>写入时机：图书编辑时价格字段（定价 / 会员价 / 成本价）发生变化，
 * 由 {@code BookServiceImpl} 在同事务内逐项写入一条记录。
 * 读接口在阶段二步骤②交付。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_book_price_history")
public class BookPriceHistory implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 历史ID */
    @TableId(value = "history_id", type = IdType.AUTO)
    private Long historyId;

    /** 门店ID */
    @TableField("store_id")
    private Long storeId;

    /** 图书ID */
    @TableField("book_id")
    private Long bookId;

    /** 价格类型（1定价 2会员价 3成本价） */
    @TableField("price_type")
    private Integer priceType;

    /** 修改前价格 */
    @TableField("before_price")
    private BigDecimal beforePrice;

    /** 修改后价格 */
    @TableField("after_price")
    private BigDecimal afterPrice;

    /** 变更原因 */
    @TableField("change_reason")
    private String changeReason;

    /** 操作人（由 MyMetaObjectHandler 自动填充） */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 操作时间（同上）。
     *
     * <p>⚠️ 本实体不继承 {@code BaseEntity}，拿不到它对审计时间的 {@code @JsonFormat}，
     * 因此**必须自己声明** —— 否则 Jackson 会输出 ISO-8601 带时区的
     * {@code 2026-09-22T11:45:05.000+08:00}，违反根 CLAUDE.md §6.1
     * 「时间字段统一 yyyy-MM-dd HH:mm:ss」（实测踩到）。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 请求参数（瞬态，不落库）。
     *
     * <p>本实体**不继承 {@code BaseEntity}**（表里没有 {@code update_*} / {@code remark} /
     * {@code del_flag}），但列表接口的通用日期区间筛选走
     * {@code params[beginTime]} / {@code params[endTime]}，由 XML 读取 ——
     * 因此这里补一个同形的瞬态 map。
     * ⚠️ 不补的话 MyBatis 会报 {@code There is no getter for property named 'params'}
     * 并让列表接口 500（实测踩到）。
     */
    @TableField(exist = false)
    private Map<String, Object> params = new HashMap<>();
}
