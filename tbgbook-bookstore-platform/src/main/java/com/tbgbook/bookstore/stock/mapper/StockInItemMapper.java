package com.tbgbook.bookstore.stock.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.stock.domain.StockInItem;

/**
 * 采购入库单明细 Mapper 接口。
 *
 * @author tbgbook
 */
public interface StockInItemMapper extends BaseMapper<StockInItem>
{
    /**
     * 查询采购入库单明细列表。
     *
     * @param item 查询条件
     * @return 明细集合
     */
    List<StockInItem> selectStockInItemList(StockInItem item);

    /**
     * 按单据头主键删除其全部明细（主从级联删除的「先删明细」一步）。
     *
     * <p>本表**没有 {@code del_flag}**，是物理删除 —— 单据草稿删除时明细本就该随之消失。
     *
     * @param inIds 单据头主键数组
     * @return 删除行数
     */
    int deleteByInIds(@Param("inIds") Long[] inIds);
}
