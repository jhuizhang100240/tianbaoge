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

import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.domain.CancelRequest;
import com.tbgbook.bookstore.member.domain.FreezeRequest;
import com.tbgbook.bookstore.member.domain.UnfreezeRequest;
import com.tbgbook.bookstore.member.service.IMemberFreezeService;
import com.tbgbook.bookstore.member.service.IMemberService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 会员 Controller（页面 A-44~A-49，FR-4.4.1 / FR-4.4.2）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/member/member")
public class MemberController extends BaseController
{
    private final IMemberService memberService;

    private final IMemberFreezeService memberFreezeService;

    /**
     * 查询会员列表（可按卡号 / 手机号 / 昵称 / 姓名 / 等级 / 认证状态 / 状态 / 来源与建档时间区间筛选；
     * 手机号脱敏返回）。
     */
    @PreAuthorize("@ss.hasPermi('member:member:list')")
    @GetMapping("/list")
    public TableDataInfo list(Member member)
    {
        startPage();
        List<Member> list = memberService.selectMemberList(member);
        return getDataTable(list);
    }

    /**
     * 获取会员详情（手机号脱敏返回）。
     */
    @PreAuthorize("@ss.hasPermi('member:member:list')")
    @GetMapping("/{memberId}")
    public AjaxResult getInfo(@PathVariable("memberId") Long memberId)
    {
        return success(memberService.selectMemberById(memberId));
    }

    /**
     * 新增会员（卡号 = M+手机号，服务端生成；分配默认等级并初始化储值账户）。
     */
    @PreAuthorize("@ss.hasPermi('member:member:add')")
    @Log(title = "会员", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody Member member)
    {
        return toAjax(memberService.addMember(member));
    }

    /**
     * 修改会员（汇总类字段与状态不接受写入；换手机号同步重算卡号）。
     */
    @PreAuthorize("@ss.hasPermi('member:member:edit')")
    @Log(title = "会员", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody Member member)
    {
        return toAjax(memberService.updateMember(member));
    }

    /**
     * 删除会员（**仅限无任何业务痕迹的误录会员**；有痕迹返回 422，引导走注销）。
     */
    @PreAuthorize("@ss.hasPermi('member:member:remove')")
    @Log(title = "会员", businessType = BusinessType.DELETE)
    @DeleteMapping("/{memberIds}")
    public AjaxResult remove(@PathVariable Long[] memberIds)
    {
        return toAjax(memberService.removeMembers(memberIds));
    }

    /**
     * 冻结会员 / 拉黑（同步会员状态为 2冻结 / 3黑名单）。
     */
    @PreAuthorize("@ss.hasPermi('member:freeze:add')")
    @Log(title = "会员冻结", businessType = BusinessType.UPDATE)
    @PostMapping("/{memberId}/freeze")
    public AjaxResult freeze(@PathVariable("memberId") Long memberId,
            @RequestBody FreezeRequest request)
    {
        return success(memberFreezeService.freeze(memberId, request));
    }

    /**
     * 解冻会员（恢复会员状态为 1正常）。
     */
    @PreAuthorize("@ss.hasPermi('member:freeze:unfreeze')")
    @Log(title = "会员冻结", businessType = BusinessType.UPDATE)
    @PostMapping("/{memberId}/unfreeze")
    public AjaxResult unfreeze(@PathVariable("memberId") Long memberId,
            @RequestBody UnfreezeRequest request)
    {
        return success(memberFreezeService.unfreeze(memberId, request));
    }

    /**
     * 注销会员（匿名化 + 保留业务流水；注销前校验无未结清业务）。
     */
    @PreAuthorize("@ss.hasPermi('member:member:cancel')")
    @Log(title = "会员注销", businessType = BusinessType.UPDATE)
    @PostMapping("/{memberId}/cancel")
    public AjaxResult cancel(@PathVariable("memberId") Long memberId,
            @RequestBody CancelRequest request)
    {
        return toAjax(memberService.cancelMember(memberId, request.getReason()));
    }
}
