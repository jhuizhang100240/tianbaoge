package com.tbgbook.bookstore.member.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.member.domain.PointLog;
import com.tbgbook.bookstore.member.service.IMemberPointService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;

/**
 * 积分流水 Controller（只读，页面 A-47）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/member/point-log")
public class PointLogController extends BaseController
{
    private final IMemberPointService memberPointService;

    /**
     * 查询积分流水列表（可按会员 / 变动类型 / 批次 / 单号筛选）。
     */
    @PreAuthorize("@ss.hasPermi('member:point:list')")
    @GetMapping("/list")
    public TableDataInfo list(PointLog log)
    {
        startPage();
        List<PointLog> list = memberPointService.selectPointLogList(log);
        return getDataTable(list);
    }

    /**
     * 获取积分流水详情。
     */
    @PreAuthorize("@ss.hasPermi('member:point:list')")
    @GetMapping("/{logId}")
    public AjaxResult getInfo(@PathVariable("logId") Long logId)
    {
        return success(memberPointService.selectPointLogById(logId));
    }
}
