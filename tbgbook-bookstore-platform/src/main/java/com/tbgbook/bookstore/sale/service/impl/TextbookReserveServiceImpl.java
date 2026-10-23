package com.tbgbook.bookstore.sale.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.domain.TextbookListItem;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.book.mapper.TextbookListItemMapper;
import com.tbgbook.bookstore.book.service.ITextbookListService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.sale.domain.PickupRequest;
import com.tbgbook.bookstore.sale.domain.PickupResult;
import com.tbgbook.bookstore.sale.domain.TextbookReserve;
import com.tbgbook.bookstore.sale.mapper.TextbookReserveMapper;
import com.tbgbook.bookstore.sale.service.ITextbookReserveService;
import com.tbgbook.bookstore.sale.support.StockLockSupport;

import lombok.RequiredArgsConstructor;

/**
 * 教材预订 Service 实现（阶段四步骤⑥）。
 *
 * <p><b>不收定金</b>（§4.3 规则 7，2026-09-21 已确认）：预订只锁库存，到店核销时按
 * 实际取书数量结算付款 —— 故本类不生成订单，order_id 保持 0
 * （契约明确「0 表示仅锁库存未支付」）。收银台按实取数量另行结算，那是收银员的动作。
 *
 * <p><b>lockedQty 的语义是「累计已取书数量」</b>（DDL 与接口文档都这么写），
 * 不是「已锁定数量」—— 列名有歧义，但两个来源一致，故照此实现。
 *
 * <p><b>挂清单的预订要回写清单明细</b>：教材清单明细的 pickedQty 注释写明
 * 「由取书核销累减（阶段四）」，而清单的 pickedQty 统计列又由明细聚合 ——
 * 两边都在同一事务内，否则 A-12 列表会显示旧数字。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class TextbookReserveServiceImpl extends ServiceImpl<TextbookReserveMapper, TextbookReserve>
        implements ITextbookReserveService
{
    /** 预订单号前缀（需求未定义，本次自定）：YD + yyyyMMdd + 6 位流水 */
    private static final String RESERVE_NO_PREFIX = "YD";

    /** 取货码长度 */
    private static final int PICKUP_CODE_LEN = 8;

    private static final int SCALE = 2;

    private final ReferenceGuard referenceGuard;

    private final BookMapper bookMapper;

    private final TextbookListItemMapper listItemMapper;

    private final ITextbookListService textbookListService;

    private final StockLockSupport stockLock;

    @Override
    public List<TextbookReserve> selectTextbookReserveList(TextbookReserve reserve)
    {
        return baseMapper.selectTextbookReserveList(reserve);
    }

    @Override
    public TextbookReserve selectTextbookReserveById(Long reserveId)
    {
        return referenceGuard.requireExists(baseMapper, reserveId, "教材预订");
    }

    // ==================================================================
    // 新增 / 修改 / 删除
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addTextbookReserve(TextbookReserve reserve)
    {
        if (reserve.getQty() == null || reserve.getQty() <= 0)
        {
            throw BizErrors.ruleViolated("预订数量必须大于 0");
        }
        if (reserve.getBookId() == null)
        {
            throw BizErrors.badRequest("预订必须指定图书");
        }
        Book book = referenceGuard.requireExists(bookMapper, reserve.getBookId(), "图书");
        requireListItem(reserve, book);

        reserve.setReserveId(null);
        reserve.setReserveNo(null);
        reserve.setLockedQty(0);
        reserve.setOrderId(0L);
        reserve.setStatus(TextbookReserve.STATUS_ARRIVED);
        reserve.setIsbn(book.getIsbn());
        reserve.setTitle(book.getTitle());
        reserve.setUnitPrice(nz(book.getPrice()));
        reserve.setTotalAmount(reserve.getUnitPrice()
                .multiply(BigDecimal.valueOf(reserve.getQty())).setScale(SCALE, RoundingMode.HALF_UP));
        LocalDateTime now = LocalDateTime.now();
        reserve.setReserveTime(now);
        reserve.setLockTime(now);
        reserve.setPickupTime(null);
        reserve.setCancelTime(null);
        reserve.setNotifyTime(null);
        reserve.setArriveTime(null);
        reserve.setPickupCode(buildPickupCode());

        Long predictedId = baseMapper.selectNextReserveId();
        reserve.setReserveNo(buildReserveNo(predictedId == null ? 1L : predictedId));
        int rows = baseMapper.insert(reserve);
        correctNo(reserve);
        stockLock.lock(reserve.getBookId(), reserve.getQty(), reserve.getReserveNo(), "教材预订锁库存");
        return rows;
    }

    /**
     * {@inheritDoc}
     *
     * <p>只接受数量的变更，且数量变化同步调整锁定库存：多了补锁、少了放回。
     * 图书不允许改（换书等于换一张单）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateTextbookReserve(TextbookReserve reserve)
    {
        if (reserve.getReserveId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的教材预订");
        }
        TextbookReserve current = referenceGuard.requireExists(baseMapper, reserve.getReserveId(), "教材预订");
        requireEditable(current, "修改");

        int oldQty = nzi(current.getQty());
        int newQty = (reserve.getQty() == null) ? oldQty : reserve.getQty();
        if (newQty <= 0)
        {
            throw BizErrors.ruleViolated("预订数量必须大于 0");
        }
        int delta = newQty - oldQty;
        if (delta > 0)
        {
            stockLock.lock(current.getBookId(), delta, current.getReserveNo(), "教材预订追加锁库存");
        }
        else if (delta < 0)
        {
            stockLock.unlock(current.getBookId(), -delta, current.getReserveNo(), "教材预订减量释放");
        }

        TextbookReserve patch = new TextbookReserve();
        patch.setReserveId(current.getReserveId());
        patch.setQty(newQty);
        patch.setTotalAmount(nz(current.getUnitPrice())
                .multiply(BigDecimal.valueOf(newQty)).setScale(SCALE, RoundingMode.HALF_UP));
        int rows = baseMapper.updateById(patch);
        referenceGuard.requireAffected(rows, "教材预订");
        return rows;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeTextbookReserves(Long[] reserveIds)
    {
        List<Long> idList = (reserveIds == null) ? Collections.emptyList() : Arrays.asList(reserveIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "教材预订");
        for (Serializable id : ids)
        {
            TextbookReserve one = referenceGuard.requireExists(baseMapper, id, "教材预订");
            requireEditable(one, "删除");
            int outstanding = nzi(one.getQty()) - nzi(one.getLockedQty());
            if (outstanding > 0)
            {
                stockLock.unlock(one.getBookId(), outstanding, one.getReserveNo(), "删除预订释放锁库存");
            }
        }
        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 取书核销 / 取消
    // ==================================================================

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PickupResult pickup(Long reserveId, PickupRequest request)
    {
        TextbookReserve reserve = referenceGuard.requireExists(baseMapper, reserveId, "教材预订");
        if (Objects.equals(reserve.getStatus(), TextbookReserve.STATUS_CANCELED))
        {
            throw BizErrors.conflict("预订「" + reserve.getReserveNo() + "」已取消，不能取书");
        }
        if (Objects.equals(reserve.getStatus(), TextbookReserve.STATUS_DONE))
        {
            throw BizErrors.conflict("预订「" + reserve.getReserveNo() + "」已取完，不能重复取书");
        }
        String given = request.getPickupCode();
        if (given != null && !given.isBlank() && !given.trim().equals(nzStr(reserve.getPickupCode())))
        {
            throw BizErrors.ruleViolated("取货码不匹配，请核对后重试");
        }

        int qty = nzi(reserve.getQty());
        int picked = nzi(reserve.getLockedQty());
        int remain = qty - picked;
        int take = nzi(request.getQty());
        if (take > remain)
        {
            throw BizErrors.ruleViolated("本次取书 " + take + " 册超过剩余数量 " + remain + " 册");
        }

        List<String> stockAfter = stockLock.unlock(reserve.getBookId(), take, reserve.getReserveNo(),
                "教材取书核销");

        int pickedAfter = picked + take;
        TextbookReserve patch = new TextbookReserve();
        patch.setReserveId(reserveId);
        patch.setLockedQty(pickedAfter);
        patch.setStatus(pickedAfter >= qty ? TextbookReserve.STATUS_DONE : TextbookReserve.STATUS_PARTIAL);
        patch.setPickupTime(LocalDateTime.now());
        referenceGuard.requireAffected(baseMapper.updateById(patch), "教材预订");

        writeBackListItem(reserve, take);

        PickupResult result = new PickupResult();
        result.setReserveId(reserveId);
        result.setReserveNo(reserve.getReserveNo());
        result.setLockedQty(pickedAfter);
        result.setQty(qty);
        result.setRemainQty(qty - pickedAfter);
        result.setStatus(patch.getStatus());
        result.setStockAfter(stockAfter);
        result.setPickupTime(patch.getPickupTime());
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancel(Long reserveId, String reason)
    {
        TextbookReserve reserve = referenceGuard.requireExists(baseMapper, reserveId, "教材预订");
        if (Objects.equals(reserve.getStatus(), TextbookReserve.STATUS_CANCELED))
        {
            throw BizErrors.conflict("预订「" + reserve.getReserveNo() + "」已取消");
        }
        if (Objects.equals(reserve.getStatus(), TextbookReserve.STATUS_DONE))
        {
            throw BizErrors.conflict("预订「" + reserve.getReserveNo() + "」已取完，不能取消");
        }
        int picked = nzi(reserve.getLockedQty());
        if (picked > 0)
        {
            throw BizErrors.ruleViolated("预订「" + reserve.getReserveNo() + "」已取 " + picked
                    + " 册，不可取消（取消会放回已取走的库存）");
        }

        stockLock.unlock(reserve.getBookId(), nzi(reserve.getQty()), reserve.getReserveNo(),
                "取消预订释放锁库存");

        TextbookReserve patch = new TextbookReserve();
        patch.setReserveId(reserveId);
        patch.setStatus(TextbookReserve.STATUS_CANCELED);
        patch.setCancelTime(LocalDateTime.now());
        int rows = baseMapper.updateById(patch);
        referenceGuard.requireAffected(rows, "教材预订");
        if (nzl(reserve.getListId()) > 0)
        {
            textbookListService.refreshStatistics(reserve.getListId());
        }
        return rows;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 只放行「2待到店取书」——3/4 已取过、5 已取消，都不该改 */
    private void requireEditable(TextbookReserve reserve, String action)
    {
        if (!Objects.equals(reserve.getStatus(), TextbookReserve.STATUS_ARRIVED))
        {
            throw BizErrors.ruleViolated("预订「" + reserve.getReserveNo() + "」当前为「"
                    + statusName(reserve.getStatus()) + "」，只有「2待到店取书」的可以" + action);
        }
    }

    /** 清单与清单明细的存在性、一致性校验，并挡「预订量超过到货数」 */
    private TextbookListItem requireListItem(TextbookReserve reserve, Book book)
    {
        Long listItemId = nzl(reserve.getListItemId());
        if (listItemId <= 0)
        {
            return null;
        }
        TextbookListItem item = referenceGuard.requireExists(listItemMapper, listItemId, "教材清单明细");
        if (nzl(reserve.getListId()) > 0 && !Objects.equals(item.getListId(), reserve.getListId()))
        {
            throw BizErrors.ruleViolated("清单明细「" + item.getTitle() + "」不属于所选清单");
        }
        if (!Objects.equals(item.getBookId(), book.getBookId()))
        {
            throw BizErrors.ruleViolated("清单明细「" + item.getTitle() + "」与所选图书不是同一本");
        }
        reserve.setListId(item.getListId());

        int arrived = nzi(item.getArrivedQty());
        if (arrived > 0)
        {
            int already = baseMapper.sumActiveQtyByListItem(listItemId, null);
            int want = nzi(reserve.getQty());
            if (already + want > arrived)
            {
                throw BizErrors.ruleViolated("清单明细「" + item.getTitle() + "」到货 " + arrived
                        + " 册、已被预订 " + already + " 册，本次 " + want + " 册超额");
            }
        }
        return item;
    }

    /** 取书后回写清单明细的已取数与状态（清单统计列的唯一维护点） */
    private void writeBackListItem(TextbookReserve reserve, int take)
    {
        Long listItemId = nzl(reserve.getListItemId());
        if (listItemId <= 0)
        {
            return;
        }
        TextbookListItem item = listItemMapper.selectById(listItemId);
        if (item == null)
        {
            return;
        }
        int pickedAfter = nzi(item.getPickedQty()) + take;
        int arrived = nzi(item.getArrivedQty());
        TextbookListItem patch = new TextbookListItem();
        patch.setItemId(listItemId);
        patch.setPickedQty(pickedAfter);
        patch.setRemainQty(arrived - pickedAfter);
        patch.setStatus(deriveItemStatus(arrived, pickedAfter));
        listItemMapper.updateById(patch);
        textbookListService.refreshStatistics(item.getListId());
    }

    /** 与教材清单明细同一套状态口径（1待到货 2已到货待取书 3部分取书 4已取完） */
    private int deriveItemStatus(int arrivedQty, int pickedQty)
    {
        if (arrivedQty <= 0)
        {
            return 1;
        }
        if (pickedQty <= 0)
        {
            return 2;
        }
        if (pickedQty < arrivedQty)
        {
            return 3;
        }
        return 4;
    }

    /** 取货码：8 位，去掉了易混淆的 0 O 1 I */
    private String buildPickupCode()
    {
        final String alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder(PICKUP_CODE_LEN);
        for (int i = 0; i < PICKUP_CODE_LEN; i++)
        {
            sb.append(alphabet.charAt(ThreadLocalRandom.current().nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private void correctNo(TextbookReserve reserve)
    {
        String actual = buildReserveNo(reserve.getReserveId());
        if (!actual.equals(reserve.getReserveNo()))
        {
            TextbookReserve patch = new TextbookReserve();
            patch.setReserveId(reserve.getReserveId());
            patch.setReserveNo(actual);
            baseMapper.updateById(patch);
            reserve.setReserveNo(actual);
        }
    }

    private String buildReserveNo(Long reserveId)
    {
        return RESERVE_NO_PREFIX + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%06d", reserveId);
    }

    private String statusName(Integer status)
    {
        if (status == null)
        {
            return "未知";
        }
        return switch (status)
        {
            case TextbookReserve.STATUS_WAITING -> "1待到货";
            case TextbookReserve.STATUS_ARRIVED -> "2待到店取书";
            case TextbookReserve.STATUS_PARTIAL -> "3部分取书";
            case TextbookReserve.STATUS_DONE -> "4已取完";
            case TextbookReserve.STATUS_CANCELED -> "5已取消";
            default -> "未知";
        };
    }

    private BigDecimal nz(BigDecimal v)
    {
        return (v == null) ? BigDecimal.ZERO : v;
    }

    private int nzi(Integer v)
    {
        return (v == null) ? 0 : v;
    }

    private long nzl(Long v)
    {
        return (v == null) ? 0L : v;
    }

    private String nzStr(String v)
    {
        return (v == null) ? "" : v;
    }
}
