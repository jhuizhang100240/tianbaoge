package com.tbgbook.bookstore.book.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.domain.BookChangeLog;
import com.tbgbook.bookstore.book.domain.BookDetail;
import com.tbgbook.bookstore.book.domain.BookPriceHistory;
import com.tbgbook.bookstore.book.service.IBookChangeLogService;
import com.tbgbook.bookstore.book.service.IBookDetailService;
import com.tbgbook.bookstore.book.service.IBookPriceHistoryService;
import com.tbgbook.bookstore.book.service.IBookService;
import com.tbgbook.bookstore.book.service.IBookTextbookService;
import com.tbgbook.bookstore.stock.mapper.StockMapper;

import lombok.RequiredArgsConstructor;

/**
 * 图书全景详情 Service 实现（A-08）。
 *
 * <p>全程只读，故**不加 {@code @Transactional}** —— 五个部分各自一条查询，
 * 不构成一个需要原子性的写单元；加事务只会白占一个连接。
 *
 * <p><b>唯一一处直接依赖 Mapper 的地方是 {@link StockMapper}</b>：
 * {@code IStockService} 没有「按图书查各库位库存」的方法（那属于库存总览/扫码的能力，
 * 在 {@code IInventoryOverviewService} 里），而这里要的正是 {@code ScanStock} 那个形状。
 * 与其为一个只读查询给 {@code IStockService} 加方法、再让 {@code IInventoryOverviewService}
 * 转发一次，不如直接用它的 Mapper —— 且**同模块内已有反向先例**
 * （{@code InventoryOverviewServiceImpl} 直接注入 {@code BookMapper}）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class BookDetailServiceImpl implements IBookDetailService
{
    /**
     * 「最近 N 条」的 N。
     *
     * <p>本端点是**概览**（契约：「一次请求返回，避免前端串行多次调用」），
     * 把一本书的全部价格版本与变更日志一次性吐出来，长年累积的图书会有成百上千行。
     * 页签要看全量走各自的**分页**列表端点：
     * {@code GET /book/book/price-history/list}、{@code GET /book/book/change-log/list}。
     */
    public static final int RECENT_LIMIT = 20;

    private final IBookService bookService;

    private final IBookTextbookService bookTextbookService;

    private final IBookPriceHistoryService bookPriceHistoryService;

    private final IBookChangeLogService bookChangeLogService;

    private final StockMapper stockMapper;

    @Override
    public BookDetail selectBookDetail(Long bookId)
    {
        // 主档走 IBookService 而不是自己的查询：那里已经做了「不存在抛 404」与
        // 「成本价按 book:cost:view 裁剪」（FR-4.6 规则 5）两件事，绕开就等于重做一遍，
        // 迟早两处不一致。
        Book book = bookService.selectBookById(bookId);

        BookDetail detail = new BookDetail();
        detail.setBook(book);
        // 教材扩展是 1:1（主键即 bookId）。非教材没有行 → getById 返回 null，
        // 正好是契约要的「textbook 教材扩展（非教材为 null）」，不需要额外判 bookType。
        detail.setTextbook(bookTextbookService.getById(bookId));
        detail.setStocks(stockMapper.selectStocksByBookId(bookId));
        detail.setPriceHistory(recentPriceHistory(bookId));
        detail.setChangeLogs(recentChangeLogs(bookId));
        return detail;
    }

    /**
     * 最近 {@link #RECENT_LIMIT} 条价格版本（倒序）。
     *
     * <p>上限走 {@code params.limit}，由 XML 里的可选 {@code <if>} 转成 {@code limit} 子句
     * —— 复用既有的列表查询，不为「取前 N 条」再写一个几乎相同的 select。
     */
    private List<BookPriceHistory> recentPriceHistory(Long bookId)
    {
        BookPriceHistory query = new BookPriceHistory();
        query.setBookId(bookId);
        query.getParams().put("limit", RECENT_LIMIT);
        return bookPriceHistoryService.selectBookPriceHistoryList(query);
    }

    /** 最近 {@link #RECENT_LIMIT} 条变更日志（倒序），同上走 {@code params.limit} */
    private List<BookChangeLog> recentChangeLogs(Long bookId)
    {
        BookChangeLog query = new BookChangeLog();
        query.setBookId(bookId);
        query.getParams().put("limit", RECENT_LIMIT);
        return bookChangeLogService.selectBookChangeLogList(query);
    }
}
