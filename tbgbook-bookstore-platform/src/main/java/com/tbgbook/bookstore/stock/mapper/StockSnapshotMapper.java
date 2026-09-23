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
}
