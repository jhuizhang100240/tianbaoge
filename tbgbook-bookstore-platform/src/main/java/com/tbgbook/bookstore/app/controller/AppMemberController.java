package com.tbgbook.bookstore.app.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.app.domain.request.AppCancelMemberRequest;
import com.tbgbook.bookstore.app.domain.request.AppCouponClaimRequest;
import com.tbgbook.bookstore.app.domain.request.AppMessageReadRequest;
import com.tbgbook.bookstore.app.domain.request.AppProfileUpdateRequest;
import com.tbgbook.bookstore.app.domain.request.AppRechargeRequest;
import com.tbgbook.bookstore.app.domain.request.AppSubscribeAuthRequest;
import com.tbgbook.bookstore.app.service.IAppMemberService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;

import lombok.RequiredArgsConstructor;

/**
 * 会员端会员中心 Controller（B-21~B-28）。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/member")
public class AppMemberController extends BaseController
{
    private final IAppMemberService memberService;

    @GetMapping("/center")
    public AjaxResult center()
    {
        return success(memberService.center());
    }

    @GetMapping("/levels")
    public AjaxResult levels()
    {
        return success(memberService.levels());
    }

    @GetMapping("/point/logs")
    public AjaxResult pointLogs(
            @RequestParam(value = "changeType", required = false) Integer changeType,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize)
    {
        return success(memberService.pointLogs(changeType, startDate, endDate, pageNum, pageSize));
    }

    @GetMapping("/point/expiring")
    public AjaxResult expiringPoints()
    {
        return success(memberService.expiringPoints());
    }

    @GetMapping("/balance")
    public AjaxResult balance()
    {
        return success(memberService.balance());
    }

    @GetMapping("/balance/logs")
    public AjaxResult balanceLogs(
            @RequestParam(value = "amountType", required = false) Integer amountType,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize)
    {
        return success(memberService.balanceLogs(amountType, startDate, endDate, pageNum, pageSize));
    }

    @PostMapping("/balance/recharge")
    public AjaxResult recharge(@Validated @RequestBody AppRechargeRequest request)
    {
        return success(memberService.recharge(request));
    }

    @GetMapping("/coupon/list")
    public AjaxResult couponList(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "usableOnly", required = false, defaultValue = "false") boolean usableOnly)
    {
        return success(memberService.couponList(status, usableOnly));
    }

    @GetMapping("/coupon/center")
    public AjaxResult couponCenter()
    {
        return success(memberService.couponCenter());
    }

    @PostMapping("/coupon/claim")
    public AjaxResult claimCoupon(@Validated @RequestBody AppCouponClaimRequest request)
    {
        return success(memberService.claimCoupon(request));
    }

    @GetMapping("/records")
    public AjaxResult records(
            @RequestParam(value = "types", required = false) String types,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize)
    {
        return success(memberService.records(types, startDate, endDate, pageNum, pageSize));
    }

    @GetMapping("/message/list")
    public AjaxResult messages(
            @RequestParam(value = "msgType", required = false) Integer msgType,
            @RequestParam(value = "readStatus", required = false) Integer readStatus,
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize)
    {
        return success(memberService.messages(msgType, readStatus, pageNum, pageSize));
    }

    @PostMapping("/message/read")
    public AjaxResult markMessagesRead(
            @RequestBody(required = false) AppMessageReadRequest request)
    {
        return success(memberService.markMessagesRead(request == null ? null : request.getMessageIds()));
    }

    @GetMapping("/profile")
    public AjaxResult profile()
    {
        return success(memberService.profile());
    }

    @PutMapping("/profile")
    public AjaxResult updateProfile(@Validated @RequestBody AppProfileUpdateRequest request)
    {
        return success(memberService.updateProfile(request));
    }

    @PostMapping("/subscribe-auth")
    public AjaxResult reportSubscribeAuth(@Validated @RequestBody AppSubscribeAuthRequest request)
    {
        return success(memberService.reportSubscribeAuth(request));
    }

    @PostMapping("/cancel")
    public AjaxResult cancel(@Validated @RequestBody AppCancelMemberRequest request)
    {
        return success(memberService.cancel(request));
    }

    @PostMapping("/data-export")
    public AjaxResult dataExport()
    {
        return success(memberService.dataExport());
    }

    @GetMapping("/subscribe-config")
    public AjaxResult subscribeConfig()
    {
        return success(memberService.subscribeConfig());
    }
}
