package com.tbgbook.bookstore.stock.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.stock.domain.Stock;

/**
 * 库存 服务层。
 *
 * <p><b>四类数量不接受直接写入</b>（FR-4.2 规则 1）：任何数量变更必须走入库 / 出库 /
 * 盘点 / 调拨 / 报损等单据，由各单据的服务端逻辑在同事务内改库存 + 写流水。
 * 本服务只负责库存行的「建档 / 改库位 / 删行」。
 *
 * @author tbgbook
 */
public interface IStockService extends IService<Stock>
{
    /**
     * 查询库存列表。
     *
     * @param stock 查询条件
     * @return 库存集合
     */
    List<Stock> selectStockList(Stock stock);

    /**
     * 查询库存详情。
     *
     * @param stockId 库存ID
     * @return 库存；不存在时抛 404
     */
    Stock selectStockById(Long stockId);

    /**
     * 新增库存行（建档某个「图书 + 库位」的库存位）。
     *
     * <p>四类数量与安全库存一律置 0，{@code version} 置 0 —— 客户端传了会被忽略。
     * 「图书 + 库位」已存在时返回 409（{@code uk_book_location}）。
     *
     * @param stock 库存
     * @return 结果
     */
    int addStock(Stock stock);

    /**
     * 修改库存行 —— **只允许改库位**。
     *
     * <p>四类数量、安全库存、版本号都不接受传入。走 {@code @Version} 乐观锁：
     * 先读当前版本再更新，期间被他人改过则影响 0 行并返回 409，不静默覆盖。
     *
     * @param stock 库存
     * @return 结果
     */
    int updateStock(Stock stock);

    /**
     * 删除库存行。
     *
     * <p>该「图书 + 库位」上已有库存流水时禁止删除（422）—— 流水只增不删，
     * 删掉库存行会让历史流水失去主语。
     *
     * @param stockIds 需要删除的库存ID数组
     * @return 结果
     */
    int removeStocks(Long[] stockIds);
}
