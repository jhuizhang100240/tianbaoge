package com.tbgbook.bookstore.stock.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.stock.domain.StockCheck;
import com.tbgbook.bookstore.stock.domain.StockCheckDiffReport;
import com.tbgbook.bookstore.stock.domain.StockCheckGenerateRequest;
import com.tbgbook.bookstore.stock.domain.StockCheckItemsRequest;
import com.tbgbook.bookstore.stock.domain.StockCheckRecheckResult;

/**
 * 库存盘点单 Service 接口（阶段三步骤③，页面 A-19~A-22）。
 *
 * <p><b>盘点要达成的不变量</b>：结束盘点后被盘图书的「在场实物量 == 实盘数」。
 * 口径定义见 {@code StockCheckItem} 的类注释（在场实物量 = 可售 + 锁定 + 残损，不含借出）。
 *
 * <p><b>状态机</b>：{@code 1进行中 → 2待复核 / 3已完成}、{@code → 4已作废}。
 * 差异率超 `biz.stocktake.diff.rate`（默认 2%）时进 2 待复核且**不应用差异**
 * （§3.2 库存规则 4：需二次盘点）。
 *
 * @author tbgbook
 */
public interface IStockCheckService extends IService<StockCheck>
{
    /**
     * 查询盘点单列表。
     *
     * @param stockCheck 查询条件
     * @return 单据集合
     */
    List<StockCheck> selectStockCheckList(StockCheck stockCheck);

    /**
     * 查询盘点单详情（**含明细**）。
     *
     * <p>⚠️ **盲盘（{@code showBookQty=0}）时明细的账面数字段被置 null**——
     * 否则前端拿到响应就等于看到了账面数，盲盘失去意义。
     *
     * @param checkId 单据ID
     * @return 单据；不存在时抛 404
     */
    StockCheck selectStockCheckById(Long checkId);

    /**
     * 新增盘点单（手写明细，不走范围生成）。
     *
     * <p>与 {@link #generate} 的区别：本方法是标准 CRUD，明细由客户端给；
     * 范围生成会**自己解析范围并快照账面数**。日常开单应走 {@link #generate}。
     *
     * @param stockCheck 单据（含 items）
     * @return 影响行数
     */
    int addStockCheck(StockCheck stockCheck);

    /**
     * 修改盘点单。**只有进行中可改**（已应用的差异不能再改）。
     *
     * @param stockCheck 单据
     * @return 影响行数
     */
    int updateStockCheck(StockCheck stockCheck);

    /**
     * 删除盘点单（**只允许进行中或已作废**，主从级联）。
     *
     * <p>已完成/待复核的单据不能删：它的明细是那次盘点的唯一证据。
     *
     * @param checkIds 单据主键数组
     * @return 实际删除行数
     */
    int removeStockChecks(Long[] checkIds);

    /**
     * 按范围生成盘点单并**快照账面数**（FR-4.2.6）。
     *
     * <p>三种范围：按分类（**含子分类**）/ 按库位 / 指定图书。
     * 只纳入**有库存行**的「图书 + 库位」组合 —— 没有库存行就没有账面数可对。
     *
     * @param request 生成请求
     * @return 新建的盘点单（含明细）
     * @throws com.tbgbook.common.exception.ServiceException 422 范围内没有库存记录；
     *         409 范围内已存在进行中的盘点单
     */
    StockCheck generate(StockCheckGenerateRequest request);

    /**
     * 批量录入实盘数（**增量提交**，可多次调用，按 {@code itemId} 覆盖）。
     *
     * <p>差异三列由服务端算；录入时写 {@code checkTime} 作为「已录入」标记。
     * **盲盘模式下响应里的账面数被置 null**。
     *
     * @param checkId 单据ID
     * @param request 实盘明细
     * @return 更新后的单据（含重算的统计列）
     * @throws com.tbgbook.common.exception.ServiceException 404 单据不存在；
     *         422 单据不是进行中 / 提交了非本单的明细行
     */
    StockCheck entryItems(Long checkId, StockCheckItemsRequest request);

    /**
     * 结束盘点并**应用差异**。
     *
     * <p>差异率 ≤ 阈值（或 {@code forceFinish=true}）时：逐行把差异应用到库存
     * （盘盈回补可售、盘亏冲减可售），写库存流水（`biz_type=7 盘盈` / `8 盘亏`），
     * 单据置 {@code 3已完成}。<br>
     * 差异率 > 阈值且未强制时：置 {@code 2待复核} + {@code needRecheck=1}，
     * **不应用差异**（等复核，§3.2 库存规则 4）。
     *
     * <p>应用差异前会**重新判一次盘点期差异**：若某行图书的当前在场实物量已不等于
     * 生成时的快照，说明盘点期间发生了出入库 —— 该行标 {@code isPeriodDiff=1}，
     * 且**应用的是「实盘数 − 当前在场量」而不是「实盘数 − 快照账面数」**，
     * 否则会把盘点期间那笔变动重复计一次（详见实现里的注释）。
     *
     * @param checkId     单据ID
     * @param forceFinish 差异率超标时是否强制结束
     * @return 更新后的单据
     * @throws com.tbgbook.common.exception.ServiceException 422 未录入完 / 差异行缺原因 /
     *         单据不是进行中
     */
    StockCheck finish(Long checkId, Boolean forceFinish);

    /**
     * 盘点差异报告（A-22）。
     *
     * <p>两类差异分开返回：{@code diffItems}（盘点人责任）与
     * {@code periodDiffItems}（盘点期差异，单独列出，§4.2 规则 5）。
     *
     * @param checkId 单据ID
     * @return 差异报告
     */
    StockCheckDiffReport diffReport(Long checkId);

    /**
     * 对差异品种发起二次盘点：新建一张只含上次差异行的盘点单（§3.2 库存规则 4）。
     *
     * @param checkId   原单据ID
     * @param checkType 新单类型，null 取 2 抽盘
     * @return 新单 id/no 与原单号
     * @throws com.tbgbook.common.exception.ServiceException 422 原单未处于待复核状态
     */
    StockCheckRecheckResult recheck(Long checkId, Integer checkType);

    /**
     * 重算单据头的统计列（品种数 / 总量 / 盈亏 / 差异率）。
     *
     * <p>**为什么公开**：明细侧的增删改（{@code IStockCheckItemService}）在写完明细后
     * 必须触发一次重算，否则「在明细页加一行、列表页的品种数与差异率还是旧的」。
     * 与其在明细服务里复制一份统计逻辑（两份迟早不一致），不如把入口开出来。
     *
     * @param checkId 单据ID
     */
    void refreshStats(Long checkId);

    /**
     * 读差异率阈值（`sys_config.biz.stocktake.diff.rate`，默认 2）。
     *
     * <p>NFR-M4/R7：阈值必须由运营可配，不得写死在 Java 里。走
     * {@code ISysConfigService}（带 Redis 缓存）。
     *
     * @return 阈值（百分数，如 2 表示 2%）
     */
    java.math.BigDecimal diffRateThreshold();
}
