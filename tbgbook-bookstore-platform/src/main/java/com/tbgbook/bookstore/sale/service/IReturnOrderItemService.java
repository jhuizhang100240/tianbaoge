package com.tbgbook.bookstore.sale.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.sale.domain.ReturnOrderItem;

/**
 * 退货单明细 Service（`/sales/return/item`）。
 *
 * <p><b>明细的增删改只允许发生在「1待审核」的退货单上</b>：有原单退货在提交时就已完成
 * （{@code status=2}），它的明细是**库存与资金的凭证**，改了就对不上账。
 * 而「待审核」只可能是无单退货（有原单退货提交即完成），故本接口的写操作只服务无单退货。
 *
 * <p><b>改明细必然牵动父单</b>：{@code total_qty} 与退款额在明细间的分摊都要重算，
 * 故每次写操作结束都调用 {@link IReturnOrderService#recalcTotals}。
 * 退款**总额**不重算 —— 那是收银员填、店长审批的数，明细的增删只改变它在各行的分摊。
 *
 * <p>{@code refund_amount} 不接受客户端传值（NFR-S9），一律服务端按定价比例分摊。
 */
public interface IReturnOrderItemService extends IService<ReturnOrderItem>
{
    List<ReturnOrderItem> selectReturnOrderItemList(ReturnOrderItem item);

    ReturnOrderItem selectReturnOrderItemById(Long itemId);

    /**
     * 新增退货单明细（**仅父单待审核**）。
     *
     * <p>书名 / ISBN / 单价按 {@code bookId} 从图书主档取，{@code orderItemId} 置 0
     * —— 无单退货没有原订单明细可挂。
     *
     * @throws com.tbgbook.common.exception.ServiceException
     *         404 图书不存在；422 父单不是待审核 / 数量不合法 / 有损却选回退可售
     */
    int addReturnOrderItem(ReturnOrderItem item);

    /**
     * 修改退货单明细（**仅父单待审核**；只接受数量与图书状况、库存去向）。
     *
     * @throws com.tbgbook.common.exception.ServiceException 404 明细不存在；409 父单已被处理
     */
    int updateReturnOrderItem(ReturnOrderItem item);

    /** 删除退货单明细（**仅父单待审核**）。 */
    int removeReturnOrderItems(Long[] itemIds);
}
