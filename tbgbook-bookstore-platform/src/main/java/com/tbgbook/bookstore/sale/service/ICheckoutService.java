package com.tbgbook.bookstore.sale.service;

import com.tbgbook.bookstore.sale.domain.CheckoutRequest;
import com.tbgbook.bookstore.sale.domain.CheckoutResult;

/**
 * 收银结算 Service —— **本项目最核心的写接口**（A-28）。
 *
 * <p>一次调用在同一事务内完成六步（契约原文）：
 * ①二次校验并**扣减库存** → ②写订单头与明细（四类优惠按比例分摊到行）→ ③写支付流水
 * → ④扣减积分 / 核销券 → ⑤累加成长值与积分 → ⑥更新会员消费汇总。
 * **任一步失败整体回滚。**
 *
 * <p><b>幂等</b>（NFR-P2 约束 4）：同 {@code requestId} 重复提交返回首次结果、业务只执行一次。
 * 并发重复（首次仍在跑）返回 409。
 */
public interface ICheckoutService
{
    /**
     * 结算。
     *
     * @param request 结算请求（含幂等键 requestId、商品、组合支付）
     * @return 订单号/金额/四类优惠/逐行分摊/积分
     * @throws com.tbgbook.common.exception.ServiceException
     *         409 requestId 并发重复 / 库存乐观锁冲突；
     *         422 库存不足 / 储值余额不足 / 支付金额之和 ≠ 应付 / 券不可用 / 积分超限
     */
    CheckoutResult checkout(CheckoutRequest request);
}
