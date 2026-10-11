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

import com.tbgbook.bookstore.member.domain.MemberTag;
import com.tbgbook.bookstore.member.service.IMemberTagService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 会员标签 Controller（页面 A-50，FR-4.4.7）。
 *
 * <p>规则标签的表达式口径（白名单指标）见 {@code member.support.TagRule}；
 * 手工打标在 {@code /member/tag/rel/mark}。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/member/tag")
public class MemberTagController extends BaseController
{
    private final IMemberTagService memberTagService;

    /**
     * 查询会员标签列表（可按名称 / 类型 / 是否规则 / 状态筛选）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:list')")
    @GetMapping("/list")
    public TableDataInfo list(MemberTag tag)
    {
        startPage();
        List<MemberTag> list = memberTagService.selectMemberTagList(tag);
        return getDataTable(list);
    }

    /**
     * 获取会员标签详情。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:list')")
    @GetMapping("/{tagId}")
    public AjaxResult getInfo(@PathVariable("tagId") Long tagId)
    {
        return success(memberTagService.selectMemberTagById(tagId));
    }

    /**
     * 新增会员标签（规则标签即时校验表达式语法）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:add')")
    @Log(title = "会员标签", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody MemberTag tag)
    {
        return toAjax(memberTagService.addMemberTag(tag));
    }

    /**
     * 修改会员标签（isRule 不允许修改）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:edit')")
    @Log(title = "会员标签", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody MemberTag tag)
    {
        return toAjax(memberTagService.updateMemberTag(tag));
    }

    /**
     * 删除会员标签（被关系引用的标签禁止删除）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:remove')")
    @Log(title = "会员标签", businessType = BusinessType.DELETE)
    @DeleteMapping("/{tagIds}")
    public AjaxResult remove(@PathVariable Long[] tagIds)
    {
        return toAjax(memberTagService.removeMemberTags(tagIds));
    }

    /**
     * 立即重算规则标签（整体替换关系并更新覆盖数）。
     */
    @PreAuthorize("@ss.hasPermi('member:tag:recalc')")
    @Log(title = "会员标签", businessType = BusinessType.UPDATE)
    @PostMapping("/{tagId}/recalc")
    public AjaxResult recalc(@PathVariable("tagId") Long tagId)
    {
        return success(memberTagService.recalc(tagId));
    }
}
