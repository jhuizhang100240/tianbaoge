package com.tbgbook.bookstore.member.service;

import java.util.List;

import com.tbgbook.bookstore.member.domain.PointAdjustRequest;
import com.tbgbook.bookstore.member.domain.PointBatch;
import com.tbgbook.bookstore.member.domain.PointChangeResult;
import com.tbgbook.bookstore.member.domain.PointExpiringResult;
import com.tbgbook.bookstore.member.domain.PointGrantRequest;
import com.tbgbook.bookstore.member.domain.PointLog;

/**
 * 会员积分 Service 接口（阶段五步骤②，页面 A-47）。
 *
 * <p>对外的读接口是「积分批次 / 积分流水」两个只读资源；写操作只有三个动作：
 * 人工调整、积分赠送、即将到期查询。积分增减一律**走批次**（先到期先消耗），
 * 保证「到期清零」与总账一致。
 *
 * <p>收银 / 退货流程通过 {@link #deductPoints} / {@link #awardPoints} / {@link #returnPoints}
 * 复用本服务（阶段四遗留的跨域写在这里完成收编）。
 *
 * @author tbgbook
 */
public interface IMemberPointService
{
    /**
     * 查询积分批次列表。
     *
     * @param batch 查询条件
     * @return 批次集合
     */
    List<PointBatch> selectPointBatchList(PointBatch batch);

    /**
     * 查询积分批次详情。
     *
     * @param batchId 批次ID
     * @return 批次；不存在时抛 404
     */
    PointBatch selectPointBatchById(Long batchId);

    /**
     * 查询积分流水列表。
     *
     * @param log 查询条件
     * @return 流水集合
     */
    List<PointLog> selectPointLogList(PointLog log);

    /**
     * 查询积分流水详情。
     *
     * @param logId 流水ID
     * @return 流水；不存在时抛 404
     */
    PointLog selectPointLogById(Long logId);

    /**
     * 人工调整积分（敏感操作）：正数走新批次，负数按批次先到期先消耗。
     *
     * @param request 调整请求
     * @return 变动结果
     */
    PointChangeResult adjustPoints(PointAdjustRequest request);

    /**
     * 积分赠送：写入独立批次（来源=3 活动赠送），同样受有效期约束。
     *
     * @param request 赠送请求
     * @return 变动结果
     */
    PointChangeResult grantPoints(PointGrantRequest request);

    /**
     * 即将到期积分查询（默认窗口取 biz.points.expire.notify.days）。
     *
     * @param days     天数阈值，null=取配置
     * @param memberId 限定会员，null=全部
     * @return 明细 + 汇总
     */
    PointExpiringResult selectExpiring(Integer days, Long memberId);

    /**
     * [收银抵扣] 扣减积分（变动类型=2 抵扣消耗，按批次先到期先消耗）。
     *
     * @return 扣减后的积分余额
     */
    int deductPoints(Long memberId, int points, String refNo, String remark);

    /**
     * [消费获得] 增加积分（来源=1 消费获得，变动类型=1）。
     *
     * @return 增加后的积分余额
     */
    int awardPoints(Long memberId, int points, String refNo, String remark);

    /**
     * [退货返还] 退还积分（来源=4 退货返还，变动类型=5）。
     *
     * @return 返还后的积分余额
     */
    int returnPoints(Long memberId, int points, String refNo, String remark);
}
