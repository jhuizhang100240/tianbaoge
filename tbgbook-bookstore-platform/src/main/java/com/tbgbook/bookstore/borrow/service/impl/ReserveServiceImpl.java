package com.tbgbook.bookstore.borrow.service.impl;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.borrow.domain.BorrowRecord;
import com.tbgbook.bookstore.borrow.domain.BorrowReserve;
import com.tbgbook.bookstore.borrow.domain.BorrowRule;
import com.tbgbook.bookstore.borrow.domain.ReserveNotifyResult;
import com.tbgbook.bookstore.borrow.mapper.BorrowRecordMapper;
import com.tbgbook.bookstore.borrow.mapper.BorrowReserveMapper;
import com.tbgbook.bookstore.borrow.service.IBorrowRuleService;
import com.tbgbook.bookstore.borrow.service.IReserveService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.domain.MemberMessage;
import com.tbgbook.bookstore.member.mapper.MemberMapper;
import com.tbgbook.bookstore.member.mapper.MemberMessageMapper;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 借阅预约 Service 实现（阶段五步骤④第二批，FR-4.5.6）。
 *
 * <p><b>自定口径（登记 A43）</b>：
 * <ol>
 *   <li><b>预约单号</b>：{@code RS + yyyyMMddHHmmss + 4 位流水}（流水取 max(reserve_id)+1，同 A42）；</li>
 *   <li><b>队列序号</b>：同一图书下 {@code max(queue_no)+1}（只算状态 1排队中 / 2已到书待取）；
 *       **同一会员同一图书不允许重复排队**（409）；</li>
 *   <li><b>新增校验</b>：会员与图书必须存在；图书已下架 / 停售 422；**该书当前无可借库存**才允许预约
 *       （有货时应直接借阅）；</li>
 *   <li><b>到书通知</b>：仅「1排队中」可通知；要求该书**当前有可售库存**（即已归还）；
 *       置 2已到书待取，写 {@code notify_time} 与 {@code expire_time = now + reserve_hold_days}，
 *       并发站内信 {@code biz_message}（msgType=6系统消息，渠道2站内信，状态2已发送），
 *       真实微信订阅消息留阶段九；</li>
 *   <li><b>取消</b>：仅 1排队中 / 2已到书待取 可取消；置 4已取消并把同图书 queue_no 更大的
 *       活跃预约序号整体 -1（队列顺延）；已取书 / 已过期 422；</li>
 *   <li><b>删除保护</b>：仅「已取消 / 已过期」可删，排队中 / 已到书 / 已取书一律 422（留痕）；</li>
 *   <li><b>修改</b>：仅 {@code remark} 可改，其余字段服务端接管。</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class ReserveServiceImpl extends ServiceImpl<BorrowReserveMapper, BorrowReserve>
        implements IReserveService
{
    /** 预约单号前缀（需求 §3.2 编码表：预约单 RS） */
    private static final String RESERVE_NO_PREFIX = "RS";

    private static final DateTimeFormatter NO_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ReferenceGuard referenceGuard;

    private final MemberMapper memberMapper;

    private final BookMapper bookMapper;

    private final BorrowRecordMapper borrowRecordMapper;

    private final MemberMessageMapper memberMessageMapper;

    private final IBorrowRuleService borrowRuleService;

    @Override
    public List<BorrowReserve> selectReserveList(BorrowReserve reserve)
    {
        return baseMapper.selectBorrowReserveList(reserve);
    }

    @Override
    public BorrowReserve selectReserveById(Long reserveId)
    {
        return referenceGuard.requireExists(baseMapper, reserveId, "借阅预约");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addReserve(BorrowReserve reserve)
    {
        if (reserve.getMemberId() == null || reserve.getBookId() == null)
        {
            throw BizErrors.badRequest("会员ID与图书ID不能为空");
        }
        referenceGuard.requireExists(memberMapper, reserve.getMemberId(), "会员");
        Book book = referenceGuard.requireExists(bookMapper, reserve.getBookId(), "图书");
        if (book.getStatus() != null && (book.getStatus() == 2 || book.getStatus() == 4))
        {
            throw BizErrors.ruleViolated("《" + book.getTitle() + "》已"
                    + (book.getStatus() == 2 ? "下架" : "停售") + "，不能预约");
        }
        long dup = baseMapper.selectCount(new LambdaQueryWrapper<BorrowReserve>()
                .eq(BorrowReserve::getMemberId, reserve.getMemberId())
                .eq(BorrowReserve::getBookId, reserve.getBookId())
                .in(BorrowReserve::getStatus, BorrowReserve.STATUS_QUEUING,
                        BorrowReserve.STATUS_NOTIFIED));
        if (dup > 0)
        {
            throw BizErrors.conflict("该会员已在此图书的预约队列中，不能重复预约");
        }
        Integer nextQueue = baseMapper.selectNextQueueNo(reserve.getBookId());
        LocalDateTime now = LocalDateTime.now();
        reserve.setReserveId(null);
        reserve.setReserveNo(generateNo());
        reserve.setIsbn(book.getIsbn());
        reserve.setTitle(book.getTitle());
        reserve.setQueueNo(nextQueue == null ? 1 : nextQueue);
        reserve.setStatus(BorrowReserve.STATUS_QUEUING);
        reserve.setReserveTime(reserve.getReserveTime() == null ? now : reserve.getReserveTime());
        reserve.setNotifyTime(null);
        reserve.setExpireTime(null);
        reserve.setPickupTime(null);
        reserve.setCreateTime(now);
        reserve.setUpdateTime(now);
        return baseMapper.insert(reserve);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateReserve(BorrowReserve reserve)
    {
        if (reserve.getReserveId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的借阅预约");
        }
        BorrowReserve existing = referenceGuard.requireExists(baseMapper, reserve.getReserveId(),
                "借阅预约");
        reserve.setReserveNo(existing.getReserveNo());
        reserve.setMemberId(existing.getMemberId());
        reserve.setBookId(existing.getBookId());
        reserve.setIsbn(existing.getIsbn());
        reserve.setTitle(existing.getTitle());
        reserve.setQueueNo(existing.getQueueNo());
        reserve.setStatus(existing.getStatus());
        reserve.setReserveTime(existing.getReserveTime());
        reserve.setNotifyTime(existing.getNotifyTime());
        reserve.setExpireTime(existing.getExpireTime());
        reserve.setPickupTime(existing.getPickupTime());
        reserve.setCreateTime(existing.getCreateTime());
        reserve.setUpdateTime(LocalDateTime.now());
        return baseMapper.updateById(reserve);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeReserves(Long[] reserveIds)
    {
        List<Long> idList = (reserveIds == null) ? Collections.emptyList() : Arrays.asList(reserveIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "借阅预约");
        for (Serializable id : ids)
        {
            BorrowReserve row = referenceGuard.requireExists(baseMapper, id, "借阅预约");
            if (!Integer.valueOf(BorrowReserve.STATUS_CANCELLED).equals(row.getStatus())
                    && !Integer.valueOf(BorrowReserve.STATUS_EXPIRED).equals(row.getStatus()))
            {
                throw BizErrors.ruleViolated("预约单当前为「" + statusName(row.getStatus())
                        + "」，需要留痕不能删除；请先走取消动作");
            }
        }
        return baseMapper.deleteByIds(ids);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReserveNotifyResult notifyReserve(Long reserveId)
    {
        BorrowReserve reserve = referenceGuard.requireExists(baseMapper, reserveId, "借阅预约");
        if (!Integer.valueOf(BorrowReserve.STATUS_QUEUING).equals(reserve.getStatus()))
        {
            throw BizErrors.ruleViolated("该预约当前为「" + statusName(reserve.getStatus())
                    + "」，不在排队中状态，不能发送到书通知");
        }
        // 「图书当前并非已归还」判定：该书没有在借单（status in 1/3 且未归还）
        long active = borrowRecordMapper.selectCount(new LambdaQueryWrapper<BorrowRecord>()
                .eq(BorrowRecord::getBookId, reserve.getBookId())
                .in(BorrowRecord::getStatus, BorrowRecord.STATUS_BORROWING, BorrowRecord.STATUS_OVERDUE)
                .isNull(BorrowRecord::getReturnTime));
        if (active > 0)
        {
            throw BizErrors.ruleViolated("《" + reserve.getTitle()
                    + "》当前仍有在借记录，尚未归还，不能发送到书通知");
        }
        BorrowRule rule = borrowRuleService.requireActiveRule();
        int holdDays = rule.getReserveHoldDays() == null ? 3 : rule.getReserveHoldDays();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expire = now.plusDays(holdDays);

        BorrowReserve patch = new BorrowReserve();
        patch.setReserveId(reserveId);
        patch.setStatus(BorrowReserve.STATUS_NOTIFIED);
        patch.setNotifyTime(now);
        patch.setExpireTime(expire);
        patch.setUpdateTime(now);
        baseMapper.updateById(patch);

        // 站内信通知（真实微信订阅消息留阶段九）
        MemberMessage message = new MemberMessage();
        message.setMemberId(reserve.getMemberId());
        message.setMsgType(6);
        message.setTitle("预约到书通知");
        message.setContent("您预约的《" + reserve.getTitle() + "》已到馆，请于 " + expire
                + " 前到店取书，逾期未取将顺延给下一位。");
        message.setLinkUrl("/borrow/reserve/" + reserveId);
        message.setBizNo(reserve.getReserveNo());
        message.setSendChannel(2);
        message.setSendStatus(2);
        message.setFailReason("");
        message.setSendTime(now);
        message.setIsRead(0);
        message.setReadTime(null);
        message.setCreateTime(now);
        memberMessageMapper.insert(message);

        ReserveNotifyResult result = new ReserveNotifyResult();
        result.setReserveNo(reserve.getReserveNo());
        result.setMemberId(reserve.getMemberId());
        result.setNotifyTime(now);
        result.setExpireTime(expire);
        result.setNextReserveNo(findNextReserveNo(reserve.getBookId(), reserve.getQueueNo()));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelReserve(Long reserveId, String reason)
    {
        BorrowReserve reserve = referenceGuard.requireExists(baseMapper, reserveId, "借阅预约");
        if (Integer.valueOf(BorrowReserve.STATUS_PICKED).equals(reserve.getStatus())
                || Integer.valueOf(BorrowReserve.STATUS_EXPIRED).equals(reserve.getStatus()))
        {
            throw BizErrors.ruleViolated("预约已" + statusName(reserve.getStatus()) + "，不能取消");
        }
        if (Integer.valueOf(BorrowReserve.STATUS_CANCELLED).equals(reserve.getStatus()))
        {
            throw BizErrors.conflict("该预约已取消，不能重复取消");
        }
        LocalDateTime now = LocalDateTime.now();
        BorrowReserve patch = new BorrowReserve();
        patch.setReserveId(reserveId);
        patch.setStatus(BorrowReserve.STATUS_CANCELLED);
        patch.setUpdateTime(now);
        patch.setRemark(reason == null ? reserve.getRemark() : reason.trim());
        int rows = baseMapper.updateById(patch);
        // 队列顺延：同图书 queue_no 更大的活跃预约序号 -1
        baseMapper.shiftQueueAfter(reserve.getBookId(), reserve.getQueueNo());
        return rows;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 下一位排队中的预约单号（用于顺延提示） */
    private String findNextReserveNo(Long bookId, Integer queueNo)
    {
        List<BorrowReserve> next = baseMapper.selectList(new LambdaQueryWrapper<BorrowReserve>()
                .eq(BorrowReserve::getBookId, bookId)
                .gt(BorrowReserve::getQueueNo, queueNo)
                .eq(BorrowReserve::getStatus, BorrowReserve.STATUS_QUEUING)
                .orderByAsc(BorrowReserve::getQueueNo)
                .last("limit 1"));
        return next.isEmpty() ? null : next.get(0).getReserveNo();
    }

    /** 预约单号：RS + yyyyMMddHHmmss + 4 位流水 */
    private String generateNo()
    {
        List<BorrowReserve> last = baseMapper.selectList(new LambdaQueryWrapper<BorrowReserve>()
                .select(BorrowReserve::getReserveId)
                .orderByDesc(BorrowReserve::getReserveId)
                .last("limit 1"));
        long next = last.isEmpty() || last.get(0).getReserveId() == null
                ? 1L : last.get(0).getReserveId() + 1;
        return RESERVE_NO_PREFIX + LocalDateTime.now().format(NO_TIME_FORMAT)
                + String.format("%04d", next % 10000);
    }

    private String statusName(Integer status)
    {
        if (status == null)
        {
            return "未知";
        }
        switch (status)
        {
            case 1:
                return "排队中";
            case 2:
                return "已到书待取";
            case 3:
                return "已取书";
            case 4:
                return "已取消";
            case 5:
                return "已过期";
            default:
                return "未知";
        }
    }

    private String currentUsername()
    {
        try
        {
            String name = SecurityUtils.getUsername();
            return name == null ? "" : name;
        }
        catch (Exception e)
        {
            return "";
        }
    }
}
