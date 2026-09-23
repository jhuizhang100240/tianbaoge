package com.tbgbook.bookstore.stock.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.stock.domain.StockWarning;
import com.tbgbook.bookstore.stock.domain.WarningHandleRequest;
import com.tbgbook.bookstore.stock.domain.WarningHandleResult;
import com.tbgbook.bookstore.stock.domain.WarningScanResult;

/**
 * 库存预警 Service 接口（页面 A-23 库存预警中心）。
 *
 * <p><b>预警由系统产出，只提供两条人可用的写路径</b>：
 * {@link #scan}（立即扫一次，与定时任务同一套逻辑）与 {@link #handle}（改处置状态）。
 * 没有手工新增 / 修改 / 删除 —— 预警是「系统对账面的判断」，
 * 手工造一条或改掉它的数字都会让判断失去依据。
 *
 * @author tbgbook
 */
public interface IStockWarningService extends IService<StockWarning>
{
    /**
     * 查询库存预警列表。
     *
     * @param warning 查询条件
     * @return 预警集合
     */
    List<StockWarning> selectStockWarningList(StockWarning warning);

    /**
     * 查询库存预警详情。
     *
     * @param warningId 预警ID
     * @return 预警；不存在时抛 404
     */
    StockWarning selectStockWarningById(Long warningId);

    /**
     * 执行一次预警扫描（四类）。
     *
     * <p><b>与定时任务「库存预警扫描」（每日 06:00）是同一套逻辑</b>，
     * 只是触发方式不同 —— 定时任务在阶段十接上 Quartz，调的就是本方法。
     *
     * <p>顺带把算出的**安全库存写回** `biz_stock.safety_qty`（§3.2 库存规则 2 的公式），
     * 否则「安全库存」这一列永远是 0、补货预警也就无从判定。
     *
     * <p><b>去重口径</b>：同一图书同一类型若已存在**待处理**的预警，就地刷新数值不新增行；
     * 已处理 / 已忽略的视为已销项，会重新生成一条新的。
     *
     * @param warningType 只扫描指定类型（1~4），null 表示四类全扫
     * @return 扫描图书数 / 新生成数 / 就地更新数
     * @throws com.tbgbook.common.exception.ServiceException 422 预警类型不在 1~4
     */
    WarningScanResult scan(Integer warningType);

    /**
     * 处置预警（置为已处理 / 已忽略）。
     *
     * <p>处置**补货类**（1补货 / 4零库存）预警时可顺带开一张**采购入库单草稿**，
     * 减少重复录入 —— 草稿按预警的 {@code bookId} 与 {@code suggestQty} 开单、
     * 不填库位（落 {@code locationId=0} 未分配库位），库位与实收数量由 A-16 开单页补齐。
     * 转单与处置在**同一事务**内完成：转单失败则处置也不生效。
     *
     * <p>滞销与临期预警**不允许转采购单**（对滞销的书下采购单是反的），返回 422。
     *
     * @param request 处置请求
     * @return 处置结果（含转单信息，若有）
     * @throws com.tbgbook.common.exception.ServiceException 404 预警不存在；
     *         409 该预警已处置；422 处理状态取值非法 / 该类型不支持转采购单
     */
    WarningHandleResult handle(WarningHandleRequest request);
}
