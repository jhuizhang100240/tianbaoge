package com.tbgbook.bookstore.borrow.service;

import java.util.List;

import com.tbgbook.bookstore.borrow.domain.BorrowReserve;
import com.tbgbook.bookstore.borrow.domain.ReserveNotifyResult;

/**
 * 借阅预约 Service 接口（阶段五步骤④第二批，FR-4.5.6）。
 *
 * <p>对已借出图书发起预约；归还后按队列序号通知；取消后队列顺延。
 *
 * @author tbgbook
 */
public interface IReserveService
{
    /**
     * 查询预约列表。
     *
     * @param reserve 查询条件
     * @return 预约集合
     */
    List<BorrowReserve> selectReserveList(BorrowReserve reserve);

    /**
     * 查询预约详情。
     *
     * @param reserveId 预约ID
     * @return 预约；不存在时抛 404
     */
    BorrowReserve selectReserveById(Long reserveId);

    /**
     * 新增预约（同一会员同一图书不允许重复排队；同店唯一）。
     *
     * @param reserve 预约
     * @return 影响行数
     */
    int addReserve(BorrowReserve reserve);

    /**
     * 修改预约（仅备注可改；状态流转走通知 / 取消动作）。
     *
     * @param reserve 预约
     * @return 影响行数
     */
    int updateReserve(BorrowReserve reserve);

    /**
     * 删除预约（仅「已取消 / 已过期」可删，留痕）。
     *
     * @param reserveIds 预约主键数组
     * @return 影响行数
     */
    int removeReserves(Long[] reserveIds);

    /**
     * 到书通知（排队中 → 已到书待取，设置取书截止时间）。
     *
     * @param reserveId 预约ID
     * @return 通知结果（含下一位预约单号）
     */
    ReserveNotifyResult notifyReserve(Long reserveId);

    /**
     * 取消预约（队列顺序重排，后续预约人序号前移）。
     *
     * @param reserveId 预约ID
     * @param reason    取消原因（可空）
     * @return 影响行数
     */
    int cancelReserve(Long reserveId, String reason);
}
