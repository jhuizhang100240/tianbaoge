package com.tbgbook.bookstore.app.service;

import java.time.LocalDate;
import java.util.Map;

/**
 * 会员端借阅 Service（B-16~B-18）。
 */
public interface IAppBorrowService
{
    Map<String, Object> center();

    Map<String, Object> list(Integer status, LocalDate startDate, LocalDate endDate, int pageNum, int pageSize);

    Map<String, Object> detail(Long borrowId);

    Map<String, Object> renew(Long borrowId);

    Map<String, Object> rule();

    Map<String, Object> reserve(Long bookId);

    Map<String, Object> reserveList(Integer status);

    Map<String, Object> cancelReserve(Long reserveId, String reason);

    Map<String, Object> payPenalty(Long penaltyId, Integer payMethod, java.math.BigDecimal payAmount);
}
