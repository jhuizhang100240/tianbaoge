package com.tbgbook.bookstore.sale.controller;

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

import com.tbgbook.bookstore.sale.domain.DailySettlement;
import com.tbgbook.bookstore.sale.domain.SettlementGenerateRequest;
import com.tbgbook.bookstore.sale.domain.SettlementLockRequest;
import com.tbgbook.bookstore.sale.service.IDailySettlementService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 日结对账 Controller（A-33，FR-4.3.12）。
 *
 * <p><b>各金额列一律由订单聚合生成，不接受传入</b>（契约明写）：唯一的维护点是
 * generate，重复生成覆盖未锁定的单。
 *
 * <p><b>锁定后该批次订单不可再改</b>（调整必须走红冲，§4.3 规则 6），锁定不可逆。
 * 差异不为 0 时差异说明必填 —— 差异的口径见 IDailySettlementService 的类注释。
 *
 * <p>⚠️ 本资源含成本类金额，**菜单仅授店长**（sales:settlement:list 的备注如此）。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/settlement")
public class DailySettlementController extends BaseController
{
    private final IDailySettlementService dailySettlementService;

    @PreAuthorize("@ss.hasPermi('sales:settlement:list')")
    @GetMapping("/list")
    public TableDataInfo list(DailySettlement settlement)
    {
        startPage();
        List<DailySettlement> list = dailySettlementService.selectDailySettlementList(settlement);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('sales:settlement:list')")
    @GetMapping("/{settleId}")
    public AjaxResult getInfo(@PathVariable("settleId") Long settleId)
    {
        return success(dailySettlementService.selectDailySettlementById(settleId));
    }

    /** 新增日结单（**只建空单**；金额要由 generate 聚合，不接受传入）。 */
    @PreAuthorize("@ss.hasPermi('sales:settlement:add')")
    @Log(title = "日结对账", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DailySettlement settlement)
    {
        return toAjax(dailySettlementService.addDailySettlement(settlement));
    }

    /** 修改日结单（**仅 1待对账**；只接受备注与差异说明）。 */
    @PreAuthorize("@ss.hasPermi('sales:settlement:edit')")
    @Log(title = "日结对账", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DailySettlement settlement)
    {
        return toAjax(dailySettlementService.updateDailySettlement(settlement));
    }

    /** 删除日结单（**仅未锁定**；删除会摘掉订单上的日结单号）。 */
    @PreAuthorize("@ss.hasPermi('sales:settlement:remove')")
    @Log(title = "日结对账", businessType = BusinessType.DELETE)
    @DeleteMapping("/{settleIds}")
    public AjaxResult remove(@PathVariable Long[] settleIds)
    {
        return toAjax(dailySettlementService.removeDailySettlements(settleIds));
    }

    /**
     * 生成日结对账（**按门店 + 日期 + 班次汇总**；重复生成覆盖未锁定的单）。
     *
     * <p>给了 shiftId 才可能算出**现金差异**（需要交班实点数）。
     */
    @PreAuthorize("@ss.hasPermi('sales:settlement:generate')")
    @Log(title = "生成日结对账", businessType = BusinessType.UPDATE)
    @PostMapping("/generate")
    public AjaxResult generate(@Valid @RequestBody SettlementGenerateRequest request)
    {
        return success(dailySettlementService.generate(request));
    }

    /** 锁定日结（**该批次订单不可再改**；锁定不可逆；差异不为 0 时说明必填）。 */
    @PreAuthorize("@ss.hasPermi('sales:settlement:lock')")
    @Log(title = "锁定日结", businessType = BusinessType.UPDATE)
    @PostMapping("/{settleId}/lock")
    public AjaxResult lock(@PathVariable("settleId") Long settleId,
            @Valid @RequestBody(required = false) SettlementLockRequest request)
    {
        String reason = (request == null) ? null : request.getDiffReason();
        return success(dailySettlementService.lock(settleId, reason));
    }
}
