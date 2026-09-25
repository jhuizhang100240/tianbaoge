package com.tbgbook.bookstore.sale.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.sale.domain.OrderItem;
import com.tbgbook.bookstore.sale.service.IOrderItemService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;

import lombok.RequiredArgsConstructor;

/**
 * 订单明细 Controller。本步骤只交付**只读**；写入属步骤④。
 *
 * <p>`costPrice` 是成本类字段，无 `book:cost:view` 时返回 **null**（FR-4.6 规则 5）。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/order/item")
public class OrderItemController extends BaseController
{
    private final IOrderItemService orderItemService;

    @PreAuthorize("@ss.hasPermi('sales:order:list')")
    @GetMapping("/list")
    public TableDataInfo list(OrderItem item)
    {
        startPage();
        List<OrderItem> list = orderItemService.selectOrderItemList(item);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('sales:order:list')")
    @GetMapping("/{itemId}")
    public AjaxResult getInfo(@PathVariable("itemId") Long itemId)
    {
        return success(orderItemService.selectOrderItemById(itemId));
    }
}
