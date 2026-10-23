package com.tbgbook.bookstore.stock.domain;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * 单份快照的**逐图书聚合**行（Mapper 查询结果，**不落库**）。
 *
 * <p>做成「先各查一次、再在服务层合并」而不是一条自连接 SQL：对比是
 * 「两张快照的同一本书相减」，两侧的数据来源完全相同；合并逻辑放在 Java 里
 * 才能用一句 {@code Map} 讲清楚，写成 SQL 则要两次 LEFT JOIN 自己再对齐 NULL。
 *
 * <p>数量口径是**实物总量**（四类之和），与快照主表的 {@code totalQty} 一致 ——
 * 对比页关心的是「货多了还是少了」，而不是四类内部的此消彼长。
 *
 * @author tbgbook
 */
@Data
public class SnapshotBookAgg implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 图书ID */
    private Long bookId;

    /** ISBN（快照明细里的冗余列） */
    private String isbn;

    /** 书名（JOIN `biz_book` 取当前书名；书被删则可能为 null） */
    private String title;

    /** 实物总量（四类之和，跨全部库位） */
    private Integer qty;

    /** 库存金额（成本口径；成本价为空的行记为 0） */
    private BigDecimal amount;
}
