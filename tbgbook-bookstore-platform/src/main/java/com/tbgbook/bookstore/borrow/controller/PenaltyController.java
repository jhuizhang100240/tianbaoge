package com.tbgbook.bookstore.borrow.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.borrow.domain.Penalty;
import com.tbgbook.bookstore.borrow.domain.PenaltyPayRequest;
import com.tbgbook.bookstore.borrow.domain.PenaltyReduceRequest;
import com.tbgbook.bookstore.borrow.domain.PenaltyRequest;
import com.tbgbook.bookstore.borrow.service.IPenaltyService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 罚金与赔偿单 Controller（页面 A-56，FR-4.5.5 / FR-4.5.7）。
 *
 * <p>资源只读（列表 / 详情）；生成走 {@code POST /borrow/penalty}，
 * 缴费走 {@code /{id}/pay}，减免走 {@code /{id}/reduce}（需店长）。
 * 罚金属财务凭证，不提供删除。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/borrow/penalty")
public class PenaltyController extends BaseController
{
    private final IPenaltyService penaltyService;

    /**
     * 查询罚金 / 赔偿单列表。
     */
    @PreAuthorize("@ss.hasPermi('borrow:penalty:list')")
    @GetMapping("/list")
    public TableDataInfo list(Penalty penalty)
    {
        startPage();
        List<Penalty> list = penaltyService.selectPenaltyList(penalty);
        return getDataTable(list);
    }

    /**
     * 获取罚金 / 赔偿单详情。
     */
    @PreAuthorize("@ss.hasPermi('borrow:penalty:list')")
    @GetMapping("/{penaltyId}")
    public AjaxResult getInfo(@PathVariable("penaltyId") Long penaltyId)
    {
        return success(penaltyService.selectPenaltyById(penaltyId));
    }

    /**
     * 生成罚金 / 赔偿单（金额由服务端按规则计算；优先从押金扣除）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:penalty:list')")
    @Log(title = "罚金赔偿", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult generate(@RequestBody PenaltyRequest request)
    {
        return success(penaltyService.generate(request));
    }

    /**
     * 缴纳罚金 / 赔偿款。
     */
    @PreAuthorize("@ss.hasPermi('borrow:penalty:pay')")
    @Log(title = "罚金赔偿", businessType = BusinessType.UPDATE)
    @PostMapping("/{penaltyId}/pay")
    public AjaxResult pay(@PathVariable("penaltyId") Long penaltyId,
            @RequestBody PenaltyPayRequest request)
    {
        return success(penaltyService.pay(penaltyId, request));
    }

    /**
     * 罚金减免（需店长审批）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:penalty:reduce')")
    @Log(title = "罚金减免", businessType = BusinessType.UPDATE)
    @PostMapping("/{penaltyId}/reduce")
    public AjaxResult reduce(@PathVariable("penaltyId") Long penaltyId,
            @RequestBody PenaltyReduceRequest request)
    {
        return success(penaltyService.reduce(penaltyId, request));
    }
}
