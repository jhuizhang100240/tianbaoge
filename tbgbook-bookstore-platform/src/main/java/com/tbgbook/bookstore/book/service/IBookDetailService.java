package com.tbgbook.bookstore.book.service;

import com.tbgbook.bookstore.book.domain.BookDetail;

/**
 * 图书全景详情 Service 接口（{@code GET /book/book/{bookId}/detail}，页面 A-08）。
 *
 * <p><b>为什么要单独一个服务而不是塞进 {@code IBookService}</b>：本接口是**跨域组合读模型**
 * —— 它要同时碰图书主档、教材扩展、库存、价格历史、变更日志五个部分。
 * 塞进 {@code BookServiceImpl} 会让那个类同时依赖库存域，而 {@code BookServiceImpl}
 * 是全项目的样板类，值得保持「只管图书主档自己的写路径」的边界。
 * 这里改为**依赖各域已有的服务**（{@code IBookService} / {@code IBookTextbookService} / …），
 * 不直接碰它们的 Mapper（库存除外，见实现类说明）。
 *
 * @author tbgbook
 */
public interface IBookDetailService
{
    /**
     * 查询图书全景详情。
     *
     * @param bookId 图书ID
     * @return 全景详情
     * @throws com.tbgbook.common.exception.ServiceException 404 图书不存在
     */
    BookDetail selectBookDetail(Long bookId);
}
