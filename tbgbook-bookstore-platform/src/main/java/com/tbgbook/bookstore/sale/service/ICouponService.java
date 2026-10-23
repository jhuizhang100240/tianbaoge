package com.tbgbook.bookstore.sale.service;

import java.math.BigDecimal;
import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.sale.domain.Coupon;
import com.tbgbook.bookstore.sale.domain.CouponTemplate;
import com.tbgbook.bookstore.sale.domain.CouponGrantRequest;
import com.tbgbook.bookstore.sale.domain.CouponGrantResult;
import com.tbgbook.bookstore.sale.domain.CouponVerifyResult;

/**
 * 会员优惠券实例 Service 接口（页面 A-38，FR-4.3.8）。
 *
 * <p><b>核销不开放手工接口</b>（契约明写）：{@code 1未使用→2已使用} 由**收银结算内部**完成。
 * 本接口的增改只服务「后台补录」与元数据修正。
 *
 * @author tbgbook
 */
public interface ICouponService extends IService<Coupon>
{
    /**
     * 查询会员优惠券实例列表。
     *
     * <p>查询前会**惰性刷新过期状态**（契约：「过期由定时任务或查询时惰性判定」）——
     * 否则会出现「状态写着未使用、其实早过期」的脏数据让收银误判可用。
     *
     * @param coupon 查询条件
     * @return 券集合
     */
    List<Coupon> selectCouponList(Coupon coupon);

    /**
     * 查询会员优惠券实例详情（同样先惰性刷新过期状态）。
     *
     * @param couponId 券ID
     * @return 券；不存在时抛 404
     */
    Coupon selectCouponById(Long couponId);

    /**
     * 新增会员优惠券实例（**后台补录**）。
     *
     * <p>面额、门槛、有效期、券名**全部从模板快照**，客户端传了不认。
     *
     * <p>⚠️ <b>不校验每人限领</b>：本接口是管理端的补录入口，本就是「已经超限了要手工补一张」
     * 这类场景的出口；限领校验放在 {@link #grant}（面向批量发放）。
     *
     * @param coupon 券
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 400 未指定模板或会员；404 模板不存在
     */
    int addCoupon(Coupon coupon);

    /**
     * 修改会员优惠券实例（**仅未使用可改**，且只能改持有人等元数据）。
     *
     * @param coupon 券
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 422 券已使用或已过期
     */
    int updateCoupon(Coupon coupon);

    /**
     * 删除会员优惠券实例（**物理删除，且仅未使用可删**）。
     *
     * <p>已使用的券是**财务记录**（对应一笔订单的抵扣），删掉会让订单的优惠金额失去凭据。
     *
     * @param couponIds 券主键数组
     * @return 实际删除行数
     * @throws com.tbgbook.common.exception.ServiceException 422 含已使用或已过期的券
     */
    int removeCoupons(Long[] couponIds);

    /**
     * 给一个会员发一张券（**发放的共用原语**，供人工补发与批次发放复用）。
     *
     * <p>负责：生成唯一券码、从模板**快照**券名/面额/门槛/有效期、置初始状态与领取时间。
     * 放在这里而不是各调用方各写一遍，是因为「快照哪几个字段、有效期怎么算」一旦分叉，
     * 同一批券就会出现两种口径 —— 而券是要拿去抵扣真金白银的。
     *
     * <p>⚠️ **不做限领与总量校验**（那是调用方的职责）：本方法只保证「发出去的那张券本身是对的」，
     * 因为限领是**按人**判、总量是**按模板**判，两者的判定时机与失败处理方式都不同
     * （补发是逐人失败、批次是整批预检）。
     *
     * @param template 模板（调用方已校验可用）
     * @param memberId 会员ID
     * @param batchId  发放批次ID（0 = 非批次发放，如人工补发）
     * @return 新券ID
     */
    Long issueCoupon(CouponTemplate template, Long memberId, Long batchId);

    /**
     * 人工补发优惠券（`POST /sales/coupon/grant`，**敏感操作**，强制记操作日志）。
     *
     * <p><b>部分失败不整体回滚</b>（契约错误码原文）：逐人尝试，
     * 已达每人限领的人记进 {@code failDetails} 后照常提交 ——
     * 让一个人失败连带其余人拿不到券，对运营不可接受。
     *
     * @param request 补发请求（模板、会员列表、原因）
     * @return 成功数 / 失败数 / 失败明细
     * @throws com.tbgbook.common.exception.ServiceException 404 模板不存在或未发布；
     *         422 模板发放总量已用尽
     */
    CouponGrantResult grant(CouponGrantRequest request);

    /**
     * 校验券对当前购物车是否可用（`GET /sales/coupon/verify`）。
     *
     * <p>⚠️ 只能判「是否本人持有 / 是否未使用 / 是否过期 / 门槛够不够」——
     * 契约 {@code reason} 里还列了「范围不符」「与特价商品冲突」，那两类**需要购物车明细**，
     * 本接口拿不到（只有 {@code amount}），故在**试算**里判。已在文档注明边界。
     *
     * @param couponId 券ID
     * @param memberId 会员ID
     * @param amount   当前商品金额
     * @return 可用性与原因
     * @throws com.tbgbook.common.exception.ServiceException 404 券不存在
     */
    CouponVerifyResult verify(Long couponId, Long memberId, BigDecimal amount);
}
