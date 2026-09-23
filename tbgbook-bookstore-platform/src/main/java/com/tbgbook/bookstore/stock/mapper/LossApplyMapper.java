package com.tbgbook.bookstore.stock.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.stock.domain.LossApply;

/**
 * 报损申请单 Mapper 接口。
 *
 * @author tbgbook
 */
public interface LossApplyMapper extends BaseMapper<LossApply>
{
    /**
     * 查询报损申请单列表。
     *
     * @param apply 查询条件
     * @return 单据集合
     */
    List<LossApply> selectLossApplyList(LossApply apply);

    /**
     * 取 {@code biz_loss_apply} 的下一个自增主键（用于预生成 applyNo）。
     *
     * <p>{@code apply_no} 是 {@code NOT NULL} 且**无默认值**、还带唯一索引
     * （{@code uk_apply_no}），因此不能「先插入再回填」—— 手法同
     * {@code StockInMapper#selectNextInId}。
     *
     * @return 预估的下一个主键值
     */
    Long selectNextApplyId();
}
