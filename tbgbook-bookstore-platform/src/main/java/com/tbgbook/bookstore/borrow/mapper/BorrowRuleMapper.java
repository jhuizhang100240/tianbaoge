package com.tbgbook.bookstore.borrow.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.borrow.domain.BorrowRule;

/**
 * 借阅全局规则 Mapper（仅列表查询走 XML）。
 *
 * @author tbgbook
 */
public interface BorrowRuleMapper extends BaseMapper<BorrowRule>
{
    /**
     * 查询借阅规则列表（可按状态与创建时间筛选）。
     *
     * @param rule 查询条件
     * @return 规则集合
     */
    List<BorrowRule> selectBorrowRuleList(BorrowRule rule);
}
