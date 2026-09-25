package com.tbgbook.bookstore.sale.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.sale.domain.Order;
import com.tbgbook.bookstore.sale.domain.OrderExportRequest;
import com.tbgbook.bookstore.sale.domain.OrderExportResult;
import com.tbgbook.bookstore.sale.domain.OrderPickupResult;
import com.tbgbook.bookstore.sale.domain.OrderRedResult;

/** 销售订单 Service（本步骤只交付**只读**；写入与 cancel/red/pickup 属步骤④）。 */
public interface IOrderService extends IService<Order>
{
    List<Order> selectOrderList(Order order);

    /** 详情（含明细与支付流水）。 */
    Order selectOrderById(Long orderId);

    /**
     * 后台补录手工单（契约：门店收银走 checkout、线上下单走 `/app/order/submit`，本端点仅用于补录）。
     *
     * <p>**内部委托给结算**（`ICheckoutService#checkout`，服务端生成 requestId）：
     * 补录与结算的优惠计算、库存扣减、券核销、积分口径必须完全一致，另写一套必然分叉。
     */
    int addOrder(Order order);

    /** 修改订单（**仅待支付且未日结**；金额与明细不可改，要改走红冲）。 */
    int updateOrder(Order order);

    /** 删除订单（**仅待支付且未日结**；主从级联 + **释放已扣库存**）。 */
    int removeOrders(Long[] orderIds);

    /** 取消订单（待支付/待取货 → 已取消，**释放已扣库存并写流水**；已支付需走退货）。 */
    int cancelOrder(Long orderId, String reason);

    /** 订单红冲（**仅店长**；生成反向单 + 回退库存，红冲记录永久保留）。 */
    OrderRedResult redOrder(Long orderId, String reason);

    /** 取货核销（线上下单到店取货；核销后已完成并累加积分与成长值）。 */
    OrderPickupResult pickupOrder(Long orderId, String pickupCode, String pickupBy);

    /** 创建订单导出任务（**只建任务行**；文件生成与下载属阶段八）。 */
    OrderExportResult exportOrders(OrderExportRequest request);
}
