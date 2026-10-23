package com.tbgbook.bookstore.book.domain;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 标签打印响应体（`POST /book/book/label/print` 的 {@code data}）。
 *
 * <p>契约写的是「{@code data} 结构：list 数组，每项含 title/price/barCode/isbn/locationCode」，
 * 即 {@code data} 是个**含 {@code list} 的对象**。这里在 {@code list} 之外回显三个**生效值**：
 *
 * <ul>
 *   <li>{@code labelSize} / {@code copies} / {@code includePrice} 是请求里的可选项，
 *       服务端有默认值（{@code 60x40} / {@code 1} / {@code true}）。回显生效值后，
 *       前端渲染预览画布时不必自己再实现一份默认值 —— 两份默认值迟早会不一致。</li>
 * </ul>
 *
 * @author tbgbook
 */
@Data
public class BookLabelResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 标签列表（按入参 bookIds 顺序，每本书展开为 {@code copies} 张） */
    private List<BookLabel> list;

    /** 生效的标签规格 */
    private String labelSize;

    /** 生效的每本书份数 */
    private Integer copies;

    /** 生效的是否打印价格 */
    private Boolean includePrice;
}
