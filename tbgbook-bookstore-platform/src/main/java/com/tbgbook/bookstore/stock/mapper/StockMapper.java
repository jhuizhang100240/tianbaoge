package com.tbgbook.bookstore.stock.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.stock.domain.CategoryStock;
import com.tbgbook.bookstore.stock.domain.ScanStock;
import com.tbgbook.bookstore.stock.domain.Stock;
import com.tbgbook.bookstore.stock.domain.StockOverview;
import com.tbgbook.bookstore.stock.domain.WarningCounts;

/**
 * 库存 Mapper 接口。
 *
 * @author tbgbook
 */
public interface StockMapper extends BaseMapper<Stock>
{
    /**
     * 查询库存列表（含 JOIN `biz_book` 的 isbn / title 筛选与展示字段）。
     *
     * @param stock 查询条件
     * @return 库存集合
     */
    List<Stock> selectStockList(Stock stock);

    /**
     * 库存总览四类合计 + 品种数 + 库存总金额（A-14 顶部指标卡）。
     *
     * <p>金额是**成本口径**（{@code sum(qty * cost_price)}），调用方需按
     * {@code inventory:cost:view} 权限决定是否置 null（FR-4.6 规则 5）。
     *
     * @param stock 限定条件（categoryId / locationId，均可空）
     * @return 合计行；无数据时各列为 0（SQL 用 ifnull 兜底）
     */
    StockOverview selectOverviewTotals(Stock stock);

    /**
     * 按图书分类聚合库存（A-14 饼图）。
     *
     * @param stock 限定条件（locationId 可空）
     * @return 每个分类一行，按金额倒序
     */
    List<CategoryStock> selectCategoryDistribution(Stock stock);

    /**
     * 四类预警数量（A-14 指标卡下方）。
     *
     * <p>读 `biz_stock_warning` 的**待处理**行并按 {@code warning_type} 分档
     * （1补货 2滞销 3临期 4零库存）。本查询只读，不依赖阶段三步骤⑤的预警服务。
     *
     * @return 四类计数；无数据时为 0
     */
    WarningCounts selectWarningCounts();

    /**
     * 扫码查书：按图书 ID 汇总，**逐库位展开**（A-14 扫码 / B-05）。
     *
     * @param bookId 图书ID
     * @return 每个库位一行（含库位编码与名称）；无库存时返回空列表
     */
    List<ScanStock> selectStocksByBookId(@Param("bookId") Long bookId);

    /**
     * 把安全库存写回该图书的**全部**库存行（「图书 + 库位」每行一份）。
     *
     * <p>⚠️ <b>刻意绕开 {@code @Version} 与实体更新</b>，用定列 UPDATE：
     * {@code safety_qty} 是预警扫描**算出来的派生值**，不是业务写入的库存数量。
     * 若走 {@code Stock} 实体更新，MP 会把 {@code version} 一起 +1，
     * 于是「一次后台扫描」会把当天所有并发的库存单据判成乐观锁冲突（409）——
     * 为一个派生列打断正常交易是不可接受的。本语句只改 {@code safety_qty}，
     * 不碰四类数量，因此无需参与版本竞争。
     *
     * @param bookId    图书ID
     * @param safetyQty 安全库存（≥ 0）
     * @return 影响行数（该书的库存行数；无库存行时为 0）
     */
    int updateSafetyQty(@Param("bookId") Long bookId, @Param("safetyQty") Integer safetyQty);
}
