package com.tbgbook.bookstore.stock.service;

import java.util.List;

import com.tbgbook.bookstore.stock.domain.CategoryStock;
import com.tbgbook.bookstore.stock.domain.ScanResult;
import com.tbgbook.bookstore.stock.domain.StockOverview;

/**
 * 库存总览 服务层（页面 A-14 的三个动作端点）。
 *
 * @author tbgbook
 */
public interface IInventoryOverviewService
{
    /**
     * 库存总览统计（四类合计 + 品种数 + 库存总金额 + 四类预警数量）。
     *
     * <p>库存总金额是**成本口径**，无 {@code inventory:cost:view} 时置 {@code null}
     * 而不是报错（FR-4.6 规则 5）。
     *
     * @param categoryId 限定分类，可空
     * @param locationId 限定库位，可空
     * @return 总览
     */
    StockOverview summary(Long categoryId, Long locationId);

    /**
     * 分类库存分布（饼图）。
     *
     * <p>占比优先按**金额**算；金额不可用时（无 {@code inventory:cost:view}，或成本价全为 0
     * —— 例如刚上线还没发生过入库）退化为按**实物总量**算，避免占比全 0 导致饼图空白。
     *
     * @param locationId 限定库位，可空
     * @return 每个分类一行，按金额倒序
     */
    List<CategoryStock> byCategory(Long locationId);

    /**
     * 扫码查书：按 ISBN / 图书编号查库存与库位，**逐库位展开**。
     *
     * @param code ISBN（可含连字符）或图书编号（{@code BK...}）
     * @return 扫码结果；未命中抛 404
     */
    ScanResult scan(String code);
}
