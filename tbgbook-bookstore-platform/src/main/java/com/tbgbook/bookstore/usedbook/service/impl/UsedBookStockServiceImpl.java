package com.tbgbook.bookstore.usedbook.service.impl;

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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.bookstore.usedbook.domain.ConditionGrade;
import com.tbgbook.bookstore.usedbook.domain.OffShelfRequest;
import com.tbgbook.bookstore.usedbook.domain.RecycleItem;
import com.tbgbook.bookstore.usedbook.domain.ShelfRequest;
import com.tbgbook.bookstore.usedbook.domain.ShelfResult;
import com.tbgbook.bookstore.usedbook.domain.UsedBook;
import com.tbgbook.bookstore.usedbook.mapper.UsedBookMapper;
import com.tbgbook.bookstore.usedbook.service.IUsedBookGradeService;
import com.tbgbook.bookstore.usedbook.service.IUsedBookStockService;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 二手书库存 Service 实现（阶段六第一批，页面 A-42）。
 *
 * <p><b>自定口径（登记 A44）</b>：
 * <ol>
 *   <li><b>副本条码</b>：{@code BC + yyyyMMdd + 6 位流水}（流水取当前最大 used_book_id + 1，
 *       避开 {@code information_schema} 缓存问题；与收银端「BC 前缀 = 二手册」的既有约定一致）；
 *       <b>SKU</b>：{@code UB + 8 位零填充副本ID}，与正价书 SKU（图书编码）天然隔离；</li>
 *   <li><b>手工补录</b>：{@code POST} 仅用于存量建档，落库时 {@code stockStatus=4下架}
 *       （未上架状态），须走 {@code /shelf} 才上架；{@code recycleId}/{@code recycleItemId} 传 0；</li>
 *   <li><b>库存状态不接受直接写入</b>：PUT 只改基础信息（ISBN / 书名 / 作者 / 出版社 / 封面 /
 *       成色 / 原价 / 库位 / 备注），状态与时间戳一律由动作端点维护；</li>
 *   <li><b>删除保护</b>：{@code stockStatus in (2锁定, 3已售)} 一律 422；
 *       另检查 {@code biz_order_item.used_book_id} 是否引用（被引用禁删）；仅「未上架 / 已下架」
 *       且无订单引用的副本可物理删除；</li>
 *   <li><b>上架校验</b>：售价必填且 &gt; 0（422）；**售价不得高于原价**（422）；
 *       {@code stockStatus=3已售 / 4下架} 中**已售** 409；已下架可直接重新上架；
 *       成色必须存在（404）；上架置 {@code 1在售} + {@code shelfTime}；</li>
 *   <li><b>下架</b>：原因必填；已售出 409；锁定中 422（正在被订单占用，需先释放）。</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class UsedBookStockServiceImpl extends ServiceImpl<UsedBookMapper, UsedBook>
        implements IUsedBookStockService
{
    /** 副本条码前缀（BC = 二手册，与收银端 {@code USED_BARCODE_PREFIX} 一致） */
    private static final String BARCODE_PREFIX = "BC";

    /** SKU 前缀（UB = Used Book） */
    private static final String SKU_PREFIX = "UB";

    /** 单店部署固定门店ID */
    private static final long DEFAULT_STORE_ID = 1L;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReferenceGuard referenceGuard;

    private final IUsedBookGradeService gradeService;

    private final BookMapper bookMapper;

    @Override
    public List<UsedBook> selectUsedBookList(UsedBook usedBook)
    {
        return baseMapper.selectUsedBookList(usedBook);
    }

    @Override
    public UsedBook selectUsedBookById(Long usedBookId)
    {
        return referenceGuard.requireExists(baseMapper, usedBookId, "二手书库存");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addUsedBook(UsedBook usedBook)
    {
        if (usedBook.getIsbn() == null || usedBook.getIsbn().isBlank())
        {
            throw BizErrors.badRequest("ISBN 不能为空");
        }
        if (usedBook.getTitle() == null || usedBook.getTitle().isBlank())
        {
            throw BizErrors.badRequest("书名不能为空");
        }
        ConditionGrade grade = requireGrade(usedBook.getConditionGradeId(),
                usedBook.getConditionGradeName());
        LocalDateTime now = LocalDateTime.now();
        usedBook.setUsedBookId(null);
        usedBook.setStoreId(usedBook.getStoreId() == null ? DEFAULT_STORE_ID : usedBook.getStoreId());
        usedBook.setBarCode(generateBarCode());
        // SKU 依赖副本ID，落库后按真实主键回填
        usedBook.setSkuCode("");
        usedBook.setRecycleId(0L);
        usedBook.setRecycleItemId(0L);
        usedBook.setBookId(usedBook.getBookId() == null ? 0L : usedBook.getBookId());
        usedBook.setIsbn(usedBook.getIsbn().trim());
        usedBook.setTitle(usedBook.getTitle().trim());
        usedBook.setAuthor(usedBook.getAuthor() == null ? "" : usedBook.getAuthor().trim());
        usedBook.setPublisherName(
                usedBook.getPublisherName() == null ? "" : usedBook.getPublisherName().trim());
        usedBook.setCoverUrl(usedBook.getCoverUrl() == null ? "" : usedBook.getCoverUrl().trim());
        usedBook.setConditionGradeId(grade.getGradeId());
        usedBook.setConditionGradeName(grade.getGradeName());
        usedBook.setOriginPrice(nzd(usedBook.getOriginPrice()));
        usedBook.setUsedPrice(BigDecimal.ZERO);
        usedBook.setCostPrice(BigDecimal.ZERO);
        usedBook.setLocationId(usedBook.getLocationId() == null ? 0L : usedBook.getLocationId());
        // 手工补录一律落「未上架」，须走上架动作
        usedBook.setStockStatus(UsedBook.STATUS_OFF_SHELF);
        usedBook.setLockOrderId(0L);
        usedBook.setLockTime(null);
        usedBook.setShelfTime(null);
        usedBook.setSoldTime(null);
        usedBook.setRemark(usedBook.getRemark() == null ? "" : usedBook.getRemark());
        usedBook.setCreateBy(currentUsername());
        usedBook.setCreateTime(now);
        usedBook.setUpdateBy(currentUsername());
        usedBook.setUpdateTime(now);
        return insertWithGeneratedCodes(usedBook);
    }

    /**
     * {@inheritDoc}
     *
     * <p>由回收验收合格明细生成副本：条码 / SKU 由服务端生成、落库为未上架；
     * 若该 ISBN 已建档则回填作者 / 出版社 / 封面 / 原价，未建档则留空。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFromRecycle(RecycleItem item, Long recycleId, Long recycleItemId,
            BigDecimal costPrice)
    {
        ConditionGrade grade = gradeService.requireGrade(item.getJudgeGradeId(), null);
        Book book = bookMapper.selectOne(new LambdaQueryWrapper<Book>()
                .eq(Book::getIsbn, item.getIsbn())
                .last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        UsedBook usedBook = new UsedBook();
        usedBook.setStoreId(DEFAULT_STORE_ID);
        usedBook.setBarCode(generateBarCode());
        usedBook.setSkuCode("");
        usedBook.setRecycleId(recycleId);
        usedBook.setRecycleItemId(recycleItemId);
        usedBook.setBookId(book == null ? 0L : book.getBookId());
        usedBook.setIsbn(item.getIsbn());
        usedBook.setTitle(item.getTitle());
        usedBook.setAuthor(book == null || book.getAuthor() == null ? "" : book.getAuthor());
        usedBook.setPublisherName(
                book == null || book.getPublisherName() == null ? "" : book.getPublisherName());
        usedBook.setCoverUrl(book == null || book.getCoverUrl() == null ? "" : book.getCoverUrl());
        usedBook.setConditionGradeId(grade.getGradeId());
        usedBook.setConditionGradeName(grade.getGradeName());
        usedBook.setOriginPrice(book == null ? BigDecimal.ZERO : nzd(book.getPrice()));
        usedBook.setUsedPrice(BigDecimal.ZERO);
        usedBook.setCostPrice(nzd(costPrice));
        usedBook.setLocationId(0L);
        usedBook.setStockStatus(UsedBook.STATUS_OFF_SHELF);
        usedBook.setLockOrderId(0L);
        usedBook.setRemark("回收验收生成（回收单ID=" + recycleId + "）");
        usedBook.setCreateBy(currentUsername());
        usedBook.setCreateTime(now);
        usedBook.setUpdateBy(currentUsername());
        usedBook.setUpdateTime(now);
        insertWithGeneratedCodes(usedBook);
        return usedBook.getUsedBookId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateUsedBook(UsedBook usedBook)
    {
        if (usedBook.getUsedBookId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的二手书副本");
        }
        UsedBook existing = referenceGuard.requireExists(baseMapper, usedBook.getUsedBookId(),
                "二手书库存");
        if (Integer.valueOf(UsedBook.STATUS_SOLD).equals(existing.getStockStatus()))
        {
            throw BizErrors.ruleViolated("该副本已售出，不能修改基础信息");
        }
        if (usedBook.getConditionGradeId() != null)
        {
            ConditionGrade grade = gradeService.requireGrade(usedBook.getConditionGradeId(), null);
            usedBook.setConditionGradeName(grade.getGradeName());
        }
        else
        {
            usedBook.setConditionGradeId(existing.getConditionGradeId());
            usedBook.setConditionGradeName(existing.getConditionGradeName());
        }
        if (usedBook.getIsbn() == null)
        {
            usedBook.setIsbn(existing.getIsbn());
        }
        if (usedBook.getTitle() == null)
        {
            usedBook.setTitle(existing.getTitle());
        }
        if (usedBook.getAuthor() == null)
        {
            usedBook.setAuthor(existing.getAuthor());
        }
        if (usedBook.getPublisherName() == null)
        {
            usedBook.setPublisherName(existing.getPublisherName());
        }
        if (usedBook.getCoverUrl() == null)
        {
            usedBook.setCoverUrl(existing.getCoverUrl());
        }
        if (usedBook.getOriginPrice() == null)
        {
            usedBook.setOriginPrice(existing.getOriginPrice());
        }
        if (usedBook.getLocationId() == null)
        {
            usedBook.setLocationId(existing.getLocationId());
        }
        if (usedBook.getRemark() == null)
        {
            usedBook.setRemark(existing.getRemark());
        }
        // 服务端接管字段
        usedBook.setStoreId(existing.getStoreId());
        usedBook.setBarCode(existing.getBarCode());
        usedBook.setSkuCode(existing.getSkuCode());
        usedBook.setRecycleId(existing.getRecycleId());
        usedBook.setRecycleItemId(existing.getRecycleItemId());
        usedBook.setBookId(existing.getBookId());
        usedBook.setUsedPrice(existing.getUsedPrice());
        usedBook.setCostPrice(existing.getCostPrice());
        usedBook.setStockStatus(existing.getStockStatus());
        usedBook.setLockOrderId(existing.getLockOrderId());
        usedBook.setLockTime(existing.getLockTime());
        usedBook.setShelfTime(existing.getShelfTime());
        usedBook.setSoldTime(existing.getSoldTime());
        usedBook.setCreateBy(existing.getCreateBy());
        usedBook.setCreateTime(existing.getCreateTime());
        usedBook.setUpdateBy(currentUsername());
        usedBook.setUpdateTime(LocalDateTime.now());
        return baseMapper.updateById(usedBook);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeUsedBooks(Long[] usedBookIds)
    {
        List<Long> idList = (usedBookIds == null) ? Collections.emptyList() : Arrays.asList(usedBookIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "二手书库存");
        for (Serializable id : ids)
        {
            UsedBook row = referenceGuard.requireExists(baseMapper, id, "二手书库存");
            if (Integer.valueOf(UsedBook.STATUS_LOCKED).equals(row.getStockStatus()))
            {
                throw BizErrors.ruleViolated("该副本已被订单锁定，不能删除（usedBookId=" + id + "）");
            }
            if (Integer.valueOf(UsedBook.STATUS_SOLD).equals(row.getStockStatus()))
            {
                throw BizErrors.ruleViolated("该副本已售出，需要留痕不能删除（usedBookId=" + id + "）");
            }
            referenceGuard.requireNoReference("biz_order_item", "used_book_id", id, null,
                    "订单明细", "二手书副本", null);
        }
        // biz_used_book 无 del_flag —— 物理删除，仅对未上架 / 已下架且无订单引用的行开放
        return baseMapper.deleteByIds(ids);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShelfResult shelf(Long usedBookId, ShelfRequest request)
    {
        UsedBook usedBook = referenceGuard.requireExists(baseMapper, usedBookId, "二手书库存");
        if (request == null || request.getUsedPrice() == null)
        {
            throw BizErrors.ruleViolated("售价为空，不能上架");
        }
        BigDecimal usedPrice = request.getUsedPrice().setScale(2, RoundingMode.HALF_UP);
        if (usedPrice.signum() <= 0)
        {
            throw BizErrors.ruleViolated("售价必须大于 0");
        }
        BigDecimal originPrice = nzd(usedBook.getOriginPrice());
        if (originPrice.signum() > 0 && usedPrice.compareTo(originPrice) > 0)
        {
            throw BizErrors.ruleViolated("售价 " + usedPrice + " 高于原价 " + originPrice + "，不能上架");
        }
        if (Integer.valueOf(UsedBook.STATUS_SOLD).equals(usedBook.getStockStatus()))
        {
            throw BizErrors.conflict("该副本已售出，不能上架");
        }
        if (Integer.valueOf(UsedBook.STATUS_LOCKED).equals(usedBook.getStockStatus()))
        {
            throw BizErrors.conflict("该副本已被订单锁定，不能上架");
        }
        gradeService.requireGrade(usedBook.getConditionGradeId(), null);

        LocalDateTime now = LocalDateTime.now();
        UsedBook patch = new UsedBook();
        patch.setUsedBookId(usedBookId);
        patch.setUsedPrice(usedPrice);
        patch.setStockStatus(UsedBook.STATUS_ON_SALE);
        patch.setShelfTime(now);
        if (request.getLocationId() != null)
        {
            patch.setLocationId(request.getLocationId());
        }
        patch.setUpdateBy(currentUsername());
        patch.setUpdateTime(now);
        baseMapper.updateById(patch);

        ShelfResult result = new ShelfResult();
        result.setUsedBookId(usedBookId);
        result.setBarCode(usedBook.getBarCode());
        result.setUsedPrice(usedPrice);
        result.setOriginPrice(originPrice);
        result.setDiscountRate(originPrice.signum() == 0 ? BigDecimal.ZERO.setScale(4)
                : usedPrice.divide(originPrice, 4, RoundingMode.HALF_UP));
        result.setStockStatus(UsedBook.STATUS_ON_SALE);
        result.setShelfTime(now);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int offShelf(Long usedBookId, OffShelfRequest request)
    {
        UsedBook usedBook = referenceGuard.requireExists(baseMapper, usedBookId, "二手书库存");
        if (request == null || request.getReason() == null || request.getReason().isBlank())
        {
            throw BizErrors.ruleViolated("下架原因未填");
        }
        if (Integer.valueOf(UsedBook.STATUS_SOLD).equals(usedBook.getStockStatus()))
        {
            throw BizErrors.conflict("该副本已售出，不能下架");
        }
        if (Integer.valueOf(UsedBook.STATUS_LOCKED).equals(usedBook.getStockStatus()))
        {
            throw BizErrors.ruleViolated("该副本已被订单锁定，不能下架；请先释放锁定");
        }
        LocalDateTime now = LocalDateTime.now();
        UsedBook patch = new UsedBook();
        patch.setUsedBookId(usedBookId);
        patch.setStockStatus(UsedBook.STATUS_OFF_SHELF);
        patch.setRemark(mergeRemark(usedBook.getRemark(), "下架：" + request.getReason().trim()));
        patch.setUpdateBy(currentUsername());
        patch.setUpdateTime(now);
        return baseMapper.updateById(patch);
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /** 成色必须存在且启用（回收明细 / 库存都要求可用的档位） */
    private ConditionGrade requireGrade(Long gradeId, String gradeName)
    {
        if (gradeId == null)
        {
            throw BizErrors.badRequest("成色等级ID不能为空");
        }
        ConditionGrade grade = gradeService.requireGrade(gradeId, null);
        if ("1".equals(grade.getStatus()))
        {
            throw BizErrors.ruleViolated("成色等级「" + grade.getGradeName()
                    + "」已停用，不能用于建档");
        }
        return grade;
    }

    /** 副本条码：BC + yyyyMMdd + 6 位流水（取当前最大 used_book_id + 1） */
    private String generateBarCode()
    {
        Long maxId = baseMapper.selectList(new LambdaQueryWrapper<UsedBook>()
                .select(UsedBook::getUsedBookId)
                .orderByDesc(UsedBook::getUsedBookId)
                .last("limit 1")).stream()
                .map(UsedBook::getUsedBookId).findFirst().orElse(0L);
        long next = (maxId == null ? 0L : maxId) + 1;
        return BARCODE_PREFIX + LocalDate.now().format(DATE_FORMAT)
                + String.format("%06d", next % 1000000);
    }

    /** 插入副本并按真实主键回填 SKU（条码需在插入前给出，故先预生成） */
    private int insertWithGeneratedCodes(UsedBook usedBook)
    {
        baseMapper.insert(usedBook);
        String sku = SKU_PREFIX + String.format("%08d", usedBook.getUsedBookId());
        UsedBook patch = new UsedBook();
        patch.setUsedBookId(usedBook.getUsedBookId());
        patch.setSkuCode(sku);
        baseMapper.updateById(patch);
        usedBook.setSkuCode(sku);
        return 1;
    }

    private String mergeRemark(String oldOne, String newOne)
    {
        if (newOne == null || newOne.isBlank())
        {
            return oldOne == null ? "" : oldOne;
        }
        return oldOne == null || oldOne.isBlank() ? newOne.trim() : oldOne + "；" + newOne.trim();
    }

    private BigDecimal nzd(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
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
