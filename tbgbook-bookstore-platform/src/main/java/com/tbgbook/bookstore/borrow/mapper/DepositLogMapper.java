package com.tbgbook.bookstore.borrow.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.borrow.domain.DepositLog;

/**
 * 借阅押金流水 Mapper（只增不删）。
 *
 * @author tbgbook
 */
public interface DepositLogMapper extends BaseMapper<DepositLog>
{
    /**
     * 查询押金流水列表。
     *
     * @param log 查询条件
     * @return 流水集合
     */
    List<DepositLog> selectDepositLogList(DepositLog log);
}
