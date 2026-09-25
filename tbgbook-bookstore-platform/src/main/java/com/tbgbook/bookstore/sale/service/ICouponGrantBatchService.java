package com.tbgbook.bookstore.sale.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.sale.domain.CouponBatchExecuteResult;
import com.tbgbook.bookstore.sale.domain.CouponGrantBatch;

/**
 * 优惠券发放批次 Service 接口（页面 A-38，FR-4.3.8）。
 *
 * <p><b>两段式</b>：先建批次（圈选人群），再 {@link #execute} 发放。
 * 圈选支持三种人群（按标签 / 按等级 / 手工名单）。
 *
 * @author tbgbook
 */
public interface ICouponGrantBatchService extends IService<CouponGrantBatch>
{
    /**
     * 查询优惠券发放批次列表。
     *
     * @param batch 查询条件
     * @return 批次集合
     */
    List<CouponGrantBatch> selectCouponGrantBatchList(CouponGrantBatch batch);

    /**
     * 查询优惠券发放批次详情。
     *
     * @param batchId 批次ID
     * @return 批次；不存在时抛 404
     */
    CouponGrantBatch selectCouponGrantBatchById(Long batchId);

    /**
     * 新增优惠券发放批次（状态恒为 1发放中，{@code batchNo} 服务端生成，统计列归零）。
     *
     * @param batch 批次
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 400 未指定模板或人群类型；
     *         422 人群类型需要目标值却没给
     */
    int addCouponGrantBatch(CouponGrantBatch batch);

    /**
     * 修改优惠券发放批次（**仅未执行的批次可改**）。
     *
     * @param batch 批次
     * @return 影响行数
     * @throws com.tbgbook.common.exception.ServiceException 422 批次已执行
     */
    int updateCouponGrantBatch(CouponGrantBatch batch);

    /**
     * 删除优惠券发放批次（**已执行完成的不可删**）。
     *
     * @param batchIds 批次主键数组
     * @return 实际删除行数
     * @throws com.tbgbook.common.exception.ServiceException 422 含已执行完成的批次
     */
    int removeCouponGrantBatches(Long[] batchIds);

    /**
     * 执行发放（`POST /sales/coupon-batch/{batchId}/execute`）。
     *
     * <p>按 {@code targetType} 圈选人群 → 校验模板可用与总量 → 逐人发券 →
     * 回写 {@code planQty}/{@code actualQty}/{@code grantBy}/{@code grantTime} 并置 {@code 2已完成}。
     *
     * <p>⚠️ <b>不校验每人限领</b>（与人工补发不同）：批次面向的是成批人群营销，
     * 按人限领会让一部分人静默失败，而本接口的响应结构（{@code planQty}/{@code status}）
     * 没有地方交代「谁没拿到」。限领校验留给人工补发那条逐人可解释的路径。
     *
     * @param batchId   批次ID
     * @param grantType 发放方式，null 则沿用批次上的值
     * @return 批次ID/批次号/计划量/状态/实际量
     * @throws com.tbgbook.common.exception.ServiceException 404 批次或模板不存在；
     *         409 批次已执行；422 目标人群为空 / 模板总量不足
     */
    CouponBatchExecuteResult execute(Long batchId, Integer grantType);
}
