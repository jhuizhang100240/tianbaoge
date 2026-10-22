package com.tbgbook.bookstore.app.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tbgbook.bookstore.app.domain.request.ReserveSubmitRequest;
import com.tbgbook.bookstore.app.security.AppContext;
import com.tbgbook.bookstore.app.service.IAppTextbookService;
import com.tbgbook.bookstore.book.domain.TextbookList;
import com.tbgbook.bookstore.book.domain.TextbookListItem;
import com.tbgbook.bookstore.book.mapper.TextbookListItemMapper;
import com.tbgbook.bookstore.book.mapper.TextbookListMapper;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.sale.domain.TextbookReserve;
import com.tbgbook.bookstore.sale.service.ITextbookReserveService;

import lombok.RequiredArgsConstructor;

/**
 * 会员端教材预订 Service 实现（复用后台预订状态机与锁库存）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class AppTextbookServiceImpl implements IAppTextbookService
{
    private static final long STORE_ID = 1L;

    private final TextbookListMapper listMapper;

    private final TextbookListItemMapper itemMapper;

    private final ITextbookReserveService reserveService;

    @Override
    public Map<String, Object> lists(String term, String major, String college, Long listId)
    {
        LambdaQueryWrapper<TextbookList> wrapper = new LambdaQueryWrapper<TextbookList>()
                .eq(TextbookList::getStoreId, STORE_ID)
                .eq(TextbookList::getStatus, 2)
                .eq(StringUtils.hasText(term), TextbookList::getTerm, term)
                .eq(StringUtils.hasText(major), TextbookList::getMajor, major)
                .eq(StringUtils.hasText(college), TextbookList::getCollege, college)
                .eq(listId != null, TextbookList::getListId, listId)
                .orderByDesc(TextbookList::getPublishTime);
        List<TextbookList> lists = listMapper.selectList(wrapper);
        List<Map<String, Object>> items = List.of();
        if (listId != null)
        {
            if (lists.isEmpty())
            {
                throw BizErrors.notFound("已发布教材清单", listId);
            }
            items = itemMapper.selectList(new LambdaQueryWrapper<TextbookListItem>()
                    .eq(TextbookListItem::getListId, listId)
                    .orderByAsc(TextbookListItem::getItemId)).stream()
                    .map(this::itemMap).toList();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lists", lists);
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(ReserveSubmitRequest request)
    {
        Long memberId = AppContext.requireMemberId();
        TextbookListItem item = itemMapper.selectById(request.getListItemId());
        if (item == null)
        {
            throw BizErrors.notFound("教材清单明细", request.getListItemId());
        }
        TextbookList list = listMapper.selectById(item.getListId());
        if (list == null || !Integer.valueOf(2).equals(list.getStatus()))
        {
            throw BizErrors.ruleViolated("教材清单未发布或已结束");
        }
        if (list.getEndTime() != null && LocalDateTime.now().isAfter(list.getEndTime()))
        {
            throw BizErrors.ruleViolated("该教材清单已结束");
        }
        if (item.getRemainQty() != null && request.getQty() > item.getRemainQty())
        {
            throw BizErrors.ruleViolated("预订数量超过剩余可订数");
        }
        TextbookReserve reserve = new TextbookReserve();
        reserve.setStoreId(STORE_ID);
        reserve.setMemberId(memberId);
        reserve.setListId(item.getListId());
        reserve.setListItemId(item.getItemId());
        reserve.setBookId(item.getBookId());
        reserve.setQty(request.getQty());
        reserveService.addTextbookReserve(reserve);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reserveId", reserve.getReserveId());
        data.put("reserveNo", reserve.getReserveNo());
        data.put("pickupCode", reserve.getPickupCode());
        data.put("totalAmount", reserve.getTotalAmount());
        data.put("status", reserve.getStatus());
        data.put("lockTime", reserve.getLockTime());
        return data;
    }

    @Override
    public Map<String, Object> myList(Integer status)
    {
        TextbookReserve query = new TextbookReserve();
        query.setMemberId(AppContext.requireMemberId());
        query.setStatus(status);
        List<TextbookReserve> list = reserveService.selectTextbookReserveList(query);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        return data;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancel(Long reserveId, String reason)
    {
        Long memberId = AppContext.requireMemberId();
        TextbookReserve reserve = reserveService.selectTextbookReserveById(reserveId);
        if (reserve == null)
        {
            throw BizErrors.notFound("教材预订", reserveId);
        }
        if (!memberId.equals(reserve.getMemberId()))
        {
            throw BizErrors.forbidden("预订不属于当前会员");
        }
        reserveService.cancel(reserveId, reason);
        return Map.of("reserveId", reserveId, "status", 5);
    }

    private Map<String, Object> itemMap(TextbookListItem item)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", item.getItemId());
        data.put("listId", item.getListId());
        data.put("bookId", item.getBookId());
        data.put("isbn", item.getIsbn());
        data.put("title", item.getTitle());
        data.put("price", item.getPrice());
        data.put("reserveQty", item.getReserveQty());
        data.put("arrivedQty", item.getArrivedQty());
        data.put("remainQty", item.getRemainQty());
        data.put("status", item.getStatus());
        return data;
    }
}
