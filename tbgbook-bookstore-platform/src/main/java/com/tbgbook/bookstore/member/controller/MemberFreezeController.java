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

import com.tbgbook.bookstore.member.domain.MemberFreeze;
import com.tbgbook.bookstore.member.service.IMemberFreezeService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 会员冻结与黑名单 Controller（页面 A-52，FR-4.4.11）。
 *
 * <p>实际的冻结/解冻动作在 `/member/member/{memberId}/freeze|unfreeze`（见 MemberController）；
 * 本控制器是记录的查询与维护面（删除恒 422，历史留痕）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/member/freeze")
public class MemberFreezeController extends BaseController
{
    private final IMemberFreezeService memberFreezeService;

    /**
     * 查询冻结记录列表（可按会员 / 类型 / 状态 / 操作人筛选）。
     */
    @PreAuthorize("@ss.hasPermi('member:freeze:list')")
    @GetMapping("/list")
    public TableDataInfo list(MemberFreeze freeze)
    {
        startPage();
        List<MemberFreeze> list = memberFreezeService.selectMemberFreezeList(freeze);
        return getDataTable(list);
    }

    /**
     * 获取冻结记录详情。
     */
    @PreAuthorize("@ss.hasPermi('member:freeze:list')")
    @GetMapping("/{freezeId}")
    public AjaxResult getInfo(@PathVariable("freezeId") Long freezeId)
    {
        return success(memberFreezeService.selectMemberFreezeById(freezeId));
    }

    /**
     * 新增冻结记录（等价冻结动作，同步会员状态）。
     */
    @PreAuthorize("@ss.hasPermi('member:freeze:add')")
    @Log(title = "会员冻结", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody MemberFreeze freeze)
    {
        return toAjax(memberFreezeService.addMemberFreeze(freeze));
    }

    /**
     * 修改冻结记录（仅生效中可改）。
     */
    @PreAuthorize("@ss.hasPermi('member:freeze:edit')")
    @Log(title = "会员冻结", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody MemberFreeze freeze)
    {
        return toAjax(memberFreezeService.updateMemberFreeze(freeze));
    }

    /**
     * 删除冻结记录 —— 恒 422（历史留痕不可删除）。
     */
    @PreAuthorize("@ss.hasPermi('member:freeze:remove')")
    @Log(title = "会员冻结", businessType = BusinessType.DELETE)
    @DeleteMapping("/{freezeIds}")
    public AjaxResult remove(@PathVariable Long[] freezeIds)
    {
        return toAjax(memberFreezeService.removeMemberFreezes(freezeIds));
    }
}
