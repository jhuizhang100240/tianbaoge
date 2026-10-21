package com.tbgbook.bookstore.app.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tbgbook.bookstore.app.domain.request.RecycleApplyItem;
import com.tbgbook.bookstore.app.domain.request.RecycleApplyRequest;
import com.tbgbook.bookstore.app.security.AppContext;
import com.tbgbook.bookstore.app.service.IAppRecycleService;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.service.IMemberService;
import com.tbgbook.bookstore.usedbook.domain.ConditionGrade;
import com.tbgbook.bookstore.usedbook.domain.Recycle;
import com.tbgbook.bookstore.usedbook.domain.RecycleItem;
import com.tbgbook.bookstore.usedbook.mapper.ConditionGradeMapper;
import com.tbgbook.bookstore.usedbook.mapper.RecycleMapper;
import com.tbgbook.bookstore.usedbook.service.IUsedBookRecycleService;

import lombok.RequiredArgsConstructor;

/**
 * 会员端二手回收 Service 实现。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class AppRecycleServiceImpl implements IAppRecycleService
{
    private static final long STORE_ID = 1L;

    private static final Pattern ISBN = Pattern.compile("^\\d{10}(\\d{3})?$|^\\d{9}[Xx]$");

    private final IUsedBookRecycleService recycleService;

    private final RecycleMapper recycleMapper;

    private final ConditionGradeMapper gradeMapper;

    private final BookMapper bookMapper;

    private final IMemberService memberService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> apply(RecycleApplyRequest request)
    {
        Long memberId = AppContext.requireMemberId();
        if (request.getAppointmentDate().isBefore(LocalDate.now()))
        {
            throw BizErrors.ruleViolated("预约日期不能早于今天");
        }
        Member member = memberService.selectMemberById(memberId);
        Recycle recycle = new Recycle();
        recycle.setStoreId(STORE_ID);
        recycle.setMemberId(memberId);
        recycle.setMemberName(member.getNickname());
        recycle.setAppointmentDate(request.getAppointmentDate());
        recycle.setAppointmentSlot(request.getAppointmentSlot() == null ? "" : request.getAppointmentSlot());
        recycle.setRemark(request.getRemark() == null ? "" : request.getRemark());
        List<RecycleItem> items = new ArrayList<>();
        for (RecycleApplyItem line : request.getItems())
        {
            if (line.getIsbn() == null || !ISBN.matcher(line.getIsbn().trim()).matches())
            {
                throw BizErrors.ruleViolated("ISBN 格式非法");
            }
            Book book = bookMapper.selectOne(new LambdaQueryWrapper<Book>()
                    .eq(Book::getIsbn, line.getIsbn().trim()).eq(Book::getDelFlag, "0").last("limit 1"));
            if (book == null)
            {
                throw BizErrors.ruleViolated("该 ISBN 对应图书未建档，无法给出预估（可先到店由店员现场估价）");
            }
            ConditionGrade grade = gradeMapper.selectById(line.getSubmitGradeId());
            if (grade == null || !"0".equals(grade.getStatus()) || !"0".equals(grade.getDelFlag()))
            {
                throw BizErrors.ruleViolated("成色等级不可用");
            }
            BigDecimal base = book.getPrice() == null ? BigDecimal.ZERO : book.getPrice();
            BigDecimal factor = grade.getPriceFactor() == null ? BigDecimal.ONE : grade.getPriceFactor();
            RecycleItem item = new RecycleItem();
            item.setIsbn(book.getIsbn());
            item.setTitle(StringUtils.hasText(line.getTitle()) ? line.getTitle() : book.getTitle());
            item.setSubmitGradeId(grade.getGradeId());
            item.setSubmitGradeName(grade.getGradeName());
            item.setJudgeGradeId(0L);
            item.setJudgeGradeName("");
            item.setIsMismatch(0);
            item.setJudgeNote("");
            item.setBasePrice(base);
            item.setPriceFactor(factor);
            item.setFinalPrice(base.multiply(factor).setScale(2, RoundingMode.HALF_UP));
            item.setItemStatus(1);
            item.setRejectReason("");
            item.setUsedBookId(0L);
            item.setCreateTime(LocalDateTime.now());
            items.add(item);
        }
        recycle.setItems(items);
        recycleService.addRecycle(recycle);
        Recycle saved = recycleService.selectRecycleById(recycle.getRecycleId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recycleId", saved.getRecycleId());
        data.put("recycleNo", saved.getRecycleNo());
        data.put("estimatedAmount", saved.getEstimatedAmount());
        data.put("items", saved.getItems());
        data.put("appointmentDate", saved.getAppointmentDate());
        data.put("status", saved.getStatus());
        return data;
    }

    @Override
    public Map<String, Object> list(Integer status)
    {
        Recycle query = new Recycle();
        query.setMemberId(AppContext.requireMemberId());
        query.setStatus(status);
        List<Recycle> list = recycleService.selectRecycleList(query);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        return data;
    }

    @Override
    public Map<String, Object> detail(Long recycleId)
    {
        Recycle recycle = recycleService.selectRecycleById(recycleId);
        if (recycle == null)
        {
            throw BizErrors.notFound("回收单", recycleId);
        }
        if (!AppContext.requireMemberId().equals(recycle.getMemberId()))
        {
            throw BizErrors.forbidden("回收单不属于当前会员");
        }
        return Map.of("recycle", recycle);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancel(Long recycleId, String reason)
    {
        Recycle recycle = recycleService.selectRecycleById(recycleId);
        if (recycle == null)
        {
            throw BizErrors.notFound("回收单", recycleId);
        }
        if (!AppContext.requireMemberId().equals(recycle.getMemberId()))
        {
            throw BizErrors.forbidden("回收单不属于当前会员");
        }
        if (!Integer.valueOf(1).equals(recycle.getStatus()))
        {
            throw BizErrors.ruleViolated("回收单已进入验收流程或已完成");
        }
        Recycle patch = new Recycle();
        patch.setRecycleId(recycleId);
        patch.setStatus(5);
        patch.setRemark((recycle.getRemark() == null ? "" : recycle.getRemark() + "；")
                + (StringUtils.hasText(reason) ? reason : "会员取消"));
        recycleMapper.updateById(patch);
        return Map.of("recycleId", recycleId, "status", 5);
    }
}
