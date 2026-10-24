package com.tbgbook.bookstore.stock.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.stock.domain.SnapshotBookAgg;
import com.tbgbook.bookstore.stock.domain.StockSnapshotItem;

/**
 * 库存快照明细 Mapper 接口。
 *
 * <p><b>只读</b>：不提供任何写方法 —— 明细由「库存快照生成」任务（阶段十）与主表同事务写入。
 *
 * @author tbgbook
 */
public interface StockSnapshotItemMapper extends BaseMapper<StockSnapshotItem>
{
    /**
     * 查询库存快照明细列表（逐行 = 某图书在某库位）。
     *
     * @param item 查询条件
     * @return 明细集合
     */
    List<StockSnapshotItem> selectStockSnapshotItemList(StockSnapshotItem item);

    /**
     * 把某份快照的明细**按图书**聚合（跨库位求和），供对比使用。
     *
     * <p>对比页关心的是「这本书的货多了还是少了」，故按图书聚合而非逐库位展开；
     * 书名取 {@code biz_book} 的**当前**书名（书被删也照常 LEFT JOIN 出来，
     * 历史快照不该因为图书改名或下架而显示不出书名）。
     *
     * @param snapshotId 快照ID
     * @return 每本书一行（qty = 四类之和，amount = 各行金额之和）
     */
    List<SnapshotBookAgg> selectBookAggBySnapshot(@Param("snapshotId") Long snapshotId);

    /**
     * 生成快照的源数据：按「图书 + 库位」汇总四类库存，并回填 ISBN 与成本价。
     *
     * @return 快照源行
     */
    List<java.util.Map<String, Object>> selectSnapshotSourceRows();
}
