package com.tbgbook.bookstore.book.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 图书下拉选项（`GET /book/book/options`）。
 *
 * <p>供其它模块的「选书」控件用的**轻量**结构：只有 id / 编号 / ISBN / 书名 / 定价 / 封面，
 * 不带四类库存聚合、不带成本价 —— 后者既要多扫一遍 `biz_stock`，又要按权限裁剪，
 * 对一个下拉框毫无价值（契约明写「只返回 id/编号/书名/定价」）。
 *
 * <p><b>不含成本价是刻意的</b>：若带上 {@code costPrice}，就还得走
 * {@code book:cost:view} 裁剪逻辑（FR-4.6 规则 5）。干脆不查，权限问题自然不存在。
 *
 * @author tbgbook
 */
@Data
public class BookOption implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 图书ID */
    private Long bookId;

    /** 图书编号（BK+8位流水） */
    private String bookNo;

    /** ISBN */
    private String isbn;

    /** 书名 */
    private String title;

    /** 定价 */
    private BigDecimal price;

    /** 封面地址 */
    private String coverUrl;
}
