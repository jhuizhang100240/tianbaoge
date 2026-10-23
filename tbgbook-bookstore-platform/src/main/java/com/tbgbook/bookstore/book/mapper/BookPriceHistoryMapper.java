package com.tbgbook.bookstore.book.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.book.domain.BookPriceHistory;

/**
 * 图书价格版本历史 Mapper。
 *
 * <p>本表**只增不改不删**（需求 FR-4.1 规则 5），因此写路径只有 insert
 * （由 BookServiceImpl 在改价时调用），对外只提供查询。
 *
 * @author tbgbook
 */
public interface BookPriceHistoryMapper extends BaseMapper<BookPriceHistory>
{
    /**
     * 查询价格版本历史列表。
     *
     * @param history 查询条件
     * @return 历史记录集合
     */
    List<BookPriceHistory> selectBookPriceHistoryList(BookPriceHistory history);
}
