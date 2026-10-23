package com.tbgbook.bookstore.member.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.member.domain.PointBatch;
import com.tbgbook.bookstore.member.service.IMemberPointService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;

/**
 * 积分批次 Controller（只读，页面 A-47）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/member/point-batch")
public class PointBatchController extends BaseController
{
    private final IMemberPointService memberPointService;

    /**
     * 查询积分批次列表（可按会员 / 状态 / 来源 / 单号 / 过期时间区间筛选）。
     */
    @PreAuthorize("@ss.hasPermi('member:point:list')")
    @GetMapping("/list")
    public TableDataInfo list(PointBatch batch)
    {
        startPage();
        List<PointBatch> list = memberPointService.selectPointBatchList(batch);
        return getDataTable(list);
    }

    /**
     * 获取积分批次详情。
     */
    @PreAuthorize("@ss.hasPermi('member:point:list')")
    @GetMapping("/{batchId}")
    public AjaxResult getInfo(@PathVariable("batchId") Long batchId)
    {
        return success(memberPointService.selectPointBatchById(batchId));
    }
}
