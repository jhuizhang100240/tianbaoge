package com.tbgbook.bookstore.stock.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.stock.domain.LossApplyItem;

/**
 * 报损申请明细 Mapper 接口。
 *
 * @author tbgbook
 */
public interface LossApplyItemMapper extends BaseMapper<LossApplyItem>
{
    /**
     * 查询报损申请明细列表。
     *
     * @param item 查询条件
     * @return 明细集合
     */
    List<LossApplyItem> selectLossApplyItemList(LossApplyItem item);

    /**
     * 按单据头主键删除其全部明细（主从级联删除的「先删明细」一步）。
     *
     * <p>本表**没有 {@code del_flag}**，是物理删除。
     *
     * @param applyIds 单据头主键数组
     * @return 删除行数
     */
    int deleteByApplyIds(@Param("applyIds") Long[] applyIds);
}
