package com.tbgbook.bookstore.borrow.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.borrow.domain.BorrowReserve;

/**
 * 借阅预约 Mapper。
 *
 * @author tbgbook
 */
public interface BorrowReserveMapper extends BaseMapper<BorrowReserve>
{
    /**
     * 查询预约列表。
     *
     * @param reserve 查询条件
     * @return 预约集合
     */
    List<BorrowReserve> selectBorrowReserveList(BorrowReserve reserve);

    /** 该图书下一个队列序号 = max(queue_no)+1 */
    Integer selectNextQueueNo(@Param("bookId") Long bookId);

    /** 取消 / 过期后，后续排队序号前移 */
    int shiftQueueAfter(@Param("bookId") Long bookId, @Param("queueNo") Integer queueNo);
}
