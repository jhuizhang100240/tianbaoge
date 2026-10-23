package com.tbgbook.bookstore.app.service;

import java.util.Map;

import com.tbgbook.bookstore.app.domain.request.ReserveSubmitRequest;

/**
 * 会员端教材预订 Service（B-12）。
 */
public interface IAppTextbookService
{
    Map<String, Object> lists(String term, String major, String college, Long listId);

    Map<String, Object> submit(ReserveSubmitRequest request);

    Map<String, Object> myList(Integer status);

    Map<String, Object> cancel(Long reserveId, String reason);
}
