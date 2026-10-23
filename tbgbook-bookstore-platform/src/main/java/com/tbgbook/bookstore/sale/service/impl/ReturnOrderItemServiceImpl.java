package com.tbgbook.bookstore.sale.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.sale.domain.ReturnOrder;
import com.tbgbook.bookstore.sale.domain.ReturnOrderItem;
import com.tbgbook.bookstore.sale.mapper.ReturnOrderItemMapper;
import com.tbgbook.bookstore.sale.service.IReturnOrderItemService;
import com.tbgbook.bookstore.sale.service.IReturnOrderService;

import lombok.RequiredArgsConstructor;

/**
 * 退货单明细 Service 实现（阶段四步骤⑤）。
 *
 * <p><b>三条口径</b>（都是本次自定，已登记 `plan/backend-plan.md` 进度日志）：
 * <ol>
 *   <li><b>只有「1待审核」的父单能增删改明细</b>：有原单退货提交即完成，其明细是凭证</li>
 *   <li><b>退款额不接受客户端传值</b>（NFR-S9）：按各行「定价 × 数量」在父单总额内比例分摊，
 *       最后一行用「总额减前面各行」兜底，避免差一分钱</li>
 *   <li>书名 / ISBN / 单价一律按 {@code bookId} 从**图书主档**取，不信客户端</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class ReturnOrderItemServiceImpl extends ServiceImpl<ReturnOrderItemMapper, ReturnOrderItem>
        implements IReturnOrderItemService
{
    private final ReferenceGuard referenceGuard;

    private final BookMapper bookMapper;

    private final IReturnOrderService returnOrderService;

    @Override
    public List<ReturnOrderItem> selectReturnOrderItemList(ReturnOrderItem item)
    {
        return baseMapper.selectReturnOrderItemList(item);
    }

    @Override
    public ReturnOrderItem selectReturnOrderItemById(Long itemId)
    {
        return referenceGuard.requireExists(baseMapper, itemId, "退货单明细");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addReturnOrderItem(ReturnOrderItem item)
    {
        ReturnOrder parent = returnOrderService.requireEditable(item.getReturnId(), "新增明细");
        if (item.getBookId() == null)
        {
            throw BizErrors.badRequest("退货明细的图书不能为空");
        }
        Book book = referenceGuard.requireExists(bookMapper, item.getBookId(), "图书");
        requireQty(item);
        requireConditionMatchesStockTo(item);

        item.setItemId(null);
        item.setOrderItemId(0L);
        item.setUsedBookId(item.getUsedBookId() == null ? 0L : item.getUsedBookId());
        item.setIsbn(book.getIsbn());
        item.setTitle(book.getTitle());
        item.setUnitPrice(nz(book.getPrice()));
        item.setBuyQty(0);
        item.setRefundAmount(BigDecimal.ZERO);

        int rows = baseMapper.insert(item);
        returnOrderService.recalcTotals(parent.getReturnId());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateReturnOrderItem(ReturnOrderItem item)
    {
        if (item.getItemId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的退货单明细");
        }
        ReturnOrderItem current = referenceGuard.requireExists(baseMapper, item.getItemId(), "退货单明细");
        returnOrderService.requireEditable(current.getReturnId(), "修改明细");
        requireQty(item);
        requireConditionMatchesStockTo(item);

        // 只接受「数量 / 图书状况 / 库存去向」：书名、单价、退款额都是服务端算出来的
        ReturnOrderItem patch = new ReturnOrderItem();
        patch.setItemId(item.getItemId());
        patch.setReturnQty(item.getReturnQty());
        patch.setBookCondition(item.getBookCondition());
        patch.setStockTo(item.getStockTo());
        int rows = baseMapper.updateById(patch);
        referenceGuard.requireAffected(rows, "退货单明细");
        returnOrderService.recalcTotals(current.getReturnId());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeReturnOrderItems(Long[] itemIds)
    {
        List<Long> idList = (itemIds == null) ? List.of() : java.util.Arrays.asList(itemIds);
        var ids = referenceGuard.requireIds(idList, "退货单明细");
        Long returnId = null;
        for (var id : ids)
        {
            ReturnOrderItem one = referenceGuard.requireExists(baseMapper, id, "退货单明细");
            Long rid = one.getReturnId();
            returnOrderService.requireEditable(rid, "删除明细");
            // 一行明细只属于一张单；跨单批量删除无意义，显式拒绝以免删出半截
            if (returnId != null && !Objects.equals(returnId, rid))
            {
                throw BizErrors.badRequest("一次只能删除同一张退货单下的明细");
            }
            returnId = rid;
        }
        int rows = baseMapper.deleteByIds(ids);
        if (returnId != null)
        {
            returnOrderService.recalcTotals(returnId);
        }
        return rows;
    }

    private void requireQty(ReturnOrderItem item)
    {
        if (item.getReturnQty() == null || item.getReturnQty() <= 0)
        {
            throw BizErrors.ruleViolated("退货数量必须大于 0");
        }
    }

    /** 与 `ReturnOrderServiceImpl#requireConditionMatchesStockTo` 同一规则（有损不能回可售） */
    private void requireConditionMatchesStockTo(ReturnOrderItem item)
    {
        if (Objects.equals(item.getBookCondition(), ReturnOrderItem.CONDITION_DAMAGED)
                && Objects.equals(item.getStockTo(), ReturnOrderItem.STOCK_TO_AVAILABLE))
        {
            throw BizErrors.ruleViolated("图书状况为「有损」时，库存去向不能是「回退可售」——"
                    + "残次品重新上架会被卖出去；请改为「转报损」");
        }
    }

    private BigDecimal nz(BigDecimal v)
    {
        return (v == null) ? BigDecimal.ZERO : v;
    }
}
