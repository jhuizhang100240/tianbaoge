package com.tbgbook.bookstore.member.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.member.domain.MemberGrowthLog;
import com.tbgbook.bookstore.member.service.IMemberGrowthService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;

/**
 * 成长值流水 Controller（只读）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/member/growth-log")
public class GrowthLogController extends BaseController
{
    private final IMemberGrowthService memberGrowthService;

    /**
     * 查询成长值流水列表（可按会员 / 变更类型 / 单号筛选）。
     */
    @PreAuthorize("@ss.hasPermi('member:member:list')")
    @GetMapping("/list")
    public TableDataInfo list(MemberGrowthLog log)
    {
        startPage();
        List<MemberGrowthLog> list = memberGrowthService.selectMemberGrowthLogList(log);
        return getDataTable(list);
    }

    /**
     * 获取成长值流水详情。
     */
    @PreAuthorize("@ss.hasPermi('member:member:list')")
    @GetMapping("/{logId}")
    public AjaxResult getInfo(@PathVariable("logId") Long logId)
    {
        return success(memberGrowthService.selectMemberGrowthLogById(logId));
    }
}
