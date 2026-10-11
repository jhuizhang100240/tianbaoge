package com.tbgbook.bookstore.borrow.service;

import java.util.List;

import com.tbgbook.bookstore.borrow.domain.DepositLog;
import com.tbgbook.bookstore.borrow.domain.DepositPayRequest;
import com.tbgbook.bookstore.borrow.domain.DepositRefundRequest;
import com.tbgbook.bookstore.borrow.domain.DepositResult;

/**
 * 借阅押金 Service 接口（阶段五步骤④第二批）。
 *
 * <p>押金流水只增不删；缴纳 / 退还走动作端点，退还要求无在借且无未结清罚金。
 *
 * @author tbgbook
 */
public interface IDepositService
{
    /**
     * 查询押金流水列表。
     *
     * @param log 查询条件
     * @return 流水集合
     */
    List<DepositLog> selectDepositLogList(DepositLog log);

    /**
     * 查询押金流水详情。
     *
     * @param logId 流水ID
     * @return 流水；不存在时抛 404
     */
    DepositLog selectDepositLogById(Long logId);

    /**
     * 缴纳押金。
     *
     * @param request 缴纳请求
     * @return 缴纳结果
     */
    DepositResult pay(DepositPayRequest request);

    /**
     * 退还押金（无在借图书且无未结清罚金）。
     *
     * @param request 退还请求
     * @return 退还结果
     */
    DepositResult refund(DepositRefundRequest request);
}
