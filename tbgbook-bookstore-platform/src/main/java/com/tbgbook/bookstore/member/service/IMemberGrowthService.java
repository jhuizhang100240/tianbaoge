package com.tbgbook.bookstore.member.service;

import java.util.List;

import com.tbgbook.bookstore.member.domain.MemberGrowthLog;

/**
 * 会员成长值 Service 接口（阶段五步骤②）。
 *
 * <p>对外的读接口是「成长值流水」只读资源；写入口 {@link #addGrowth} 供消费 /
 * 按期归还 / 评价 / 人工调整调用，并在同事务内做**等级自动升级**。
 *
 * @author tbgbook
 */
public interface IMemberGrowthService
{
    /**
     * 查询成长值流水列表。
     *
     * @param log 查询条件
     * @return 流水集合
     */
    List<MemberGrowthLog> selectMemberGrowthLogList(MemberGrowthLog log);

    /**
     * 查询成长值流水详情。
     *
     * @param logId 流水ID
     * @return 流水；不存在时抛 404
     */
    MemberGrowthLog selectMemberGrowthLogById(Long logId);

    /**
     * 增加成长值（只增不减），写流水并在达到门槛时自动升级（写等级变更记录）。
     *
     * @param memberId   会员ID
     * @param changeType 变更类型（1消费 2按时归还 3评价 4人工调整）
     * @param delta      增量（必须 > 0）
     * @param refNo      关联单号
     * @param remark     备注
     * @return 变动后的成长值
     */
    int addGrowth(Long memberId, int changeType, int delta, String refNo, String remark);
}
