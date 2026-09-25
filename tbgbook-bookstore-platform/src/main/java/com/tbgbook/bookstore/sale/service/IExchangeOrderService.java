package com.tbgbook.bookstore.sale.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.sale.domain.ExchangeConfirmResult;
import com.tbgbook.bookstore.sale.domain.ExchangeOrder;
import com.tbgbook.bookstore.sale.domain.ExchangeOrderItem;

/**
 * 换货单 Service（A-32 换货处理，FR-4.3.6）。
 *
 * <p><b>一次换货分两步</b>：{@link #addExchangeOrder} 建单（{@code status=1待处理}，只算账不动库存），
 * {@link #confirm} 执行（换出出库、换回入库、按差额补退，{@code status=2已完成}）。
 * 分开的理由：顾客挑了新书、价格算清楚之后才动库存，中途反悔只需删单。
 *
 * <p><b>换货必须挂原订单</b>（{@code originOrderId > 0}）：换回的书要能对上「他买过这本书、
 * 还没退过也没换过」，否则等于凭空白送。无单退货那条路（审批后只记单据）在换货里**不存在**
 * —— 契约也没有对应的审批端点。
 *
 * <p><b>两边定价口径不同，这是本次自定（已登记进度日志）</b>：
 * <ul>
 *   <li><b>换出</b>（顾客拿走的新书）：按图书**当前定价**计</li>
 *   <li><b>换回</b>（顾客退回来的书）：按原订单明细的**分摊后实付单价**
 *       （{@code biz_order_item.share_amount ÷ qty}）计 —— 与退货同一口径。
 *       若按当前定价算，书一降价顾客换货就白赚差价</li>
 * </ul>
 * {@code diffAmount = 换出 − 换回}：**正数=会员补款，负数=退款**（契约原文）。
 */
public interface IExchangeOrderService extends IService<ExchangeOrder>
{
    List<ExchangeOrder> selectExchangeOrderList(ExchangeOrder order);

    /** 详情一次返回明细（同屏展示）。 */
    ExchangeOrder selectExchangeOrderById(Long exchangeId);

    /**
     * 新建换货单（**待处理**：只算账，不动库存）。
     *
     * @param order 换货单（须带 {@code originOrderId} 与 {@code items}，明细用 {@code direction} 分方向）
     * @throws com.tbgbook.common.exception.ServiceException
     *         400 未指定原订单 / 明细为空；404 原订单或图书不存在；
     *         422 方向非法 / 数量不合法 / 换回的书不在原订单里 / 换回数量超过可换额度
     */
    int addExchangeOrder(ExchangeOrder order);

    /**
     * 修改换货单（**仅 1待处理**；本表其余字段都由服务端维护，故只接受**整体替换明细**）。
     *
     * @throws com.tbgbook.common.exception.ServiceException
     *         400 未提交明细；422 当前状态不允许修改
     */
    int updateExchangeOrder(ExchangeOrder order);

    /** 删除换货单（**仅 1待处理**；已完成的是库存凭证）。 */
    int removeExchangeOrders(Long[] exchangeIds);

    /**
     * 确认换货：**换出扣可售库存 → 换回回补库存 → 按差额补退**，单据转 2已完成。
     *
     * @param payMethod 补退款支付方式（{@code diffAmount != 0} 时**必填**，契约错误码原文）
     * @throws com.tbgbook.common.exception.ServiceException
     *         422 差额不为 0 但未传支付方式 / 换出商品库存不足 / 换回数量超过可换额度；409 单据已处理
     */
    ExchangeConfirmResult confirm(Long exchangeId, Integer payMethod);

    /** 取父单并断言其为「1待处理」——明细的增删改只允许发生在待处理的单据上。 */
    ExchangeOrder requireEditable(Long exchangeId, String action);

    /**
     * 按父单原订单给一行明细**服务端定价**：填 ISBN / 书名 / 单价 / 小计。
     *
     * <p>给明细服务用（明细的增改都要走同一套定价），避免两处各写一遍。
     *
     * @throws com.tbgbook.common.exception.ServiceException 404 图书不存在；422 换回的书不在原订单里
     */
    void priceLine(ExchangeOrder parent, ExchangeOrderItem item);

    /** 重算父单的换出 / 换回 / 差额金额（明细增删改后调用，一律服务端算）。 */
    void recalcTotals(Long exchangeId);
}
