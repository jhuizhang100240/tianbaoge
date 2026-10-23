package com.tbgbook.bookstore.member.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.member.domain.PointBatch;
import com.tbgbook.bookstore.member.domain.PointExpiringItem;

/**
 * 积分批次 Mapper 接口。
 *
 * <p>除列表查询外，还承载「先到期先消耗」所需的原子扣减：
 * {@code consumeFromBatch} 带 {@code remain_points >= take} 条件 —— 并发下影响 0 行即该批次已被抢扣，
 * 服务层跳过该批次继续扫下一批（不会超扣）。
 *
 * @author tbgbook
 */
public interface PointBatchMapper extends BaseMapper<PointBatch>
{
    /**
     * 查询积分批次列表。
     *
     * @param batch 查询条件
     * @return 批次集合
     */
    List<PointBatch> selectPointBatchList(PointBatch batch);

    /**
     * 某会员当前可用的批次（{@code status=1} 且 {@code remain_points>0}），
     * 按**先到期先消耗**排序（{@code expire_time asc, batch_id asc}）。
     */
    List<PointBatch> selectAvailableBatches(@Param("memberId") Long memberId);

    /**
     * 从指定批次扣减（条件更新：剩余积分足够才扣）。
     *
     * @return 影响行数；0=批次已被并发扣减或不足
     */
    int consumeFromBatch(@Param("batchId") Long batchId, @Param("take") int take);

    /** 批次扣到 0 时置为 2已用完（幂等） */
    int markBatchUsedUp(@Param("batchId") Long batchId);

    /**
     * 到期清零（条件更新，幂等）：仅当批次仍有效、仍有剩余且已过期时生效。
     *
     * @param batchId 批次ID
     * @param now     应用侧当前时间（A32：业务时间不用库 now()）
     * @return 影响行数；0=已被并发清零或尚未到期
     */
    int expireBatch(@Param("batchId") Long batchId, @Param("now") LocalDateTime now);

    /**
     * 即将到期积分明细（批次 JOIN 会员），时间窗口由**应用侧**传入（A32 约束）。
     */
    List<PointExpiringItem> selectExpiringItems(@Param("now") LocalDateTime now,
            @Param("cutoff") LocalDateTime cutoff, @Param("memberId") Long memberId);
}
