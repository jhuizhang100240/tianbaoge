package com.tbgbook.bookstore.stock.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.stock.domain.StockWarning;
import com.tbgbook.bookstore.stock.domain.WarningCandidate;

/**
 * 库存预警 Mapper 接口。
 *
 * @author tbgbook
 */
public interface StockWarningMapper extends BaseMapper<StockWarning>
{
    /**
     * 查询库存预警列表。
     *
     * @param warning 查询条件
     * @return 预警集合
     */
    List<StockWarning> selectStockWarningList(StockWarning warning);

    /**
     * 取**全部待处理**预警（供扫描做「不重复生成」的比对）。
     *
     * <p>只取待处理的（{@code handle_status=1}）：已处理 / 已忽略的视为已销项，
     * 扫描命中时会重新生成一条新的。故这里不需要把已处置的也捞出来。
     *
     * @return 待处理预警集合（只填主键与 bookId/warningType 够用，但整行返回便于扩展）
     */
    List<StockWarning> selectPendingWarnings();

    /**
     * 一次取回判定四类预警所需的全部原始数据（**每本书一行**）。
     *
     * <p>刻意做成「一次查」而不是逐书回查：扫描要遍历全店图书，
     * 逐书发 3 条 SQL 在门店规模上就是几万次往返（N+1）。
     *
     * <p>四个 LEFT JOIN 的含义：
     * <ol>
     *   <li>{@code s} —— 本书的库存聚合（可售 / 四类之和）。没有库存行的书补 0</li>
     *   <li>{@code f} —— 本书的流水聚合：近 30 天销售出库量、最后销售时间、首条流水时间。
     *       **只用 `biz_stock_flow` 而不用 `biz_order_item`**：销售域（阶段四）的订单表
     *       按 `biz_type=2 销售出库` 写库存流水，故流水表已经完整承载了「动销」这件事；
     *       改从订单表取会引入对阶段四的依赖，而依赖方向应当反过来（详见类注释）</li>
     *   <li>{@code e} —— 引用本书的**已发布**教材清单里**最近的未来**结束时间（临期判定依据）。
     *       只取 {@code end_time >= now()} 的：已经结束的清单不叫临期，叫过期</li>
     * </ol>
     *
     * @param since   近30天统计的起点（服务端算，= now - 30 天）
     * @param nowTime 当前时间（临期判定取未来清单用）
     * @return 每本书一行
     */
    List<WarningCandidate> selectScanCandidates(@Param("since") LocalDateTime since,
            @Param("nowTime") LocalDateTime nowTime);
}
