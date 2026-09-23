package com.tbgbook.bookstore.book.domain;

import java.io.Serializable;
import java.util.List;

import com.tbgbook.bookstore.stock.domain.ScanStock;

import lombok.Data;

/**
 * 图书全景详情（`GET /book/book/{bookId}/detail`，页面 A-08）。
 *
 * <p>契约要求「一次请求返回，避免前端串行多次调用」—— 故这里是**跨域的组合视图**，
 * 不是单表记录：主档 + 教材扩展 + 各库位库存 + 价格版本 + 变更日志。
 *
 * <p><b>为什么不做成一个继承 {@code Book} 的类</b>：五个部分各有自己的字段语义
 * （教材扩展非教材为 null、库存是数组、两个日志是只增不改的流水），
 * 拍平进一个类会让「哪些字段在什么情况下有值」无从表达。
 *
 * <p><b>两处「最近 N 条」</b>：{@code priceHistory} / {@code changeLogs} 各取最近
 * {@code BookDetailServiceImpl.RECENT_LIMIT} 条 —— 本端点是**概览**，
 * 页签要看全量走各自的列表端点（{@code /book/book/price-history/list}、
 * {@code /book/book/change-log/list}，两者都支持分页）。
 *
 * <p>{@code stocks} 复用扫码查书的 {@link ScanStock}（同一形状：库位 + 四类数量），
 * 不另立一个字段完全相同的 DTO。
 *
 * @author tbgbook
 */
@Data
public class BookDetail implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 图书主档。{@code costPrice} 仅 {@code book:cost:view} 权限返回，否则为 null（FR-4.6 规则 5） */
    private Book book;

    /** 教材扩展信息。非教材（或尚未维护）时为 {@code null} */
    private BookTextbook textbook;

    /** 各库位库存（按可售数倒序）。无库存行时为空数组 */
    private List<ScanStock> stocks;

    /** 价格版本历史（最近 N 条，倒序） */
    private List<BookPriceHistory> priceHistory;

    /** 信息变更日志（最近 N 条，倒序） */
    private List<BookChangeLog> changeLogs;
}
