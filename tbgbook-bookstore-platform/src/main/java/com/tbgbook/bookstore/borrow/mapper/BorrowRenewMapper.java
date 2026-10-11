package com.tbgbook.bookstore.borrow.mapper;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.borrow.domain.BorrowRenew;

/**
 * 借阅续借记录 Mapper（只读）。
 *
 * @author tbgbook
 */
public interface BorrowRenewMapper extends BaseMapper<BorrowRenew>
{
    /**
     * 查询续借记录列表。
     *
     * @param renew 查询条件
     * @return 记录集合
     */
    List<BorrowRenew> selectBorrowRenewList(BorrowRenew renew);
}
