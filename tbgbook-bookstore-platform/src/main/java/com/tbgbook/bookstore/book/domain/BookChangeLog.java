package com.tbgbook.bookstore.book.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 图书信息变更日志对象 biz_book_change_log
 *
 * <p><b>只增不改不删</b>（需求 FR-4.1.9：价格、库存、状态等关键字段变更历史）。
 * 表里没有 {@code update_*} / {@code remark} / {@code del_flag}，
 * 因此**不继承 {@code BaseEntity}**。
 *
 * <p>通用「字段名 — 前值 — 后值」结构，由 {@code BookServiceImpl} 在编辑时
 * 对**白名单内的关键字段**逐项比对后写入。白名单与中文名见
 * {@code BookServiceImpl.TRACKED_FIELDS} —— 只记录业务关心的字段，
 * 不是全字段 diff（否则一次编辑能刷出几十行噪音）。
 *
 * @author tbgbook
 */
@Data
@TableName("biz_book_change_log")
public class BookChangeLog implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 日志ID */
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    /** 门店ID */
    @TableField("store_id")
    private Long storeId;

    /** 图书ID */
    @TableField("book_id")
    private Long bookId;

    /** 字段名（实体属性名，便于前端按字段过滤） */
    @TableField("field_name")
    private String fieldName;

    /** 字段中文名 */
    @TableField("field_label")
    private String fieldLabel;

    /** 变更前值 */
    @TableField("before_value")
    private String beforeValue;

    /** 变更后值 */
    @TableField("after_value")
    private String afterValue;

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
