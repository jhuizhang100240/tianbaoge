package com.tbgbook.bookstore.member.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.member.domain.PointAdjustRequest;
import com.tbgbook.bookstore.member.domain.PointGrantRequest;
import com.tbgbook.bookstore.member.service.IMemberPointService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 积分动作 Controller（页面 A-47）。
 *
 * <p>人工调整属**敏感操作**（强制记录操作日志、保留 ≥180 天，NFR-S10），
 * 故两个写动作都带 {@code @Log}。必填与取值校验放 Service 抛 422（契约错误码如此）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/member/point")
public class MemberPointController extends BaseController
{
    private final IMemberPointService memberPointService;

    /**
     * 人工调整积分（正增负减，原因必填）。
     */
    @PreAuthorize("@ss.hasPermi('member:point:adjust')")
    @Log(title = "会员积分", businessType = BusinessType.UPDATE, isSensitive = true, bizType = "point")
    @PostMapping("/adjust")
    public AjaxResult adjust(@RequestBody PointAdjustRequest request)
    {
        return success(memberPointService.adjustPoints(request));
    }

    /**
     * 积分赠送（写入独立批次，同样受有效期约束）。
     */
    @PreAuthorize("@ss.hasPermi('member:point:grant')")
    @Log(title = "会员积分", businessType = BusinessType.UPDATE, isSensitive = true, bizType = "point")
    @PostMapping("/grant")
    public AjaxResult grant(@RequestBody PointGrantRequest request)
    {
        return success(memberPointService.grantPoints(request));
    }

    /**
     * 即将到期积分查询（days 缺省取 biz.points.expire.notify.days）。
     */
    @PreAuthorize("@ss.hasPermi('member:point:list')")
    @GetMapping("/expiring")
    public AjaxResult expiring(@RequestParam(value = "days", required = false) Integer days,
            @RequestParam(value = "memberId", required = false) Long memberId)
    {
        return success(memberPointService.selectExpiring(days, memberId));
    }
}
