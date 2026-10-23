package com.tbgbook.bookstore.book.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * 单张标签的打印数据（`POST /book/book/label/print` 响应 list 的元素）。
 *
 * <p><b>字段与契约逐字对应</b>：{@code title} / {@code price} / {@code barCode}（BK...）/
 * {@code isbn} / {@code locationCode}，另加一个 {@code bookId} 便于前端把「份数展开」
 * 后的条目归回原书（一份书可能出多张标签，没有 bookId 就只能靠顺序猜）。
 *
 * <p>{@code barCode} 就是 {@code biz_book.book_no}（{@code BK} + 8 位流水，需求 §3.2 编码规则）
 * —— 条码即图书编号，不另生成。
 *
 * <p>{@code @JsonInclude(NON_NULL)}：{@code includePrice=false} 时 {@code price} 为 null，
 * 该字段**整条不出现在响应里**（契约：「是否打印价格」），而不是出现一个 {@code "price": null}
 * 让前端自己判空。
 *
 * @author tbgbook
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookLabel implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 图书ID */
    private Long bookId;

    /** 书名 */
    private String title;

    /** 定价。{@code includePrice=false} 时省略 */
    private BigDecimal price;

    /** 条码（= 图书编号，BK+8位流水） */
    private String barCode;

    /** ISBN */
    private String isbn;

    /**
     * 库位编码。
     *
     * <p>取**可售数最多的那个库位**，与 {@code ScanResult.shelfHint} 的取值口径一致
     * （「哪个库位最该去」是同一个业务判断，两处不能各定一套）。
     * 图书没有任何库存行时为 {@code null}（该字段随之省略）。
     */
    private String locationCode;
}
