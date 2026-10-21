package com.tbgbook.bookstore.app.service;

import java.util.Map;

import com.tbgbook.bookstore.app.domain.request.RecycleApplyRequest;

/**
 * 会员端二手回收 Service（B-13/B-14）。
 */
public interface IAppRecycleService
{
    Map<String, Object> apply(RecycleApplyRequest request);

    Map<String, Object> list(Integer status);

    Map<String, Object> detail(Long recycleId);

    Map<String, Object> cancel(Long recycleId, String reason);
}
