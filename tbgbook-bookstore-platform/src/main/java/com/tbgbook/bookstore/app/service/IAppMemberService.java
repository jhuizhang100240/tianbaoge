package com.tbgbook.bookstore.app.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.tbgbook.bookstore.app.domain.request.AppCancelMemberRequest;
import com.tbgbook.bookstore.app.domain.request.AppCouponClaimRequest;
import com.tbgbook.bookstore.app.domain.request.AppSubscribeAuthRequest;
import com.tbgbook.bookstore.app.domain.request.AppRechargeRequest;
import com.tbgbook.bookstore.app.domain.request.AppProfileUpdateRequest;

/**
 * 会员端会员中心 Service（B-21~B-28）。
 */
public interface IAppMemberService
{
    Map<String, Object> center();

    Map<String, Object> levels();

    Map<String, Object> pointLogs(Integer changeType, LocalDate startDate, LocalDate endDate,
            int pageNum, int pageSize);

    Map<String, Object> expiringPoints();

    Map<String, Object> balance();

    Map<String, Object> balanceLogs(Integer amountType, LocalDate startDate, LocalDate endDate,
            int pageNum, int pageSize);

    Map<String, Object> recharge(AppRechargeRequest request);

    Map<String, Object> couponList(Integer status, boolean usableOnly);

    Map<String, Object> couponCenter();

    Map<String, Object> claimCoupon(AppCouponClaimRequest request);

    Map<String, Object> records(String types, LocalDate startDate, LocalDate endDate,
            int pageNum, int pageSize);

    Map<String, Object> messages(Integer msgType, Integer readStatus, int pageNum, int pageSize);

    Map<String, Object> markMessagesRead(List<Long> messageIds);

    Map<String, Object> profile();

    Map<String, Object> updateProfile(AppProfileUpdateRequest request);

    Map<String, Object> reportSubscribeAuth(AppSubscribeAuthRequest request);

    Map<String, Object> cancel(AppCancelMemberRequest request);

    Map<String, Object> dataExport();

    Map<String, Object> subscribeConfig();
}
