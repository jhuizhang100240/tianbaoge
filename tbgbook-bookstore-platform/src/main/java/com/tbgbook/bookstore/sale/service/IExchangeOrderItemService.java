package com.tbgbook.bookstore.sale.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.sale.domain.ExchangeOrderItem;

/**
 * 换货单明细 Service（`/sales/exchange/item`）。
 *
 * <p>与退货单明细同一套路：**只有「1待处理」的父单能增删改明细**；
 * 单价与小计一律服务端算（{@link IExchangeOrderService#priceLine}），
 * 写完回算父单的三个金额。{@code direction} 区分换出(1)/换回(2)。
 */
public interface IExchangeOrderItemService extends IService<ExchangeOrderItem>
{
    List<ExchangeOrderItem> selectExchangeOrderItemList(ExchangeOrderItem item);

    ExchangeOrderItem selectExchangeOrderItemById(Long itemId);

    /**
     * 新增换货单明细（**仅父单待处理**）。
     *
     * @throws com.tbgbook.common.exception.ServiceException
     *         404 图书不存在；422 数量不合法 / 方向非法 / 换回的书不在原订单里 / 超过可换额度
     */
    int addExchangeOrderItem(ExchangeOrderItem item);

    /** 修改换货单明细（**仅父单待处理**；只接受方向与数量，价格由服务端重算）。 */
    int updateExchangeOrderItem(ExchangeOrderItem item);

    /** 删除换货单明细（**仅父单待处理**）。 */
    int removeExchangeOrderItems(Long[] itemIds);
}
