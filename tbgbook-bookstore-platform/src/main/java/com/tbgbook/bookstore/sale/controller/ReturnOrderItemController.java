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

import com.tbgbook.bookstore.sale.domain.ReturnOrderItem;
import com.tbgbook.bookstore.sale.service.IReturnOrderItemService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 退货单明细 Controller（`/sales/return/item`）。
 *
 * <p><b>只有「1待审核」的退货单能增删改明细</b> —— 有原单退货提交即完成，其明细是
 * 库存与资金的凭证。而「待审核」只可能是无单退货，故本控制器的写操作实际只服务无单退货。
 *
 * <p>{@code refundAmount} **不接受客户端传值**（NFR-S9）：服务端按各行「定价 × 数量」
 * 在父单退款总额内比例分摊；书名 / ISBN / 单价一律按 {@code bookId} 从图书主档取。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/return/item")
public class ReturnOrderItemController extends BaseController
{
    private final IReturnOrderItemService returnOrderItemService;

    @PreAuthorize("@ss.hasPermi('sales:refund:list')")
    @GetMapping("/list")
    public TableDataInfo list(ReturnOrderItem item)
    {
        startPage();
        List<ReturnOrderItem> list = returnOrderItemService.selectReturnOrderItemList(item);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('sales:refund:list')")
    @GetMapping("/{itemId}")
    public AjaxResult getInfo(@PathVariable("itemId") Long itemId)
    {
        return success(returnOrderItemService.selectReturnOrderItemById(itemId));
    }

    /** 新增退货单明细（**仅父单待审核**；改完回算父单的数量与退款额分摊）。 */
    @PreAuthorize("@ss.hasPermi('sales:refund:add')")
    @Log(title = "退货单明细", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ReturnOrderItem item)
    {
        return toAjax(returnOrderItemService.addReturnOrderItem(item));
    }

    /** 修改退货单明细（**仅父单待审核**；只接受数量 / 图书状况 / 库存去向）。 */
    @PreAuthorize("@ss.hasPermi('sales:refund:edit')")
    @Log(title = "退货单明细", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ReturnOrderItem item)
    {
        return toAjax(returnOrderItemService.updateReturnOrderItem(item));
    }

    /** 删除退货单明细（**仅父单待审核**，一次只能删同一张退货单下的明细）。 */
    @PreAuthorize("@ss.hasPermi('sales:refund:remove')")
    @Log(title = "退货单明细", businessType = BusinessType.DELETE)
    @DeleteMapping("/{itemIds}")
    public AjaxResult remove(@PathVariable Long[] itemIds)
    {
        return toAjax(returnOrderItemService.removeReturnOrderItems(itemIds));
    }
}
