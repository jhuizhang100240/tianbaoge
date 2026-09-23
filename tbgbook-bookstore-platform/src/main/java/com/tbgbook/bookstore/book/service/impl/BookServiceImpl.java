package com.tbgbook.bookstore.book.service.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.domain.BookChangeLog;
import com.tbgbook.bookstore.book.domain.BookLabel;
import com.tbgbook.bookstore.book.domain.BookLabelRequest;
import com.tbgbook.bookstore.book.domain.BookLabelResult;
import com.tbgbook.bookstore.book.domain.BookOption;
import com.tbgbook.bookstore.book.domain.BookPriceHistory;
import com.tbgbook.bookstore.book.domain.Category;
import com.tbgbook.bookstore.book.domain.Publisher;
import com.tbgbook.bookstore.book.mapper.BookChangeLogMapper;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.book.mapper.BookPriceHistoryMapper;
import com.tbgbook.bookstore.book.mapper.CategoryMapper;
import com.tbgbook.bookstore.book.mapper.PublisherMapper;
import com.tbgbook.bookstore.book.service.IBookService;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.common.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;

/**
 * 图书主档 Service 业务层处理。
 *
 * <p><b>本类是全项目的样板</b>，后续业务表照此复制。它集中演示了四件容易写错的事：
 * <ol>
 *   <li><b>服务端生成字段</b> —— {@code bookNo} / {@code costPrice} / {@code saleCount}
 *       不接受客户端传入；{@code publisherName} 由 {@code publisherId} 回填（冗余字段的一致性）</li>
 *   <li><b>成本字段按权限置 null 而非报错</b>（FR-4.6 规则 5）—— 见 {@link #maskCost}</li>
 *   <li><b>变更留痕</b> —— 价格写 {@code biz_book_price_history}（FR-4.1 规则 5）、
 *       关键字段写 {@code biz_book_change_log}（FR-4.1.9），与主表更新**同事务**</li>
 *   <li><b>删除保护</b> —— 有库存或流水的图书不可删，正确做法是下架（FR-4.1.3）</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class BookServiceImpl extends ServiceImpl<BookMapper, Book> implements IBookService
{
    /** 成本可见性权限串（根 CLAUDE.md §6.1 的 5 个成本权限之一） */
    private static final String COST_VIEW_PERM = "book:cost:view";

    /** 图书编号前缀与流水位数（需求 §3.2 编码规则：BK + 8 位流水） */
    private static final String BOOK_NO_PREFIX = "BK";
    private static final int BOOK_NO_DIGITS = 8;

    /** 图书状态取值边界（biz_book.status：1在售 2下架 3缺货 4停售） */
    private static final int STATUS_MIN = 1;
    private static final int STATUS_MAX = 4;

    /** 图书下拉选项的返回上限（契约：默认 20，最大 50） */
    private static final int OPTION_LIMIT_DEFAULT = 20;
    private static final int OPTION_LIMIT_MAX = 50;

    /** 价格类型（biz_book_price_history.price_type） */
    private static final int PRICE_TYPE_LIST = 1;   // 定价
    private static final int PRICE_TYPE_MEMBER = 2; // 会员价
    private static final int PRICE_TYPE_COST = 3;   // 成本价

    /**
     * 需要留痕的关键字段白名单。
     *
     * <p>**不是全字段 diff** —— 一次编辑若把 30 个字段都记一遍，变更日志会被噪音淹没，
     * 而业务真正关心的是价格、分类、状态这几类（FR-4.1.9 点名的「价格、库存、状态」）。
     */
    private static final List<TrackedField> TRACKED_FIELDS = List.of(
            new TrackedField("title", "书名", Book::getTitle),
            new TrackedField("author", "作者", Book::getAuthor),
            new TrackedField("publisherId", "出版社", Book::getPublisherId),
            new TrackedField("categoryId", "分类", Book::getCategoryId),
            new TrackedField("bookType", "图书类型", Book::getBookType),
            new TrackedField("price", "定价", Book::getPrice),
            new TrackedField("memberPrice", "会员价", Book::getMemberPrice),
            new TrackedField("status", "图书状态", Book::getStatus),
            new TrackedField("publishStatus", "上架状态", Book::getPublishStatus),
            new TrackedField("infoStatus", "建档信息状态", Book::getInfoStatus));

    /** 留痕字段：属性名 + 中文名 + 取值函数 */
    private record TrackedField(String name, String label, Function<Book, Object> getter)
    {
    }

    private final ReferenceGuard referenceGuard;
    private final PublisherMapper publisherMapper;
    private final CategoryMapper categoryMapper;
    private final BookPriceHistoryMapper priceHistoryMapper;
    private final BookChangeLogMapper changeLogMapper;

    @Override
    public List<Book> selectBookList(Book book)
    {
        List<Book> list = baseMapper.selectBookList(book);
        return maskCost(list);
    }

    @Override
    public Book selectBookById(Long bookId)
    {
        Book book = referenceGuard.requireExists(baseMapper, bookId, "图书");
        return maskCost(book);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addBook(Book book)
    {
        // 服务端生成字段：客户端传了也不认
        book.setBookId(null);
        book.setBookNo(null);
        book.setCostPrice(null);
        book.setSaleCount(null);

        requireIsbnAvailable(book.getIsbn(), null);
        fillRedundantAndValidateRefs(book);
        applyDefaults(book);
        book.setInfoStatus(computeInfoStatus(book));

        // bookNo 的生成：`book_no` 是 NOT NULL 且**无默认值**，所以插入前必须先给出值
        // ——「先插入再回填」在第一步就会因缺列被数据库拒绝（实测踩到）。
        // 做法：预取 AUTO_INCREMENT 作为**预估主键**生成编号 → 插入 → 若真实主键与预估不符
        // （并发下另一个请求先插入），再按真实主键校正一次。
        // 不采用「先查 max(book_no)+1 再插入」：并发下会撞号，而 book_no 没有唯一索引兜底。
        Long predictedId = baseMapper.selectNextBookId();
        book.setBookNo(buildBookNo(predictedId == null ? 1L : predictedId));

        int rows = baseMapper.insert(book);

        String actualNo = buildBookNo(book.getBookId());
        if (!actualNo.equals(book.getBookNo()))
        {
            Book patch = new Book();
            patch.setBookId(book.getBookId());
            patch.setBookNo(actualNo);
            baseMapper.updateById(patch);
            book.setBookNo(actualNo);
        }

        return rows;
    }

    /** 按需求 §3.2 编码规则生成图书编号：{@code BK} + 8 位流水 */
    private String buildBookNo(Long bookId)
    {
        return BOOK_NO_PREFIX + String.format("%0" + BOOK_NO_DIGITS + "d", bookId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateBook(Book book)
    {
        if (book.getBookId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的图书");
        }
        Book current = referenceGuard.requireExists(baseMapper, book.getBookId(), "图书");

        // 服务端生成字段不接受客户端传入
        book.setBookNo(null);
        book.setCostPrice(null);
        book.setSaleCount(null);

        if (book.getIsbn() != null && !book.getIsbn().equals(current.getIsbn()))
        {
            requireIsbnAvailable(book.getIsbn(), book.getBookId());
        }
        fillRedundantAndValidateRefs(book);

        // 留痕必须在更新前算 —— 更新后 current 与 book 就分不清前后了
        writePriceHistory(current, book);
        writeChangeLogs(current, book);

        return baseMapper.updateById(book);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeBooks(Long[] bookIds)
    {
        List<Long> idList = (bookIds == null) ? Collections.emptyList() : Arrays.asList(bookIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "图书");

        for (Serializable id : ids)
        {
            // 删除保护：有库存行或库存流水都不允许删 —— 流水表会因此失去主语，
            // 且历史订单的明细将指向一个不存在的图书。正确做法是下架（FR-4.1.3）。
            // ⚠️ biz_stock 与 biz_stock_flow 都没有 del_flag，故 softDeleteColumn 传 null。
            long stockRows = referenceGuard.countReference("biz_stock", "book_id", id, null);
            long flowRows = referenceGuard.countReference("biz_stock_flow", "book_id", id, null);
            // 教材清单明细：2026-09-22 补。删掉被引用的图书会让明细行变成孤儿 ——
            // 而「发布」要校验每行明细都能关联到有效图书，孤儿行会让整份清单卡在草稿态
            // （docs/db/README.md §6.1 删除保护类；本表无 del_flag，故传 null）
            long textbookRows = referenceGuard.countReference("biz_textbook_list_item", "book_id", id, null);
            if (stockRows > 0 || flowRows > 0 || textbookRows > 0)
            {
                throw BizErrors.ruleViolated(
                        "该图书已有库存或流水记录（库存行 " + stockRows + " 条、流水 " + flowRows
                                + " 条），或已被 " + textbookRows
                                + " 条教材清单明细引用，不能删除；请改为下架");
            }
        }

        return baseMapper.deleteByIds(ids);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int changeStatus(Long bookId, Integer status, String reason)
    {
        // 范围校验放在这里而不是 DTO 的注解上：契约给本端点定的错误码是 422，
        // 而 @Min/@Max 会被 @Valid 拦成 400（见 BookStatusRequest 类注释）。
        if (status == null || status < STATUS_MIN || status > STATUS_MAX)
        {
            throw BizErrors.ruleViolated(
                    "图书状态取值非法（收到 " + status + "），允许 1在售 / 2下架 / 3缺货 / 4停售");
        }
        Book current = referenceGuard.requireExists(baseMapper, bookId, "图书");

        // 幂等：已是目标状态则直接成功。两个理由 ——
        // ① 重复点「下架」不该刷出第二条变更日志；
        // ② 不写库就不必依赖 MySQL「匹配行 vs 受影响行」的返回口径
        //    （Connector/J 默认 useAffectedRows=false 即返回匹配行，但这依赖连接串，
        //     不写库最干净）。
        if (Objects.equals(current.getStatus(), status))
        {
            return 1;
        }

        Book patch = new Book();
        patch.setBookId(bookId);
        patch.setStatus(status);

        // 状态是 TRACKED_FIELDS 之一 —— 复用 PUT 编辑那套留痕，不另起一套逻辑。
        // writeChangeLogs 只对「提交了的字段」比对，patch 上只有 status，故恰好出一条日志。
        writeChangeLogs(current, patch);

        int rows = baseMapper.updateById(patch);
        // 读到更新之间图书被删（逻辑删）会命中 0 行 —— 静默返回成功会让前端以为改成了
        referenceGuard.requireAffected(rows, "图书");
        return rows;

        // ⚠️ reason **有意不在方法体内使用**：biz_book_change_log 没有原因列
        //    （只有 field_name / before_value / after_value），存不下。
        //    该值由 BookController#changeStatus 上的 @Log 记入 sys_oper_log.oper_param，
        //    在 A-83 操作日志里可查。保留入参是为了让「请求体收了什么」在签名上可见。
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BookOption> selectBookOptions(String keyword, Integer limit)
    {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return baseMapper.selectBookOptions(kw, resolveOptionLimit(limit));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BookLabelResult buildLabels(BookLabelRequest request)
    {
        List<Long> bookIds = parseBookIds(request.getBookIds());
        if (bookIds.isEmpty())
        {
            throw BizErrors.ruleViolated("请至少选择一本图书再打印标签");
        }
        if (bookIds.size() > BookLabelRequest.MAX_BATCH_SIZE)
        {
            throw BizErrors.ruleViolated("单批最多打印 " + BookLabelRequest.MAX_BATCH_SIZE
                    + " 种图书的标签，本次选了 " + bookIds.size() + " 种，请分批打印");
        }

        int copies = resolveCopies(request.getCopies());
        boolean includePrice = !Boolean.FALSE.equals(request.getIncludePrice());

        List<BookLabel> rows = baseMapper.selectBookLabels(bookIds);
        if (rows.size() != bookIds.size())
        {
            throw BizErrors.notFound("所选图书中有 " + (bookIds.size() - rows.size())
                    + " 本已不存在（可能已被删除），请刷新列表后重试");
        }

        // in 不保序 —— 按入参顺序归位，打印顺序应与勾选顺序一致
        Map<Long, BookLabel> byId = new HashMap<>();
        for (BookLabel row : rows)
        {
            byId.put(row.getBookId(), row);
        }

        List<BookLabel> labels = new ArrayList<>(bookIds.size() * copies);
        for (Long bookId : bookIds)
        {
            BookLabel label = byId.get(bookId);
            if (!includePrice)
            {
                // 置 null 而非 0：BookLabel 上有 @JsonInclude(NON_NULL)，
                // 该字段会整条从响应里消失（契约：「是否打印价格」）
                label.setPrice(null);
            }
            for (int i = 0; i < copies; i++)
            {
                // 同一本书的多张标签内容相同，复用同一实例即可（响应只读，不会被改写）
                labels.add(label);
            }
        }

        BookLabelResult result = new BookLabelResult();
        result.setList(labels);
        result.setLabelSize(resolveLabelSize(request.getLabelSize()));
        result.setCopies(copies);
        result.setIncludePrice(includePrice);
        return result;
    }

    // ==================================================================
    // 内部：标签打印与下拉选项的入参归一化
    // ==================================================================

    /**
     * 解析逗号分隔的图书ID串。
     *
     * <p><b>去重但保序</b>：勾选重复的图书不该出两份标签 —— 控制重复的入口是 {@code copies}。
     * 非法片段抛 422 而不是静默跳过：静默跳过会让操作员拿到比预期少的标签却不知道原因。
     */
    private List<Long> parseBookIds(String raw)
    {
        List<Long> ids = new ArrayList<>();
        if (raw == null || raw.isBlank())
        {
            return ids;
        }
        for (String part : raw.split(","))
        {
            String token = part.trim();
            if (token.isEmpty())
            {
                continue;
            }
            try
            {
                ids.add(Long.parseLong(token));
            }
            catch (NumberFormatException e)
            {
                throw BizErrors.ruleViolated("图书ID列表格式非法：「" + token + "」不是有效的图书ID");
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    /** 份数：默认 1；小于 1 或大于上限都抛 422（上限见 {@link BookLabelRequest#MAX_COPIES}） */
    private int resolveCopies(Integer copies)
    {
        if (copies == null)
        {
            return BookLabelRequest.DEFAULT_COPIES;
        }
        if (copies < 1)
        {
            throw BizErrors.ruleViolated("每本书的标签份数至少为 1 份");
        }
        if (copies > BookLabelRequest.MAX_COPIES)
        {
            throw BizErrors.ruleViolated("每本书的标签份数最多 "
                    + BookLabelRequest.MAX_COPIES + " 份，本次传了 " + copies + " 份");
        }
        return copies;
    }

    /** 标签规格：空白取默认 60x40。本服务不解释规格含义（渲染在前端） */
    private String resolveLabelSize(String labelSize)
    {
        return (labelSize == null || labelSize.isBlank())
                ? BookLabelRequest.DEFAULT_LABEL_SIZE
                : labelSize.trim();
    }

    /** 下拉上限：null 或小于 1 取默认 20，超过 50 夹到 50（契约：默认 20，最大 50） */
    private int resolveOptionLimit(Integer limit)
    {
        if (limit == null || limit < 1)
        {
            return OPTION_LIMIT_DEFAULT;
        }
        return Math.min(limit, OPTION_LIMIT_MAX);
    }

    // ==================================================================
    // 成本可见性
    // ==================================================================

    /**
     * 成本字段按权限裁剪：无 {@code book:cost:view} 时把 {@code costPrice} 置 null。
     *
     * <p><b>置 null 而不是抛 403</b> —— 抛错会导致整张列表打不开；
     * 置 null 让前端只隐藏这一列（FR-4.6 规则 5 / 根 CLAUDE.md §6.1）。
     */
    private List<Book> maskCost(List<Book> list)
    {
        if (!canViewCost())
        {
            for (Book b : list)
            {
                b.setCostPrice(null);
            }
        }
        return list;
    }

    private Book maskCost(Book book)
    {
        if (book != null && !canViewCost())
        {
            book.setCostPrice(null);
        }
        return book;
    }

    private boolean canViewCost()
    {
        try
        {
            return SecurityUtils.hasPermi(COST_VIEW_PERM);
        }
        catch (Exception e)
        {
            // 无登录上下文（定时任务 / 内部调用）时按「无权限」处理，不抛异常
            return false;
        }
    }

    // ==================================================================
    // 内部：校验与填充
    // ==================================================================

    /** ISBN 唯一（`uk_isbn`）：同 ISBN 仅允许一条主记录，多副本通过库存数量管理（FR-4.1 规则 2） */
    private void requireIsbnAvailable(String isbn, Long excludeBookId)
    {
        if (isbn == null || isbn.isBlank())
        {
            return;
        }
        Long exists = baseMapper.selectCount(Wrappers.<Book>lambdaQuery()
                .eq(Book::getIsbn, isbn)
                .ne(excludeBookId != null, Book::getBookId, excludeBookId));
        if (exists != null && exists > 0)
        {
            throw BizErrors.duplicate("图书", "ISBN", isbn);
        }
    }

    /**
     * 校验外键引用并把冗余字段补齐。
     *
     * <p>84 张业务表零物理外键（`docs/db/README.md` §6.1），引用完整性在 service 层保证：
     * <ul>
     *   <li>{@code publisherId} → 校验存在，并把 {@code publisherName} 回填为库中的当前名称
     *       （冗余字段必须由服务端维护，否则出版社改名后列表会显示旧名）</li>
     *   <li>{@code categoryId} → 校验存在</li>
     * </ul>
     */
    private void fillRedundantAndValidateRefs(Book book)
    {
        if (book.getPublisherId() != null && book.getPublisherId() > 0)
        {
            Publisher publisher = referenceGuard.requireExists(publisherMapper, book.getPublisherId(), "出版社");
            book.setPublisherName(publisher.getPublisherName());
        }
        if (book.getCategoryId() != null && book.getCategoryId() > 0)
        {
            Category category = referenceGuard.requireExists(categoryMapper, book.getCategoryId(), "分类");
            // 只允许挂二级分类：一级分类是分组，挂上去会让分类树统计失真
            if (category.getCategoryLevel() != null && category.getCategoryLevel() < 2)
            {
                throw BizErrors.ruleViolated(
                        "「" + category.getCategoryName() + "」是一级分类，图书只能挂在二级分类下");
            }
        }
    }

    /** 补齐默认值（与库表默认值保持一致，避免插入时落 null 与预期不符） */
    private void applyDefaults(Book book)
    {
        if (book.getBookType() == null)
        {
            book.setBookType(1);
        }
        if (book.getStatus() == null)
        {
            book.setStatus(1);
        }
        if (book.getPublishStatus() == null)
        {
            book.setPublishStatus(1);
        }
        if (book.getSummarySource() == null)
        {
            book.setSummarySource(1);
        }
        if (book.getMemberPrice() == null)
        {
            book.setMemberPrice(BigDecimal.ZERO);
        }
    }

    /**
     * 计算建档信息状态：关键字段齐全为 1（完整），否则为 2（待补全）。
     *
     * <p>依据 FR-4.1 规则 1 —— ISBN 查询失败时允许手工录入并自动打「信息待补全」标记。
     * 这里把判断条件统一为「关键字段是否齐全」，手工录入与 ISBN 回填走同一把尺子。
     */
    private int computeInfoStatus(Book book)
    {
        boolean complete = notBlank(book.getAuthor())
                && book.getPublisherId() != null && book.getPublisherId() > 0
                && book.getCategoryId() != null && book.getCategoryId() > 0
                && book.getPrice() != null && book.getPrice().compareTo(BigDecimal.ZERO) > 0;
        return complete ? 1 : 2;
    }

    // ==================================================================
    // 内部：留痕
    // ==================================================================

    /**
     * 价格变化写 {@code biz_book_price_history}（FR-4.1 规则 5：价格变动必须留痕）。
     *
     * <p>只对**提交了的**字段比对：{@code after == null} 表示客户端本次没提交该字段，
     * MP 的 {@code updateById} 也不会写它，因此不算变更 —— 否则每次改书名都会刷出三条
     * 价格「变更」（实际前后同值）。
     *
     * <p><b>比对用 {@linkplain BigDecimal#compareTo 数值比较}，不是 {@code equals}</b>。
     * 这条踩过坑：{@code biz_book.price} 是 {@code decimal(12,2)}，从库里读出来是
     * {@code 49.00}（scale=2），而 A-07 的 {@code el-input-number} 提交的是 {@code 49}
     * （scale=0）—— 两者 {@code equals} 为 false、{@code compareTo} 为 0。
     * 用 {@code equals} 会让「只改了书名」的一次编辑刷出两条内容为
     * 「49.00 → 49」的假价格变更（实测踩到，A-07 联调时发现）。
     */
    private void writePriceHistory(Book before, Book after)
    {
        recordPriceChange(before.getBookId(), PRICE_TYPE_LIST, "定价", before.getPrice(), after.getPrice());
        recordPriceChange(before.getBookId(), PRICE_TYPE_MEMBER, "会员价", before.getMemberPrice(), after.getMemberPrice());
        recordPriceChange(before.getBookId(), PRICE_TYPE_COST, "成本价", before.getCostPrice(), after.getCostPrice());
    }

    private void recordPriceChange(Long bookId, int priceType, String label,
            BigDecimal before, BigDecimal after)
    {
        // after 为 null 表示本次未提交该价格字段 → MP 不会更新它 → 不算变更
        if (after == null)
        {
            return;
        }
        BigDecimal b = (before == null) ? BigDecimal.ZERO : before;
        // compareTo 而非 equals：49.00 与 49 是同一个金额，见方法注释
        if (b.compareTo(after) == 0)
        {
            return;
        }
        BookPriceHistory history = new BookPriceHistory();
        history.setBookId(bookId);
        history.setPriceType(priceType);
        // 两个金额都**归一到列定义的 scale(2)** 再落库，避免历史表里
        // 出现 49 与 49.00 混排（前端按字符串渲染时会看着像两个不同金额）
        history.setBeforePrice(scale2(b));
        history.setAfterPrice(scale2(after));
        history.setChangeReason(label + "变更");
        priceHistoryMapper.insert(history);
    }

    /**
     * 关键字段变化写 {@code biz_book_change_log}（FR-4.1.9）。
     *
     * <p><b>比对必须区分类型，不能一律用 {@code Objects.equals}</b>。
     * 金额字段（定价 / 会员价）踩过同一个坑：{@code biz_book.price} 是
     * {@code decimal(12,2)}，从库里读出来是 {@code 49.00}（scale=2），
     * 而 A-07 的 {@code el-input-number} 提交的是 {@code 49}（scale=0）——
     * {@code Objects.equals} 判为不同，于是「只改了书名」的一次编辑会刷出
     * {@code price 49.00 -> 49} 这样的假变更日志（实测踩到）。
     * 见 {@link #valuesEqual}。
     */
    private void writeChangeLogs(Book before, Book after)
    {
        for (TrackedField field : TRACKED_FIELDS)
        {
            Object oldVal = field.getter().apply(before);
            Object newVal = field.getter().apply(after);
            // 未提交该字段（null）→ MP 不会更新它 → 不算变更
            if (newVal == null || valuesEqual(oldVal, newVal))
            {
                continue;
            }
            BookChangeLog log = new BookChangeLog();
            log.setBookId(before.getBookId());
            log.setFieldName(field.name());
            log.setFieldLabel(field.label());
            log.setBeforeValue(stringify(oldVal));
            log.setAfterValue(stringify(newVal));
            changeLogMapper.insert(log);
        }
    }

    /**
     * 留痕用的值比较：金额按**数值**比，其余按 {@code Objects.equals}。
     *
     * <p>{@code BigDecimal.equals} 连 scale 一起比 —— {@code 49.00} 与 {@code 49}
     * 不相等，但它们是同一个金额。落库时两个值都归一到 scale=2，
     * 所以日志里读到的始终是 {@code 49.00}，不会出现两种写法。
     */
    private boolean valuesEqual(Object a, Object b)
    {
        if (a instanceof BigDecimal && b instanceof BigDecimal)
        {
            return ((BigDecimal) a).compareTo((BigDecimal) b) == 0;
        }
        return Objects.equals(a, b);
    }

    private String stringify(Object value)
    {
        if (value == null)
        {
            return "";
        }
        // 金额归一到 scale=2，避免日志里 49 与 49.00 混排（看着像两个金额）
        String s = (value instanceof BigDecimal) ? scale2((BigDecimal) value).toPlainString()
                : String.valueOf(value);
        // before_value / after_value 是 varchar(500)，超长截断避免插入报错
        return (s.length() > 500) ? s.substring(0, 500) : s;
    }

    private boolean notBlank(String s)
    {
        return s != null && !s.isBlank();
    }

    /** 金额归一到两位小数（与 {@code biz_book.price} / {@code biz_book_price_history.*_price} 的列定义一致） */
    private BigDecimal scale2(BigDecimal v)
    {
        return (v == null) ? null : v.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
