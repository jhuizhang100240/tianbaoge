package com.tbgbook.bookstore.sale.service;

import com.tbgbook.bookstore.sale.domain.CashierScanRequest;
import com.tbgbook.bookstore.sale.domain.CashierScanResult;
import com.tbgbook.bookstore.sale.domain.MemberLookupRequest;
import com.tbgbook.bookstore.sale.domain.MemberLookupResult;
import com.tbgbook.bookstore.sale.domain.TrialRequest;
import com.tbgbook.bookstore.sale.domain.TrialResult;

/**
 * 收银台基础层 Service（FR-4.3.1 / FR-4.3.3）—— 扫码加商品、会员识别、优惠试算。
 *
 * <p>三个接口都**不落库、不动库存**：它们是收银开单过程中的即时计算，
 * 真正的扣减在结算（步骤④）。
 */
public interface ICashierService
{
    /**
     * 扫码添加商品（FR-4.3.1）。
     *
     * <p>正价书按 ISBN / 图书编号命中并按数量汇总；二手书按 `BC` 条码 ——
     * ⚠️ **二手书池属阶段六，当前明确拒绝**（404 + 说明文案），而不是当正价书查
     * （那会把「未接入」伪装成「不存在」）。
     *
     * <p>库存不足**只提示不阻断**：{@code qtyAvailable} 如实返回，拦截在结算时。
     *
     * @param request 扫码内容与数量
     * @return 商品与实时单价、可售量
     * @throws com.tbgbook.common.exception.ServiceException 404 条码未命中 / 二手书未接入；
     *         422 图书已下架或停售
     */
    CashierScanResult scan(CashierScanRequest request);

    /**
     * 收银台会员识别（FR-4.3.3）：手机号 / 会员卡号 / 会员码 → 等级折扣 + 可用券 + 借阅状态。
     *
     * @param request 识别内容
     * @return 会员信息（手机号**脱敏**）
     * @throws com.tbgbook.common.exception.ServiceException 404 未查到会员
     */
    MemberLookupResult memberLookup(MemberLookupRequest request);

    /**
     * 优惠试算（FR-4.3.1）。
     *
     * <p>计算顺序**固定**：单品折扣 → 会员等级折扣 → 优惠券 → 积分抵扣（§4.3 规则 2），
     * 并把四类优惠**按金额比例分摊到行**（§4.3 规则 5，毛利核算口径）。
     *
     * @param request 会员、券、积分与商品明细
     * @return 四类优惠分项 + 应付金额 + 逐行分摊
     * @throws com.tbgbook.common.exception.ServiceException 404 图书或券不存在；
     *         422 券不可用（门槛/过期/特价冲突/非本人）/ 积分超抵扣上限或超持有量
     */
    TrialResult trial(TrialRequest request);
}
