package com.tbgbook.bookstore.borrow.service;

import java.time.LocalDate;
import java.util.List;

import com.tbgbook.bookstore.borrow.domain.BorrowRecord;
import com.tbgbook.bookstore.borrow.domain.BorrowRenew;
import com.tbgbook.bookstore.borrow.domain.BorrowStatView;
import com.tbgbook.bookstore.borrow.domain.LendRequest;
import com.tbgbook.bookstore.borrow.domain.LendResult;
import com.tbgbook.bookstore.borrow.domain.OverdueNotifyRequest;
import com.tbgbook.bookstore.borrow.domain.OverdueNotifyResult;
import com.tbgbook.bookstore.borrow.domain.OverdueResult;
import com.tbgbook.bookstore.borrow.domain.RenewRequest;
import com.tbgbook.bookstore.borrow.domain.RenewResult;
import com.tbgbook.bookstore.borrow.domain.ReturnRequest;
import com.tbgbook.bookstore.borrow.domain.ReturnResult;

/**
 * 借阅单 Service 接口（阶段五步骤④，页面 A-53~A-56，FR-4.5.2 ~ FR-4.5.5 / FR-4.5.8 / FR-4.5.9）。
 *
 * <p>一单一册；借还走动作端点（{@link #lend}/{@link #returnBook}），
 * 资源 CRUD 仅供后台人工补录与维护。
 *
 * @author tbgbook
 */
public interface IBorrowRecordService
{
    // ------------------------------------------------------------------
    // 借阅单 CRUD
    // ------------------------------------------------------------------

    /**
     * 查询借阅单列表。
     *
     * @param record 查询条件
     * @return 借阅单集合
     */
    List<BorrowRecord> selectBorrowRecordList(BorrowRecord record);

    /**
     * 查询借阅单详情。
     *
     * @param borrowId 借阅单ID
     * @return 借阅单；不存在时抛 404
     */
    BorrowRecord selectBorrowRecordById(Long borrowId);

    /**
     * 新增借阅单（后台补录，不联动库存；正常借出走 {@link #lend}）。
     *
     * @param record 借阅单
     * @return 影响行数
     */
    int addBorrowRecord(BorrowRecord record);

    /**
     * 修改借阅单（仅备注与取书库位可改；其余字段服务端接管）。
     *
     * @param record 借阅单
     * @return 影响行数
     */
    int updateBorrowRecord(BorrowRecord record);

    /**
     * 删除借阅单（**仅「无任何流转痕迹」的误录单**可删；已归还 / 已逾期 / 有罚金单一律 422）。
     *
     * @param borrowIds 借阅单主键数组
     * @return 影响行数
     */
    int removeBorrowRecords(Long[] borrowIds);

    // ------------------------------------------------------------------
    // 借出 / 归还 / 续借
    // ------------------------------------------------------------------

    /**
     * 借书办理（资格四道校验 + 库存联动 + 单位押金快照）。
     *
     * @param request 借书请求
     * @return 借阅结果
     */
    LendResult lend(LendRequest request);

    /**
     * 还书办理（逾期判定 + 罚金计算 + 库存回补 + 按时归还成长值）。
     *
     * @param request 还书请求
     * @return 还书结果
     */
    ReturnResult returnBook(ReturnRequest request);

    /**
     * 续借办理（次数上限 + 无他人预约 + 未逾期）。
     *
     * @param borrowId 借阅单ID
     * @param request  续借请求
     * @return 续借结果
     */
    RenewResult renew(Long borrowId, RenewRequest request);

    /**
     * 查询续借记录列表。
     *
     * @param renew 查询条件
     * @return 续借记录集合
     */
    List<BorrowRenew> selectBorrowRenewList(BorrowRenew renew);

    /**
     * 查询续借记录详情。
     *
     * @param renewId 续借ID
     * @return 续借记录；不存在时抛 404
     */
    BorrowRenew selectBorrowRenewById(Long renewId);

    // ------------------------------------------------------------------
    // 逾期与统计
    // ------------------------------------------------------------------

    /**
     * 逾期清单与罚金试算。
     *
     * @param memberId       限定会员（可空）
     * @param minOverdueDays 最小逾期天数（可空）
     * @param status         借阅状态（可空，默认在借 / 已逾期）
     * @return 逾期清单与汇总
     */
    OverdueResult overdueList(Long memberId, Integer minOverdueDays, Integer status);

    /**
     * 批量发送催还提醒（站内信 + 微信订阅消息，逐条记失败原因）。
     *
     * @param request 催还请求
     * @return 发送结果
     */
    OverdueNotifyResult notifyOverdue(OverdueNotifyRequest request);

    /**
     * 借阅统计（趋势 / 热门图书 / 周期分布 / 逾期率 / 等级占比）。
     *
     * @param startDate   开始日期
     * @param endDate     结束日期
     * @param granularity 粒度 day/week/month
     * @return 统计视图
     */
    BorrowStatView statistics(LocalDate startDate, LocalDate endDate, String granularity);
}
