package com.tbgbook.bookstore.sale.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 执行优惠券发放批次的结果（`POST /sales/coupon-batch/{batchId}/execute` 的 {@code data}）。
 *
 * <p>契约要求 {@code batchId/batchNo/planQty/status}。
 *
 * <p>⚠️ 契约原文说「发放是异步的，本接口创建任务后立即返回，实际发放进度看批次详情的
 * {@code actualQty}/{@code receivedQty}」。**本实现是同步发放完毕再返回**
 * （单店几千张券的插入是毫秒级，引入线程池得不偿失，且异步线程里拿不到登录上下文
 * 会让 {@code grantBy} 丢）—— 故返回时 {@code status} 通常已是 2已完成，
 * 前端轮询 {@code actualQty} 的路径照常可用。已登记 `plan/待确认项.md`。
 *
 * @author tbgbook
 */
@Data
public class CouponBatchExecuteResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 批次ID */
    private Long batchId;

    /** 批次号 */
    private String batchNo;

    /** 计划发放数量（本次执行时按圈选结果写入） */
    private Integer planQty;

    /** 状态（1发放中 2已完成 3已失败） */
    private Integer status;

    /** 实际发放数量（同步发放故返回时即有值） */
    private Integer actualQty;
}
