package com.tbgbook.bookstore.book.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.domain.BookLabel;
import com.tbgbook.bookstore.book.domain.BookOption;

/**
 * 图书主档 Mapper 接口。
 *
 * @author tbgbook
 */
public interface BookMapper extends BaseMapper<Book>
{
    /**
     * 查询图书列表（含四类库存聚合）。
     *
     * @param book 查询条件
     * @return 图书集合
     */
    List<Book> selectBookList(Book book);

    /**
     * 图书下拉选项（{@code GET /book/book/options}）。
     *
     * <p>轻量查询：**不分页、不聚合库存、不查成本价**。排序把「编号 / ISBN 精确命中」置顶
     * —— 该接口的主要消费场景是扫码枪录入，扫到的码必须第一条命中。
     *
     * @param keyword 书名 / ISBN / 图书编号的模糊关键词；null 或空表示不过滤
     * @param limit   返回上限（调用方已夹到 [1,50]）
     * @return 选项集合
     */
    List<BookOption> selectBookOptions(@Param("keyword") String keyword, @Param("limit") int limit);

    /**
     * 标签打印数据（{@code POST /book/book/label/print}）。
     *
     * <p>每条含书名 / 定价 / 条码（= book_no）/ ISBN / 主库位编码。
     * 主库位取**可售数最多**的那个库位，与 {@code ScanResult.shelfHint} 同口径。
     *
     * @param bookIds 图书ID集合（调用方已去重、已校验非空）
     * @return 标签数据；**顺序不保证与入参一致**，调用方按 bookId 归位
     */
    List<BookLabel> selectBookLabels(@Param("bookIds") Collection<Long> bookIds);

    /**
     * 取 biz_book 的下一个自增主键（用于预生成 bookNo）。
     *
     * <p>{@code book_no} 是 {@code NOT NULL} 且**无默认值**，因此不能「先插入再回填」——
     * 插入时就必须给出值。这里从 {@code information_schema} 取 AUTO_INCREMENT 作为
     * 预估主键；插入后若真实主键与预估不符（并发场景），再按真实主键校正一次。
     *
     * @return 预估的下一个主键值
     */
    Long selectNextBookId();
}
