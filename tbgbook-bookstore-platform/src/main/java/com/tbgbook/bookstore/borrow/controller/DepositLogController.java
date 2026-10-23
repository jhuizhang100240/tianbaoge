package com.tbgbook.bookstore.borrow.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.borrow.domain.DepositLog;
import com.tbgbook.bookstore.borrow.domain.DepositPayRequest;
import com.tbgbook.bookstore.borrow.domain.DepositRefundRequest;
import com.tbgbook.bookstore.borrow.service.IDepositService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 借阅押金流水 Controller（只读 + 押金缴纳 / 退还动作）。
 *
 * <p>流水**只增不删**（无 PUT / DELETE 端点）；缴纳与退还走独立动作端点。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
public class DepositLogController extends BaseController
{
    private final IDepositService depositService;

    /**
     * 查询押金流水列表。
     */
    @PreAuthorize("@ss.hasPermi('borrow:penalty:list')")
    @GetMapping("/borrow/deposit-log/list")
    public TableDataInfo list(DepositLog log)
    {
        startPage();
        List<DepositLog> list = depositService.selectDepositLogList(log);
        return getDataTable(list);
    }

    /**
     * 获取押金流水详情。
     */
    @PreAuthorize("@ss.hasPermi('borrow:penalty:list')")
    @GetMapping("/borrow/deposit-log/{logId}")
    public AjaxResult getInfo(@PathVariable("logId") Long logId)
    {
        return success(depositService.selectDepositLogById(logId));
    }

    /**
     * 缴纳借阅押金。
     */
    @PreAuthorize("@ss.hasPermi('borrow:penalty:pay')")
    @Log(title = "借阅押金", businessType = BusinessType.INSERT)
    @PostMapping("/borrow/deposit/pay")
    public AjaxResult pay(@RequestBody DepositPayRequest request)
    {
        return success(depositService.pay(request));
    }

    /**
     * 退还借阅押金（无在借且无未结清罚金）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:penalty:refund')")
    @Log(title = "借阅押金", businessType = BusinessType.UPDATE)
    @PostMapping("/borrow/deposit/refund")
    public AjaxResult refund(@RequestBody DepositRefundRequest request)
    {
        return success(depositService.refund(request));
    }
}
