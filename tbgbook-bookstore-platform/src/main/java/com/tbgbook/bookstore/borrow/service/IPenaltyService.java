package com.tbgbook.bookstore.borrow.service;

import java.util.List;

import com.tbgbook.bookstore.borrow.domain.Penalty;
import com.tbgbook.bookstore.borrow.domain.PenaltyPayRequest;
import com.tbgbook.bookstore.borrow.domain.PenaltyPayResult;
import com.tbgbook.bookstore.borrow.domain.PenaltyReduceRequest;
import com.tbgbook.bookstore.borrow.domain.PenaltyReduceResult;
import com.tbgbook.bookstore.borrow.domain.PenaltyRequest;
import com.tbgbook.bookstore.borrow.domain.PenaltyResult;

/**
 * 罚金与赔偿单 Service 接口（阶段五步骤④第二批，FR-4.5.5 / FR-4.5.7）。
 *
 * <p>资源只读（列表 / 详情）；生成走 {@link #generate}，缴费走 {@link #pay}，
 * 减免走 {@link #reduce}（需店长）。罚金属财务凭证，**不提供删除**。
 *
 * @author tbgbook
 */
public interface IPenaltyService
{
    /**
     * 查询罚金 / 赔偿单列表。
     *
     * @param penalty 查询条件
     * @return 单据集合
     */
    List<Penalty> selectPenaltyList(Penalty penalty);

    /**
     * 查询罚金 / 赔偿单详情。
     *
     * @param penaltyId 单据ID
     * @return 单据；不存在时抛 404
     */
    Penalty selectPenaltyById(Long penaltyId);

    /**
     * 生成罚金 / 赔偿单（金额由服务端按规则计算；优先从押金扣除）。
     *
     * @param request 生成请求
     * @return 生成结果
     */
    PenaltyResult generate(PenaltyRequest request);

    /**
     * 缴纳罚金 / 赔偿款。
     *
     * @param penaltyId 单据ID
     * @param request   缴费请求
     * @return 缴费结果
     */
    PenaltyPayResult pay(Long penaltyId, PenaltyPayRequest request);

    /**
     * 减免罚金 / 赔偿款（**需店长审批**）。
     *
     * @param penaltyId 单据ID
     * @param request   减免请求
     * @return 减免结果
     */
    PenaltyReduceResult reduce(Long penaltyId, PenaltyReduceRequest request);
}
