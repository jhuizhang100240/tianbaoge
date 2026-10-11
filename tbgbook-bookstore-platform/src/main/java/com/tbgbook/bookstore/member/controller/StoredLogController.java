package com.tbgbook.bookstore.member.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.member.domain.StoredValueLog;
import com.tbgbook.bookstore.member.service.IMemberBalanceService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;

/**
 * 储值流水 Controller（只读，页面 A-48）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/member/stored-log")
public class StoredLogController extends BaseController
{
    private final IMemberBalanceService memberBalanceService;

    /**
     * 查询储值流水列表（可按会员 / 金额类型 / 单号筛选）。
     */
    @PreAuthorize("@ss.hasPermi('member:balance:list')")
    @GetMapping("/list")
    public TableDataInfo list(StoredValueLog log)
    {
        startPage();
        List<StoredValueLog> list = memberBalanceService.selectStoredValueLogList(log);
        return getDataTable(list);
    }

    /**
     * 获取储值流水详情。
     */
    @PreAuthorize("@ss.hasPermi('member:balance:list')")
    @GetMapping("/{logId}")
    public AjaxResult getInfo(@PathVariable("logId") Long logId)
    {
        return success(memberBalanceService.selectStoredValueLogById(logId));
    }
}
