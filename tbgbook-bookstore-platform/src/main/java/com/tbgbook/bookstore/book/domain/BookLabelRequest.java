package com.tbgbook.bookstore.book.domain;

import java.io.Serializable;

import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 标签打印请求体（`POST /book/book/label/print`，FR-4.1.7 / A-11）。
 *
 * <p>本端点只**生成打印数据**，渲染与调起打印由前端负责（契约明写）。
 *
 * @author tbgbook
 */
@Data
public class BookLabelRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 标签规格默认值（契约：默认 60x40） */
    public static final String DEFAULT_LABEL_SIZE = "60x40";

    /** 每本书标签份数默认值（契约：默认 1） */
    public static final int DEFAULT_COPIES = 1;

    /**
     * 单批图书种数上限（契约：「bookIds 为空或超过单批上限（建议 ≤ 200）」）。
     */
    public static final int MAX_BATCH_SIZE = 200;

    /**
     * 每本书份数上限 —— **契约没写，这是防御性上限**。
     *
     * <p>份数会与种数相乘得到响应条目数，若不封顶，{@code copies=100000} 能在一次请求里
     * 造出两千万个对象把服务打挂。100 份远超真实贴标场景（一册书贴 3~5 张已属少见）。
     */
    public static final int MAX_COPIES = 100;

    /**
     * 图书ID列表，逗号分隔。
     *
     * <p>⚠️ **不加 {@code @NotBlank}**：契约给本端点定的错误码是
     * 「422 bookIds 为空或超过单批上限」。若在这里标 {@code @NotBlank}，
     * 空串会先被 {@code @Valid} 拦成 <b>400</b>，与契约不符。
     * 故「为空」交由 Service 判、抛 422（{@code BookServiceImpl#buildLabels}）。
     * {@code @Size} 只做长度兜底，防止超长串在解析阶段就吃满内存。
     */
    @Size(max = 2000, message = "图书ID列表过长")
    private String bookIds;

    /** 标签规格，默认 60x40。**原样回显**，本端点不解释它的含义（渲染在前端） */
    @Size(max = 20, message = "标签规格不能超过 20 个字符")
    private String labelSize;

    /** 是否打印价格，默认 true。传 false 时响应里不含 price 字段 */
    private Boolean includePrice;

    /** 每本书标签份数，默认 1 */
    private Integer copies;
}
