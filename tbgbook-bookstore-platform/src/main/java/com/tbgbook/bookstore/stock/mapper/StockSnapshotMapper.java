package com.tbgbook.bookstore.stock.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.stock.domain.StockSnapshot;

/**
 * 库存快照主表 Mapper 接口。
 *
 * @author tbgbook
 */
public interface StockSnapshotMapper extends BaseMapper<StockSnapshot>
{
    /**
     * 查询库存快照列表（支持按快照日期区间筛选）。
     *
     * @param snapshot 查询条件
     * @return 快照集合
     */
    List<StockSnapshot> selectStockSnapshotList(StockSnapshot snapshot);

    /**
     * 按日期取快照（`uk_store_date` 保证至多一条）。
     *
     * <p>供「按日期对比」使用（{@code compareDate} 参数）。
     *
     * @param snapshotDate 快照日期
     * @return 快照；该日未生成时为 {@code null}
     */
    StockSnapshot selectByDate(@Param("snapshotDate") LocalDate snapshotDate);

    /**
     * 按 `uk_store_date` 幂等写入主表：已存在则更新汇总并返回原 ID。
     *
     * @param snapshot 快照
     * @return 影响行数（1 新增 / 1 更新）
     */
    int upsertSnapshot(StockSnapshot snapshot);

    /**
     * 按 store_id + snapshot_date 取主键。
     *
     * <p>`INSERT ... ON DUPLICATE KEY UPDATE` 在「更新后无列变化」时不会回填
     * generated key，不能拿返回值当主键；写库后必须显式再查一次。
     */
    Long selectIdByStoreAndDate(@Param("storeId") Long storeId,
            @Param("snapshotDate") LocalDate snapshotDate);

    /**
     * 删除某快照的全部明细（任务重算时整体替换）。
     *
     * @param snapshotId 快照ID
     * @return 删除行数
     */
    int deleteItemsBySnapshotId(@Param("snapshotId") Long snapshotId);
}
