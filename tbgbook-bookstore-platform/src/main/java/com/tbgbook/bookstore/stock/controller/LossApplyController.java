package com.tbgbook.bookstore.stock.controller;

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

import com.tbgbook.bookstore.stock.domain.LossApply;
import com.tbgbook.bookstore.stock.domain.LossAuditRequest;
import com.tbgbook.bookstore.stock.service.ILossApplyService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 报损申请单 Controller（页面 A-18 报损出库申请 / A-19 报损审批）。
 *
 * <p><b>权限分两层，这是本单据最要紧的一点</b>：
 * <ul>
 *   <li>增删改查走 {@code inventory:loss:audit} —— 库管员与店长都有
 *       （库管员负责报损，需求 §2 角色表）</li>
 *   <li>审批（approve / reject）走 {@code inventory:loss:approve} ——
 *       <b>仅店长</b>（角色权限要点 1），且本接口**不接受任何来自前端的角色声明**，
 *       只看权限串</li>
 * </ul>
 * 从 URL 上看不出这个差别（同一个 {@code /inventory/loss} 前缀），
 * 故在方法上逐个写清楚。
 *
 * <p>⚠️ <b>字面量路径与 {@code /{applyId}} 同段位</b>：明细子资源挂在
 * {@code /inventory/loss/item}，与 {@code GET /inventory/loss/{applyId}} 竞争同一段位。
 * Spring 的字面量模式优先于路径变量，故 {@code /item/list} 不会被解析成
 * {@code applyId="item"}（那还会因 {@code Long} 转换失败报 400）。
 * 与 {@code StockInController} 是同一个坑 —— 处理方式一样：
 * **让优先级只有单一出处**，故两个控制器都放在 {@code stock/controller} 下并列声明。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory/loss")
public class LossApplyController extends BaseController
{
    private final ILossApplyService lossApplyService;

    /**
     * 查询报损申请单列表。
     */
    @PreAuthorize("@ss.hasPermi('inventory:loss:audit')")
    @GetMapping("/list")
    public TableDataInfo list(LossApply apply)
    {
        startPage();
        List<LossApply> list = lossApplyService.selectLossApplyList(apply);
        return getDataTable(list);
    }

    /**
     * 获取报损申请单详情（**含明细**）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:loss:audit')")
    @GetMapping("/{applyId}")
    public AjaxResult getInfo(@PathVariable("applyId") Long applyId)
    {
        return success(lossApplyService.selectLossApplyById(applyId));
    }

    /**
     * 新增报损申请单（**待审批，不动库存**）。
     *
     * <p>服务端算 {@code totalAmount}（成本口径）并按 {@code biz.loss.confirm.amount}
     * 置 {@code needConfirm}。请求体是「单据头 + {@code items} 明细数组」，同事务落库。
     */
    @PreAuthorize("@ss.hasPermi('inventory:loss:audit')")
    @Log(title = "报损申请单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody LossApply apply)
    {
        return toAjax(lossApplyService.addLossApply(apply));
    }

    /**
     * 修改报损申请单（**仅待审批**；明细整体替换并重取库存快照）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:loss:audit')")
    @Log(title = "报损申请单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody LossApply apply)
    {
        return toAjax(lossApplyService.updateLossApply(apply));
    }

    /**
     * 删除报损申请单（**仅非已通过状态**，主从级联）。
     *
     * <p>已通过的单据是库存的实际凭证（流水按单据号引用它），只能留着。
     */
    @PreAuthorize("@ss.hasPermi('inventory:loss:audit')")
    @Log(title = "报损申请单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{applyIds}")
    public AjaxResult remove(@PathVariable Long[] applyIds)
    {
        return toAjax(lossApplyService.removeLossApplies(applyIds));
    }

    /**
     * 审批通过报损单 —— <b>唯一的库存扣减动作</b>。
     *
     * <p>⚠️ 权限 {@code inventory:loss:approve} **仅店长持有**（角色权限要点 1）。
     * 重复审批返回 409，不会把库存扣两遍。
     */
    @PreAuthorize("@ss.hasPermi('inventory:loss:approve')")
    @Log(title = "报损审批", businessType = BusinessType.UPDATE)
    @PostMapping("/{applyId}/approve")
    public AjaxResult approve(@PathVariable("applyId") Long applyId,
            @Valid @RequestBody(required = false) LossAuditRequest request)
    {
        return success(lossApplyService.approve(applyId,
                request == null ? null : request.getAuditRemark()));
    }

    /**
     * 驳回报损单（**不扣库存**）。
     *
     * <p>{@code auditRemark} 在本端点**必填**（契约如此），由 service 判定 ——
     * 与 approve 共用同一个 DTO，字段注解分不清这次是哪个端点。
     */
    @PreAuthorize("@ss.hasPermi('inventory:loss:approve')")
    @Log(title = "报损审批", businessType = BusinessType.UPDATE)
    @PostMapping("/{applyId}/reject")
    public AjaxResult reject(@PathVariable("applyId") Long applyId,
            @Valid @RequestBody LossAuditRequest request)
    {
        return toAjax(lossApplyService.reject(applyId, request.getAuditRemark()));
    }

    /**
     * 撤销报损申请（申请人撤回自己提交的**待审批**单据）。
     *
     * <p>⚠️ 权限是 {@code inventory:loss:revoke}（授给库管员与店长），
     * 而不是 {@code :approve} —— 撤销是申请人的动作，不是审批人的。
     * 非本人提交的单据由 service 判 403。
     */
    @PreAuthorize("@ss.hasPermi('inventory:loss:revoke')")
    @Log(title = "报损申请单", businessType = BusinessType.UPDATE)
    @PostMapping("/{applyId}/revoke")
    public AjaxResult revoke(@PathVariable("applyId") Long applyId)
    {
        return toAjax(lossApplyService.revoke(applyId));
    }
}
