package com.tbgbook.bookstore.book.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.book.domain.BookChangeLog;

/**
 * 图书信息变更日志 Mapper。
 *
 * <p>本表**只增不改不删**（需求 FR-4.1.9），写路径只有 insert
 * （由 BookServiceImpl 在编辑时调用），对外只提供查询。
 *
 * @author tbgbook
 */
public interface BookChangeLogMapper extends BaseMapper<BookChangeLog>
{
    /**
     * 查询图书信息变更日志列表。
     *
     * @param log 查询条件
     * @return 日志集合
     */
    List<BookChangeLog> selectBookChangeLogList(BookChangeLog log);
}
