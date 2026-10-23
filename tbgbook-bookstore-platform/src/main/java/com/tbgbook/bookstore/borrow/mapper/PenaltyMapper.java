package com.tbgbook.bookstore.borrow.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.borrow.domain.Penalty;

/**
 * 罚金与赔偿单 Mapper。
 *
 * @author tbgbook
 */
public interface PenaltyMapper extends BaseMapper<Penalty>
{
    /**
     * 查询罚金单列表。
     *
     * @param penalty 查询条件
     * @return 单据集合
     */
    List<Penalty> selectPenaltyList(Penalty penalty);

    /** 该会员未结清罚金合计（payStatus in 1,3） */
    BigDecimal selectUnpaidTotal(@Param("memberId") Long memberId);
}
