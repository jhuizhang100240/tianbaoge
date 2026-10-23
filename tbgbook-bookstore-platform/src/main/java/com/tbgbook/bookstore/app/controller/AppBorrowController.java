package com.tbgbook.bookstore.app.controller;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.app.service.IAppBorrowService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;

import lombok.RequiredArgsConstructor;

/**
 * 会员端借阅 Controller（B-16~B-18）。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/borrow")
public class AppBorrowController extends BaseController
{
    private final IAppBorrowService borrowService;

    @GetMapping("/center")
    public AjaxResult center()
    {
        return success(borrowService.center());
    }

    @GetMapping("/list")
    public AjaxResult list(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize)
    {
        return success(borrowService.list(status, startDate, endDate, pageNum, pageSize));
    }

    @GetMapping("/{borrowId}")
    public AjaxResult detail(@PathVariable("borrowId") Long borrowId)
    {
        return success(borrowService.detail(borrowId));
    }

    @PostMapping("/{borrowId}/renew")
    public AjaxResult renew(@PathVariable("borrowId") Long borrowId)
    {
        return success(borrowService.renew(borrowId));
    }

    @GetMapping("/rule")
    public AjaxResult rule()
    {
        return success(borrowService.rule());
    }

    @PostMapping("/reserve")
    public AjaxResult reserve(@RequestBody java.util.Map<String, Object> body)
    {
        Object value = body == null ? null : body.get("bookId");
        if (value == null)
        {
            return error("图书ID不能为空");
        }
        return success(borrowService.reserve(Long.valueOf(String.valueOf(value))));
    }

    @GetMapping("/reserve/list")
    public AjaxResult reserveList(@RequestParam(value = "status", required = false) Integer status)
    {
        return success(borrowService.reserveList(status));
    }

    @PostMapping("/reserve/{reserveId}/cancel")
    public AjaxResult cancelReserve(@PathVariable("reserveId") Long reserveId,
            @RequestBody(required = false) java.util.Map<String, Object> body)
    {
        String reason = body == null ? "" : String.valueOf(body.getOrDefault("reason", ""));
        return success(borrowService.cancelReserve(reserveId, reason));
    }

    @PostMapping("/penalty/{penaltyId}/pay")
    public AjaxResult payPenalty(@PathVariable("penaltyId") Long penaltyId,
            @RequestBody java.util.Map<String, Object> body)
    {
        Integer method = body.get("payMethod") == null ? 2
                : Integer.valueOf(String.valueOf(body.get("payMethod")));
        BigDecimal amount = body.get("payAmount") == null ? null
                : new BigDecimal(String.valueOf(body.get("payAmount")));
        return success(borrowService.payPenalty(penaltyId, method, amount));
    }
}
