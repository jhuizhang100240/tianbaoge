package com.tbgbook.bookstore.stock.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.stock.domain.StockFlow;

/**
 * 库存流水 Mapper 接口。
 *
 * <p>本表**只增不删**，因此这里**只有查询方法** —— 不提供 insert/update/delete 的封装。
 * 写入由各单据的服务端逻辑在同事务内完成（阶段三步骤②起）。
 *
 * @author tbgbook
 */
public interface StockFlowMapper extends BaseMapper<StockFlow>
{
    /**
     * 查询库存流水列表（含 JOIN `biz_book` 的 isbn 筛选与书名展示）。
     *
     * @param flow 查询条件
     * @return 流水集合
     */
    List<StockFlow> selectStockFlowList(StockFlow flow);

    /**
     * 统计某「图书 + 库位」上的流水条数，供库存行的删除保护用。
     *
     * <p>为什么要单独写：{@code ReferenceGuard#countReference} 只支持**单列**引用，
     * 而流水表是按 {@code (book_id, location_id)} 这一对定位库存行的（表上没有 stock_id）。
     *
     * @param bookId     图书ID
     * @param locationId 库位ID
     * @return 流水条数
     */
    Long countByBookAndLocation(@Param("bookId") Long bookId, @Param("locationId") Long locationId);
}
