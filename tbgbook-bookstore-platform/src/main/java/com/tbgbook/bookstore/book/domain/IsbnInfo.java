package com.tbgbook.bookstore.book.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/**
 * 外部书库返回的书目信息（**供应商中立**）。
 *
 * <p>这是「可插拔适配器」的边界类型（需求 §7.2 部署要点 1）：{@code IsbnProvider} 的各实现
 * 负责把自家响应映射成这个形状，调用方（{@code IsbnLookupServiceImpl}）只认它。
 * 后续若要换用付费源（如极速数据，字段最全），**只换实现、不改调用方与接口契约**。
 *
 * <p>字段与 {@code Book} 的同名属性对齐，便于直接回填表单。
 *
 * @author tbgbook
 */
@Data
public class IsbnInfo implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 归一化后的 ISBN */
    private String isbn;

    /** 书名 */
    private String title;

    /** 副标题 */
    private String subtitle;

    /** 作者（多作者以「、」连接） */
    private String author;

    /** 译者（同上） */
    private String translator;

    /** 出版社名称（**只是名字，不是 publisher_id** —— 落库前仍需人工选出版社） */
    private String publisherName;

    /** 出版日期 */
    private LocalDate publishDate;

    /** 版次 */
    private String edition;

    /** 装帧 */
    private String binding;

    /** 页数 */
    private Integer pageCount;

    /** 定价 */
    private BigDecimal price;

    /** 封面地址 */
    private String coverUrl;

    /** 内容简介 */
    private String summary;

    /** 数据来源标识（由供应商实现自报，如 {@code neodb}） */
    private String source;
}
