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

import com.tbgbook.bookstore.sale.domain.Order;
import com.tbgbook.bookstore.sale.domain.OrderExportRequest;
import com.tbgbook.bookstore.sale.domain.OrderPickupRequest;
import com.tbgbook.bookstore.sale.domain.OrderReasonRequest;
import com.tbgbook.bookstore.sale.service.IOrderService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 销售订单 Controller（A-29 订单列表 / A-30 订单详情）。
 *
 * <p><b>收银开单不走本控制器</b>（契约明写）：门店收银走
 * {@code POST /sales/cashier/checkout}、线上下单走 {@code POST /app/order/submit}、
 * 教材预订走 {@code POST /sales/reserve}。本资源的 POST **仅用于后台补录手工单**，
 * 且内部委托结算执行（优惠与库存口径必须一致）。
 *
 * <p><b>改动范围被收窄</b>：只有「1待支付」且未日结的订单可改可删；
 * 已日结的只能**红冲**（§4.3 规则 6）。金额与明细一律不可改 ——
 * 一改就要同步调库存与四类优惠的分摊，那是红冲的职责。
 *
 * <p>⚠️ 字面量 {@code /list}、{@code /export} 与路径变量 {@code /{orderId}} 同为 3 段：
 * 三者都在本控制器里，字面量优先于路径变量，故不会被解析成 {@code orderId="list"}。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/order")
public class OrderController extends BaseController
{
    private final IOrderService orderService;

    @PreAuthorize("@ss.hasPermi('sales:order:list')")
    @GetMapping("/list")
    public TableDataInfo list(Order order)
    {
        startPage();
        List<Order> list = orderService.selectOrderList(order);
        return getDataTable(list);
    }

    /** 详情一次返回明细 + 支付流水（同屏展示，避免页面先出骨架再跳动）。 */
    @PreAuthorize("@ss.hasPermi('sales:order:list')")
    @GetMapping("/{orderId}")
    public AjaxResult getInfo(@PathVariable("orderId") Long orderId)
    {
        return success(orderService.selectOrderById(orderId));
    }

    /**
     * 新增销售订单（**后台补录手工单**；内部委托结算执行）。
     */
    @PreAuthorize("@ss.hasPermi('sales:order:add')")
    @Log(title = "销售订单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody Order order)
    {
        return toAjax(orderService.addOrder(order));
    }

    /**
     * 修改销售订单（**仅 1待支付且未日结**；只接受备注/收银员这类字段）。
     */
    @PreAuthorize("@ss.hasPermi('sales:order:edit')")
    @Log(title = "销售订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody Order order)
    {
        return toAjax(orderService.updateOrder(order));
    }

    /**
     * 删除销售订单（**仅 1待支付且未日结**；主从级联并**释放已扣库存**）。
     */
    @PreAuthorize("@ss.hasPermi('sales:order:remove')")
    @Log(title = "销售订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{orderIds}")
    public AjaxResult remove(@PathVariable Long[] orderIds)
    {
        return toAjax(orderService.removeOrders(orderIds));
    }

    /**
     * 取消订单（待支付/待取货 → 已取消，**释放已扣库存并写流水**）。
     *
     * <p>已支付的订单不走这里 —— 契约要求走退货流程（422）。
     */
    @PreAuthorize("@ss.hasPermi('sales:order:cancel')")
    @Log(title = "销售订单", businessType = BusinessType.UPDATE)
    @PostMapping("/{orderId}/cancel")
    public AjaxResult cancel(@PathVariable("orderId") Long orderId,
            @Valid @RequestBody OrderReasonRequest request)
    {
        return toAjax(orderService.cancelOrder(orderId, request.getReason()));
    }

    /**
     * 订单红冲（**仅店长**，权限串 {@code sales:order:red} 只授店长）。
     *
     * <p>生成反向单（{@code isRed=1}、金额取负数、{@code redOriginOrderId} 指向原单）
     * 并**回退库存**；红冲记录永久保留，且红冲单不可再红冲（§4.3 规则 6）。
     */
    @PreAuthorize("@ss.hasPermi('sales:order:red')")
    @Log(title = "订单红冲", businessType = BusinessType.UPDATE)
    @PostMapping("/{orderId}/red")
    public AjaxResult red(@PathVariable("orderId") Long orderId,
            @Valid @RequestBody OrderReasonRequest request)
    {
        return success(orderService.redOrder(orderId, request.getReason()));
    }

    /**
     * 取货核销（线上下单到店取货；核销后已完成并累加积分与成长值）。
     */
    @PreAuthorize("@ss.hasPermi('sales:order:pickup')")
    @Log(title = "订单取货核销", businessType = BusinessType.UPDATE)
    @PostMapping("/{orderId}/pickup")
    public AjaxResult pickup(@PathVariable("orderId") Long orderId,
            @Valid @RequestBody OrderPickupRequest request)
    {
        return success(orderService.pickupOrder(orderId, request.getPickupCode(), request.getPickupBy()));
    }

    /**
     * 导出订单（**只创建任务**；文件生成与下载属阶段八的下载中心）。
     */
    @PreAuthorize("@ss.hasPermi('sales:order:export')")
    @Log(title = "销售订单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public AjaxResult export(@RequestBody(required = false) OrderExportRequest request)
    {
        return success(orderService.exportOrders(request));
    }
}
