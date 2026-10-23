package com.tbgbook.bookstore.sale.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.sale.domain.CashierHoldRequest;
import com.tbgbook.bookstore.sale.domain.CashierHoldResult;
import com.tbgbook.bookstore.sale.domain.HoldResumeResult;
import com.tbgbook.bookstore.sale.domain.OrderHold;

/**
 * 收银挂单 Service（FR-4.3.2）。
 *
 * <p>⚠️ 购物车结构由本实现定义（契约未定义）：{@code {"items":[{"bookId","usedBookId","qty","unitPrice"}]}}。
 * 取单要靠它重校验，故不能真当不透明 blob。已登记 `plan/待确认项.md`。
 */
public interface IOrderHoldService extends IService<OrderHold>
{
    List<OrderHold> selectOrderHoldList(OrderHold hold);

    OrderHold selectOrderHoldById(Long holdId);

    /** 新增挂单（草稿态；**不动库存**）。 */
    int addOrderHold(OrderHold hold);

    int updateOrderHold(OrderHold hold);

    int removeOrderHolds(Long[] holdIds);

    /** 收银台挂单（`POST /sales/cashier/hold`）。 */
    CashierHoldResult cashierHold(CashierHoldRequest request);

    /** 作废挂单 → {@code status=3}（挂单不涉库存与资金，无副作用）。 */
    int cancelHold(Long holdId);

    /**
     * 取单 → {@code status=2}。**重新校验库存与价格**，变动写进 {@code changes}。
     *
     * <p>挂单**不锁库存**，挂单期间书可能被买走、活动可能结束 —— 故必须重校验并如实列出变动。
     */
    HoldResumeResult resumeHold(Long holdId);
}
