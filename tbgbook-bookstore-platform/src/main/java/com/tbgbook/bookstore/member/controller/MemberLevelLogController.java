package com.tbgbook.bookstore.member.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.member.domain.MemberLevelLog;
import com.tbgbook.bookstore.member.service.IMemberLevelLogService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;

/**
 * 会员等级变更记录 Controller（只读，FR-4.4.3）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/member/level-log")
public class MemberLevelLogController extends BaseController
{
    private final IMemberLevelLogService memberLevelLogService;

    /**
     * 查询等级变更记录列表（可按会员 / 变更类型 / 变更前后等级筛选）。
     */
    @PreAuthorize("@ss.hasPermi('member:level:list')")
    @GetMapping("/list")
    public TableDataInfo list(MemberLevelLog log)
    {
        startPage();
        List<MemberLevelLog> list = memberLevelLogService.selectMemberLevelLogList(log);
        return getDataTable(list);
    }

    /**
     * 获取等级变更记录详情。
     */
    @PreAuthorize("@ss.hasPermi('member:level:list')")
    @GetMapping("/{logId}")
    public AjaxResult getInfo(@PathVariable("logId") Long logId)
    {
        return success(memberLevelLogService.selectMemberLevelLogById(logId));
    }
}
