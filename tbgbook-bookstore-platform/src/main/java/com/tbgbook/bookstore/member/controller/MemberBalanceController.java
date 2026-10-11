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

import com.tbgbook.bookstore.member.domain.MemberBalance;
import com.tbgbook.bookstore.member.domain.RechargeRequest;
import com.tbgbook.bookstore.member.service.IMemberBalanceService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 会员储值账户 Controller（页面 A-48）。
 *
 * <p>⚠️ `POST /member/balance/refund`（储值退款动作）因契约与需求的资金流向冲突**暂缓实现**，
 * 待裁决（见 `plan/待确认项.md`）—— 未裁决前不触碰资金语义。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/member/balance")
public class MemberBalanceController extends BaseController
{
    private final IMemberBalanceService memberBalanceService;

    /**
     * 查询储值账户列表（可按会员与创建时间区间筛选）。
     */
    @PreAuthorize("@ss.hasPermi('member:balance:list')")
    @GetMapping("/list")
    public TableDataInfo list(MemberBalance balance)
    {
        startPage();
        List<MemberBalance> list = memberBalanceService.selectMemberBalanceList(balance);
        return getDataTable(list);
    }

    /**
     * 获取储值账户详情。
     */
    @PreAuthorize("@ss.hasPermi('member:balance:list')")
    @GetMapping("/{balanceId}")
    public AjaxResult getInfo(@PathVariable("balanceId") Long balanceId)
    {
        return success(memberBalanceService.selectMemberBalanceById(balanceId));
    }

    /**
     * 补建储值账户（一会员一账户，重复 409；正常建档已自动初始化）。
     */
    @PreAuthorize("@ss.hasPermi('member:balance:add')")
    @Log(title = "会员储值账户", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody MemberBalance balance)
    {
        return toAjax(memberBalanceService.addMemberBalance(balance));
    }

    /**
     * 修改储值账户 —— 恒 422（余额由充值 / 消费 / 退款接口维护）。
     */
    @PreAuthorize("@ss.hasPermi('member:balance:edit')")
    @Log(title = "会员储值账户", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody MemberBalance balance)
    {
        return toAjax(memberBalanceService.updateMemberBalance(balance));
    }

    /**
     * 删除储值账户（仅零余额账户可删）。
     */
    @PreAuthorize("@ss.hasPermi('member:balance:remove')")
    @Log(title = "会员储值账户", businessType = BusinessType.DELETE)
    @DeleteMapping("/{balanceIds}")
    public AjaxResult remove(@PathVariable Long[] balanceIds)
    {
        return toAjax(memberBalanceService.removeMemberBalances(balanceIds));
    }

    /**
     * 储值充值（按活动赠送拆分本金与赠送金，分别写流水）。
     */
    @PreAuthorize("@ss.hasPermi('member:balance:recharge')")
    @Log(title = "会员储值充值", businessType = BusinessType.UPDATE)
    @PostMapping("/recharge")
    public AjaxResult recharge(@RequestBody RechargeRequest request)
    {
        return success(memberBalanceService.recharge(request));
    }
}
