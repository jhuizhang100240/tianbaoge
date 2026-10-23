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

import com.tbgbook.bookstore.stock.domain.LossApplyItem;
import com.tbgbook.bookstore.stock.service.ILossApplyItemService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 报损申请明细 Controller（A-18 报损申请页的明细表格）。
 *
 * <p>明细要能独立增删改，是因为报损单在前端是「头 + 可编辑明细表格」——
 * 每加一行都回传整张单太重，且并发编辑下容易互相覆盖。
 *
 * <p><b>全部写操作都要求所属单据处于待审批状态</b>，由 Service 统一把关
 * （只靠单据侧的校验挡不住「直接对明细发 PUT」这条路径）。
 *
 * <p>权限串全部复用 {@code inventory:loss:audit}（契约如此）——
 * 注意明细侧**不涉及** {@code inventory:loss:approve}：审批是对整张单的动作，
 * 不允许通过「改明细」来间接改变已提交的申请内容。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory/loss/item")
public class LossApplyItemController extends BaseController
{
    private final ILossApplyItemService lossApplyItemService;

    /**
     * 查询报损申请明细列表。
     */
    @PreAuthorize("@ss.hasPermi('inventory:loss:audit')")
    @GetMapping("/list")
    public TableDataInfo list(LossApplyItem item)
    {
        startPage();
        List<LossApplyItem> list = lossApplyItemService.selectLossApplyItemList(item);
        return getDataTable(list);
    }

    /**
     * 获取报损申请明细详情。
     */
    @PreAuthorize("@ss.hasPermi('inventory:loss:audit')")
    @GetMapping("/{itemId}")
    public AjaxResult getInfo(@PathVariable("itemId") Long itemId)
    {
        return success(lossApplyItemService.selectLossApplyItemById(itemId));
    }

    /**
     * 新增报损申请明细。
     *
     * <p>{@code isbn} / {@code title} / {@code unitCost} / {@code amount} / {@code stockQty}
     * 全部由服务端写（契约的 {@code skip}），客户端传了会被覆盖 ——
     * 其中 {@code unitCost} 由它决定「超 500 元需二次确认」的判据，不能由客户端自填。
     */
    @PreAuthorize("@ss.hasPermi('inventory:loss:audit')")
    @Log(title = "报损申请明细", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody LossApplyItem item)
    {
        return toAjax(lossApplyItemService.addLossApplyItem(item));
    }

    /**
     * 修改报损申请明细（主键在请求体，不在路径 —— RuoYi 式非严格 REST）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:loss:audit')")
    @Log(title = "报损申请明细", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody LossApplyItem item)
    {
        return toAjax(lossApplyItemService.updateLossApplyItem(item));
    }

    /**
     * 删除报损申请明细（支持批量，主键逗号分隔；**物理删除**，本表无 del_flag）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:loss:audit')")
    @Log(title = "报损申请明细", businessType = BusinessType.DELETE)
    @DeleteMapping("/{itemIds}")
    public AjaxResult remove(@PathVariable Long[] itemIds)
    {
        return toAjax(lossApplyItemService.removeLossApplyItems(itemIds));
    }
}
