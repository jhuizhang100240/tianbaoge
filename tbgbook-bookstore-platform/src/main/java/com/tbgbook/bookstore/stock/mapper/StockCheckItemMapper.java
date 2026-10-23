package com.tbgbook.bookstore.stock.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.stock.domain.StockCheckItem;

/**
 * 库存盘点明细 Mapper 接口。
 *
 * @author tbgbook
 */
public interface StockCheckItemMapper extends BaseMapper<StockCheckItem>
{
    /**
     * 查询盘点明细列表。
     *
     * <p>筛选条件与契约一致：{@code checkId} / {@code bookId} / {@code isbn} /
     * {@code diffType} / {@code isPeriodDiff}。
     *
     * @param item 查询条件
     * @return 明细集合
     */
    List<StockCheckItem> selectStockCheckItemList(StockCheckItem item);

    /**
     * 统计「账面数不为 0 但尚未录入实盘数」的行数。
     *
     * <p>结束盘点前的必查项：没数完就结束，差异报告是假的。
     *
     * <p>⚠️ 「未录入」的判定是 {@code check_time IS NULL}，不是
     * {@code actual_qty = 0} —— 后者分不清「数了确实是 0」与「还没数」
     * （见 {@link StockCheckItem} 的类注释）。
     *
     * <p>契约的措辞是「账面数不为 0 但未录入实盘数」，故这里带了
     * {@code book_qty <> 0} 条件：账面为 0 的行本来就没有可核对的东西。
     *
     * @param checkId 盘点单ID
     * @return 未录入行数
     */
    int countUnentered(@Param("checkId") Long checkId);

    /**
     * 按盘点单主键删除其全部明细（主从级联删除的「先删明细」一步）。
     *
     * @param checkIds 单据头主键数组
     * @return 删除行数
     */
    int deleteByCheckIds(@Param("checkIds") Long[] checkIds);
}
