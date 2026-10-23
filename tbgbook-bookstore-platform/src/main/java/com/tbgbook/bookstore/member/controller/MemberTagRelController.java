package com.tbgbook.bookstore.member.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.member.domain.MemberTagRel;
import com.tbgbook.bookstore.member.domain.TagRelRequest;
import com.tbgbook.bookstore.member.service.IMemberTagService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 会员标签关系 Controller（页面 A-50）。
 *
 * <p>手工打标 / 解标走 {@code /mark} 与 {@code /remove}；规则标签的关联不接受手工增删（422）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/member/tag/rel")
public class MemberTagRelController extends BaseController
{
    private final IMemberTagService memberTagService;

    /**
     * 查询标签关系列表（可按会员 / 标签 / 来源筛选）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:list')")
    @GetMapping("/list")
    public TableDataInfo list(MemberTagRel rel)
    {
        startPage();
        List<MemberTagRel> list = memberTagService.selectMemberTagRelList(rel);
        return getDataTable(list);
    }

    /**
     * 获取标签关系详情。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:list')")
    @GetMapping("/{relId}")
    public AjaxResult getInfo(@PathVariable("relId") Long relId)
    {
        return success(memberTagService.selectMemberTagRelById(relId));
    }

    /**
     * 新增标签关系（等价单条手工打标；规则标签 422、重复 409）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:add')")
    @Log(title = "会员标签关系", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody MemberTagRel rel)
    {
        return toAjax(memberTagService.addMemberTagRel(rel));
    }

    /**
     * 修改标签关系 —— 恒 422（不支持修改）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:edit')")
    @Log(title = "会员标签关系", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody MemberTagRel rel)
    {
        return toAjax(memberTagService.updateMemberTagRel(rel));
    }

    /**
     * 批量删除标签关系（规则标签的关联不允许手工删除）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:remove')")
    @Log(title = "会员标签关系", businessType = BusinessType.DELETE)
    @DeleteMapping("/{relIds}")
    public AjaxResult remove(@PathVariable Long[] relIds)
    {
        return toAjax(memberTagService.removeMemberTagRels(relIds));
    }

    /**
     * 手工打标（新增缺失关系、已存在的跳过）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:mark')")
    @Log(title = "会员标签", businessType = BusinessType.UPDATE)
    @PostMapping("/mark")
    public AjaxResult mark(@RequestBody TagRelRequest request)
    {
        return success(memberTagService.mark(request));
    }

    /**
     * 取消手工标签。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:remove')")
    @Log(title = "会员标签", businessType = BusinessType.UPDATE)
    @PostMapping("/remove")
    public AjaxResult removeMark(@RequestBody TagRelRequest request)
    {
        return success(memberTagService.remove(request));
    }
}
