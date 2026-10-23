package com.tbgbook.bookstore.book.service.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;

import cn.idev.excel.ExcelWriter;
import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.metadata.data.ReadCellData;
import cn.idev.excel.read.listener.ReadListener;
import cn.idev.excel.write.metadata.WriteSheet;

import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.domain.BookImportBatch;
import com.tbgbook.bookstore.book.domain.BookImportRow;
import com.tbgbook.bookstore.book.domain.BookTextbook;
import com.tbgbook.bookstore.book.domain.Category;
import com.tbgbook.bookstore.book.domain.Publisher;
import com.tbgbook.bookstore.book.domain.excel.BookImportErrorRow;
import com.tbgbook.bookstore.book.domain.excel.BookImportExcelRow;
import com.tbgbook.bookstore.book.domain.excel.BookImportParseResult;
import com.tbgbook.bookstore.book.domain.excel.BookImportTemplateTip;
import com.tbgbook.bookstore.book.mapper.BookImportBatchMapper;
import com.tbgbook.bookstore.book.mapper.BookImportRowMapper;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.book.mapper.CategoryMapper;
import com.tbgbook.bookstore.book.mapper.PublisherMapper;
import com.tbgbook.bookstore.book.service.IBookImportBatchService;
import com.tbgbook.bookstore.book.service.IBookService;
import com.tbgbook.bookstore.book.service.IBookTextbookService;
import com.tbgbook.bookstore.book.support.IsbnUtils;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.common.support.ReferenceGuard;
import com.tbgbook.common.exception.file.InvalidExtensionException;
import com.tbgbook.common.config.RuoYiConfig;
import com.tbgbook.common.utils.DateUtils;
import com.tbgbook.common.utils.SecurityUtils;
import com.tbgbook.common.utils.file.FileUploadUtils;
import com.tbgbook.common.utils.file.FileUtils;
import com.tbgbook.framework.manager.AsyncManager;

import lombok.RequiredArgsConstructor;

/**
 * 图书批量导入批次 Service 实现（FR-4.1.6 / 页面 A-10）。
 *
 * <p><b>两步式</b>：{@link #parseExcel} 只解析校验、{@link #submitExecute} 才落库。
 *
 * <p><b>几处关键取舍</b>：
 * <ol>
 *   <li><b>解析同步、执行异步</b>。解析必须同步 —— 接口文档声明其响应含 {@code rows}
 *       逐行结果数组，前端步骤③的预览表格直接依赖它，异步就拿不到。
 *       执行则按文档「执行过程异步，progress 可轮询批次详情获取」丢进线程池。
 *       ⚠️ 原型 A-10 写的是「上传后异步解析 + 进度条」，与接口文档的响应契约冲突，
 *       已登记为待确认项。</li>
 *   <li><b>整行快照存 {@code raw_data}</b>，执行阶段不再读 Excel —— 解析到执行之间
 *       文件被删/被换都不影响，导入的仍是操作员在步骤③确认过的那份数据。</li>
 *   <li><b>逐行独立事务</b>（借道 {@code IBookService} 的 {@code @Transactional} 方法），
 *       不套一个大事务 —— 导入是「部分成功」语义，第 900 行失败不该回滚前 899 行。</li>
 *   <li><b>不绕开 {@code BookServiceImpl} 直接 insert</b>：{@code book_no} 生成、
 *       ISBN 唯一校验、{@code publisherName} 回填、{@code infoStatus} 计算、
 *       价格历史与变更日志都在那里，绕开就会漏。</li>
 * </ol>
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class BookImportBatchServiceImpl extends ServiceImpl<BookImportBatchMapper, BookImportBatch>
        implements IBookImportBatchService
{
    private static final Logger log = LoggerFactory.getLogger(BookImportBatchServiceImpl.class);

    /** 导入文件大小上限（接口文档：超过 → 413） */
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    /** 允许的扩展名（接口文档：xls / xlsx；其余 → 422） */
    private static final String[] ALLOWED_EXTENSIONS = { "xls", "xlsx" };

    /** 表头必填列（接口文档：缺失 → 422） */
    private static final List<String> MANDATORY_HEADERS = List.of("ISBN", "书名", "定价");

    /** 批次号前缀 */
    private static final String BATCH_NO_PREFIX = "IMP";

    /** 图书类型（biz_book.book_type） */
    private static final int BOOK_TYPE_NORMAL = 1;
    private static final int BOOK_TYPE_TEXTBOOK = 2;

    /** 分类层级：只接受二级分类挂书（与 BookServiceImpl 同一把尺子） */
    private static final int CATEGORY_LEVEL_LEAF = 2;

    /** 每处理这么多行刷一次 progress，避免每行一条 UPDATE */
    private static final int PROGRESS_STEP = 20;

    private static final String SHEET_DATA = "图书清单";
    private static final String SHEET_TIP = "字段说明";

    /** error_msg / remark 列宽，超长截断避免插入报错 */
    private static final int MAX_MSG_LEN = 500;

    /** 出版日期可接受的格式（**严格**解析，见 {@link #parseStrictDate}） */
    private static final String[] DATE_PATTERNS =
            { "yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd", "yyyyMMdd" };

    private final ReferenceGuard referenceGuard;
    private final BookMapper bookMapper;
    private final BookImportRowMapper importRowMapper;
    private final PublisherMapper publisherMapper;
    private final CategoryMapper categoryMapper;
    private final IBookService bookService;
    private final IBookTextbookService bookTextbookService;

    // ==================================================================
    // CRUD
    // ==================================================================

    @Override
    public List<BookImportBatch> selectImportBatchList(BookImportBatch batch)
    {
        return baseMapper.selectImportBatchList(batch);
    }

    @Override
    public BookImportBatch selectImportBatchById(Long batchId)
    {
        return referenceGuard.requireExists(baseMapper, batchId, "导入批次");
    }

    /**
     * {@inheritDoc}
     *
     * <p>手工登记一个空批次。{@code batchNo} 由服务端生成，计数列一律归零 ——
     * 它们描述的是解析/执行结果，不接受客户端编造。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addImportBatch(BookImportBatch batch)
    {
        batch.setBatchId(null);
        batch.setBatchNo(generateBatchNo());
        batch.setTotalRows(0);
        batch.setValidRows(0);
        batch.setWarnRows(0);
        batch.setErrorRows(0);
        batch.setSuccessRows(0);
        batch.setFailRows(0);
        batch.setStatus(BookImportBatch.STATUS_PARSING);
        batch.setProgress(0);
        batch.setFailFileUrl("");
        if (batch.getImportStrategy() == null)
        {
            batch.setImportStrategy(BookImportBatch.STRATEGY_SKIP);
        }
        if (batch.getFileName() == null)
        {
            batch.setFileName("");
        }
        if (batch.getFileUrl() == null)
        {
            batch.setFileUrl("");
        }
        return baseMapper.insert(batch);
    }

    /**
     * {@inheritDoc}
     *
     * <p>只允许改「描述性」字段（文件名/文件地址/策略/备注）；批次号与全部计数列是
     * 服务端独占，客户端传了也置空 —— 否则改一次备注就能把 {@code successRows} 改成
     * 任意值，导入结果的审计意义就没了。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateImportBatch(BookImportBatch batch)
    {
        if (batch.getBatchId() == null)
        {
            throw BizErrors.badRequest("未指定要修改的导入批次");
        }
        referenceGuard.requireExists(baseMapper, batch.getBatchId(), "导入批次");

        batch.setBatchNo(null);
        batch.setTotalRows(null);
        batch.setValidRows(null);
        batch.setWarnRows(null);
        batch.setErrorRows(null);
        batch.setSuccessRows(null);
        batch.setFailRows(null);
        batch.setStatus(null);
        batch.setProgress(null);
        batch.setFailFileUrl(null);

        return baseMapper.updateById(batch);
    }

    /**
     * {@inheritDoc}
     *
     * <p>明细行**没有独立生命周期**（{@code docs/db/README.md} §6.1 第 ③ 类「主从级联」）：
     * 批次一删，行就失去归属，故一并删除。这与「被引用时禁止删除」（第 ② 类）是两回事 ——
     * 批次不是被行「引用」的独立主数据，而是行的父。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeImportBatches(Long[] batchIds)
    {
        List<Long> idList = (batchIds == null) ? Collections.emptyList() : Arrays.asList(batchIds);
        Collection<? extends Serializable> ids = referenceGuard.requireIds(idList, "导入批次");

        importRowMapper.delete(Wrappers.<BookImportRow>lambdaQuery()
                .in(BookImportRow::getBatchId, ids));

        return baseMapper.deleteByIds(ids);
    }

    // ==================================================================
    // 第一步：解析
    // ==================================================================

    /**
     * {@inheritDoc}
     *
     * <p>事务范围含一次本地磁盘写（上传原件留档）—— 这是毫秒级本地 IO，不是根
     * CLAUDE.md §6.2 禁止的「事务里做远程调用」（那条针对百炼 API / OSS）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookImportParseResult parseExcel(MultipartFile file, Integer importStrategy)
    {
        requireUploadable(file);
        int strategy = resolveStrategy(importStrategy);

        ImportSheetListener listener = readSheet(file);
        requireMandatoryHeaders(listener.getHeadNames());

        // 去掉整行空白（Excel 末尾常见，算进 totalRows 会让「总行数」对不上操作员的观感），
        // 行号取监听器记录的**真实 Excel 行索引 + 1**（0 基 → Excel 1 基）。
        // ⚠️ 不要按「第几个数据行」顺序推算行号：FastExcel 不回调整行空白的行，
        // 中间夹一个空行，推算出的行号就会比 Excel 里少 1（实测踩到）。
        List<BookImportExcelRow> rawRows = listener.getRows();
        List<Integer> rawIndexes = listener.getRowIndexes();
        List<BookImportExcelRow> dataRows = new ArrayList<>();
        List<Integer> dataRowNos = new ArrayList<>();
        for (int i = 0; i < rawRows.size(); i++)
        {
            if (notBlankRow(rawRows.get(i)))
            {
                dataRows.add(rawRows.get(i));
                dataRowNos.add(rawIndexes.get(i) + 1);
            }
        }

        if (dataRows.isEmpty())
        {
            throw BizErrors.ruleViolated("文件无有效数据行，请确认已按标准模板填写");
        }

        // 一次性把匹配依据读进内存：出版社与二级分类都是小表，
        // 逐行查库会让 1000 行的文件产生 2000 次 SELECT
        Map<String, Long> publisherByName = loadPublisherIndex();
        Map<String, Long> categoryByName = loadCategoryIndex();
        Set<String> existingIsbns = loadExistingIsbns(dataRows);

        List<BookImportRow> rows = new ArrayList<>(dataRows.size());
        Set<String> seenIsbns = new HashSet<>();
        int valid = 0;
        int warn = 0;
        int error = 0;
        for (int i = 0; i < dataRows.size(); i++)
        {
            BookImportRow row = validateRow(dataRowNos.get(i), dataRows.get(i), strategy,
                    existingIsbns, publisherByName, categoryByName, seenIsbns);
            if (row.getRowStatus() == BookImportRow.ROW_VALID)
            {
                valid++;
            }
            else if (row.getRowStatus() == BookImportRow.ROW_WARN)
            {
                warn++;
            }
            else
            {
                error++;
            }
            rows.add(row);
        }

        // 原件留档放在**全部校验通过之后**：先传后验会让被拒的文件也落到磁盘上，
        // 留下一堆没人引用的孤儿文件（表头不合法、无有效数据行这两种最常见，实测踩到）。
        // file_url 因此始终指向「真正入过库的那一份」。
        String fileUrl;
        try
        {
            fileUrl = FileUploadUtils.upload(RuoYiConfig.getImportPath(), file, ALLOWED_EXTENSIONS);
        }
        catch (IOException | InvalidExtensionException e)
        {
            // 扩展名已在 requireUploadable 里先判过，这里兜住 FileUploadUtils 的受检异常
            // （它自己的白名单比本接口更宽，实际不会命中）
            throw BizErrors.badRequest("导入文件保存失败：" + e.getMessage());
        }

        BookImportBatch batch = new BookImportBatch();
        batch.setBatchNo(generateBatchNo());
        batch.setFileName(FileUploadUtils.extractFilename(file));
        batch.setFileUrl(fileUrl);
        batch.setImportStrategy(strategy);
        batch.setTotalRows(dataRows.size());
        batch.setValidRows(valid);
        batch.setWarnRows(warn);
        batch.setErrorRows(error);
        batch.setSuccessRows(0);
        batch.setFailRows(0);
        batch.setStatus(BookImportBatch.STATUS_VALIDATED);
        batch.setProgress(0);
        batch.setFailFileUrl("");
        baseMapper.insert(batch);

        Long batchId = batch.getBatchId();
        for (BookImportRow row : rows)
        {
            row.setBatchId(batchId);
            importRowMapper.insert(row);
        }

        BookImportParseResult result = new BookImportParseResult();
        result.setBatchId(batchId);
        result.setBatchNo(batch.getBatchNo());
        result.setTotalRows(batch.getTotalRows());
        result.setValidRows(valid);
        result.setWarnRows(warn);
        result.setErrorRows(error);
        for (BookImportRow row : rows)
        {
            result.getRows().add(new BookImportParseResult.Row(row.getRowNo(), row.getIsbn(),
                    row.getTitle(), row.getRowStatus(), row.getErrorMsg()));
        }
        log.info("图书导入解析完成 batchNo={} 总行数={} 可导入={} 警告={} 错误={}",
                batch.getBatchNo(), dataRows.size(), valid, warn, error);
        return result;
    }

    // ==================================================================
    // 第二步：执行
    // ==================================================================

    /**
     * {@inheritDoc}
     *
     * <p>刻意**不加 {@code @Transactional}**：状态占位是一条自带条件的 UPDATE
     * （本身就是原子的），而异步派发不该被包在事务里。
     */
    @Override
    public BookImportBatch submitExecute(Long batchId, Boolean skipWarnRows)
    {
        BookImportBatch batch = referenceGuard.requireExists(baseMapper, batchId, "导入批次");

        // 原子占位：只有把 2校验完成 改成 3导入中 成功的那一次调用有资格执行。
        // 不用「先查状态、再更新」两步写法 —— 那中间有竞态窗口，连点两次会导两遍。
        int locked = baseMapper.update(null, Wrappers.<BookImportBatch>lambdaUpdate()
                .eq(BookImportBatch::getBatchId, batchId)
                .eq(BookImportBatch::getStatus, BookImportBatch.STATUS_VALIDATED)
                .set(BookImportBatch::getStatus, BookImportBatch.STATUS_IMPORTING)
                .set(BookImportBatch::getProgress, 0)
                .set(BookImportBatch::getSuccessRows, 0)
                .set(BookImportBatch::getFailRows, 0)
                .set(BookImportBatch::getFailFileUrl, ""));
        if (locked == 0)
        {
            throw BizErrors.ruleViolated("批次「" + batch.getBatchNo() + "」当前状态为 " + batch.getStatus()
                    + "，不是 2校验完成，不能执行导入（已执行过或解析失败）");
        }

        // 异步线程池里没有 SecurityContext。先在这里捕获 Authentication、任务内再装回去，
        // 否则 MyMetaObjectHandler 会把操作人记成 system，
        // BookServiceImpl 的成本可见性判断也会退化成「无权限」。
        Authentication authentication = SecurityUtils.getAuthentication();
        boolean skipWarn = Boolean.TRUE.equals(skipWarnRows);
        AsyncManager.me().execute(new TimerTask()
        {
            @Override
            public void run()
            {
                SecurityContextHolder.getContext().setAuthentication(authentication);
                try
                {
                    doImport(batchId, skipWarn);
                }
                catch (Exception e)
                {
                    log.error("图书批量导入执行失败 batchId={}", batchId, e);
                    markBatchFailed(batchId, e.getMessage());
                }
                finally
                {
                    SecurityContextHolder.clearContext();
                }
            }
        });

        // 返回提交瞬间的快照：status=3、progress=0、successRows=0。
        // 真实结果靠轮询详情接口获取（接口文档：执行过程异步，progress 可轮询）。
        return baseMapper.selectById(batchId);
    }

    /**
     * 实际导入。**不加事务** —— 逐行走 {@code IBookService} 的 {@code @Transactional}
     * 方法，每行一个独立事务。导入是「部分成功」语义，第 900 行失败不该回滚前 899 行。
     */
    private void doImport(Long batchId, boolean skipWarnRows)
    {
        // 解析阶段就判为错误的行不进导入，但它们是操作员最需要对照修正的，
        // 必须先取出来 —— 执行失败也会把行状态刷成 3，取晚了就分不清是哪一类
        List<BookImportRow> parseErrors = importRowMapper.selectList(
                Wrappers.<BookImportRow>lambdaQuery()
                        .eq(BookImportRow::getBatchId, batchId)
                        .eq(BookImportRow::getRowStatus, BookImportRow.ROW_ERROR)
                        .orderByAsc(BookImportRow::getRowNo));

        List<BookImportRow> importable = importRowMapper.selectImportableRows(batchId);
        List<BookImportErrorRow> failures = new ArrayList<>();
        int success = 0;
        int fail = 0;
        int processed = 0;

        for (BookImportRow row : importable)
        {
            // 「跳过警告行」是操作员主动选择不导入这些行 —— 既不算成功也不算失败
            if (skipWarnRows && Integer.valueOf(BookImportRow.ROW_WARN).equals(row.getRowStatus()))
            {
                continue;
            }
            try
            {
                importOne(row);
                success++;
            }
            catch (Exception e)
            {
                fail++;
                String reason = resolveReason(e);
                markRowFailed(row.getRowId(), reason);
                failures.add(new BookImportErrorRow(row.getRowNo(), row.getIsbn(), row.getTitle(), reason));
            }
            processed++;
            if (processed % PROGRESS_STEP == 0)
            {
                refreshProgress(batchId, processed, importable.size());
            }
        }

        for (BookImportRow bad : parseErrors)
        {
            failures.add(new BookImportErrorRow(bad.getRowNo(), bad.getIsbn(), bad.getTitle(),
                    bad.getErrorMsg()));
        }

        String failFileUrl = failures.isEmpty() ? "" : writeErrorFileToDisk(batchId, failures);
        baseMapper.update(null, Wrappers.<BookImportBatch>lambdaUpdate()
                .eq(BookImportBatch::getBatchId, batchId)
                .set(BookImportBatch::getStatus, BookImportBatch.STATUS_DONE)
                .set(BookImportBatch::getProgress, 100)
                .set(BookImportBatch::getSuccessRows, success)
                .set(BookImportBatch::getFailRows, fail)
                .set(BookImportBatch::getFailFileUrl, failFileUrl));
        log.info("图书导入执行完成 batchId={} 成功={} 失败={}", batchId, success, fail);
    }

    /**
     * 导入单行。
     *
     * <p>**执行前重新查一次 ISBN**（接口文档要求）：解析到执行之间可能已有人建了同 ISBN 的书。
     * 这样也顺带覆盖了解析时就已知的重复 —— 两类冲突走同一条判定，不存两份逻辑。
     */
    private void importOne(BookImportRow row)
    {
        Map<String, Object> data = row.getRawData();
        if (data == null || data.isEmpty())
        {
            throw BizErrors.ruleViolated("该行原始数据缺失，无法导入");
        }
        String isbn = asString(data.get("isbn"));
        Book existing = findBookByIsbn(isbn);

        if (existing == null)
        {
            bookService.addBook(buildNewBook(data));
            createTextbookIfComplete(data);
            return;
        }

        BookImportBatch batch = baseMapper.selectById(row.getBatchId());
        boolean overwrite = batch != null
                && Integer.valueOf(BookImportBatch.STRATEGY_OVERWRITE).equals(batch.getImportStrategy());
        if (!overwrite)
        {
            throw BizErrors.conflict("ISBN「" + isbn + "」已存在，按「跳过」策略未导入");
        }
        // 覆盖更新：只覆盖 Excel 提供了值的字段，未提供的列保持原样。
        // BookServiceImpl.updateBook 对 null 字段「不算变更」，因此不会刷出假的价格历史/变更日志。
        bookService.updateBook(buildOverwriteBook(existing.getBookId(), data));
        createTextbookIfComplete(data);
    }

    private Book buildNewBook(Map<String, Object> data)
    {
        Book book = new Book();
        book.setIsbn(asString(data.get("isbn")));
        book.setTitle(asString(data.get("title")));
        book.setAuthor(asString(data.get("author")));
        book.setPublisherId(asLong(data.get("publisherId")));
        book.setCategoryId(asLong(data.get("categoryId")));
        book.setPublishDate(asLocalDate(data.get("publishDate")));
        book.setBookType(asInt(data.get("bookType")));
        book.setPrice(asDecimal(data.get("price")));
        book.setMemberPrice(asDecimal(data.get("memberPrice")));
        book.setEdition(asString(data.get("edition")));
        book.setBinding(asString(data.get("binding")));
        book.setPageCount(asLong(data.get("pageCount")));
        book.setSummary(asString(data.get("summary")));
        return book;
    }

    private Book buildOverwriteBook(Long bookId, Map<String, Object> data)
    {
        Book book = buildNewBook(data);
        book.setBookId(bookId);
        return book;
    }

    /**
     * 教材扩展（FR-4.1.8）：三项齐全才建，缺项在解析阶段已降级为警告。
     *
     * <p>扩展建档失败**不回滚图书主档**：主档已落库、信息待补全标记已打上，
     * 操作员可在 A-07 补齐 —— 这比整行失败更符合「先建档、后补全」的设计。
     */
    private void createTextbookIfComplete(Map<String, Object> data)
    {
        String major = asString(data.get("major"));
        String term = asString(data.get("term"));
        String college = asString(data.get("college"));
        if (isBlank(major) || isBlank(term) || isBlank(college))
        {
            return;
        }
        String isbn = asString(data.get("isbn"));
        Book book = findBookByIsbn(isbn);
        if (book == null)
        {
            return;
        }
        try
        {
            BookTextbook textbook = new BookTextbook();
            textbook.setBookId(book.getBookId());
            textbook.setMajor(major);
            textbook.setTerm(term);
            textbook.setCollege(college);
            bookTextbookService.addBookTextbook(textbook);
        }
        catch (Exception e)
        {
            log.warn("图书导入：教材扩展建档跳过 isbn={} 原因={}", isbn, e.getMessage());
        }
    }

    // ==================================================================
    // 下载
    // ==================================================================

    @Override
    public void writeErrorFile(Long batchId, HttpServletResponse response)
    {
        referenceGuard.requireExists(baseMapper, batchId, "导入批次");
        List<BookImportErrorRow> failures = collectFailures(batchId);
        if (failures.isEmpty())
        {
            throw BizErrors.notFound("该批次没有失败明细");
        }
        BookImportBatch batch = baseMapper.selectById(batchId);
        writeWorkbook(response, "导入失败明细_" + batch.getBatchNo(), SHEET_DATA, failures);
    }

    @Override
    public void writeTemplate(HttpServletResponse response)
    {
        List<BookImportTemplateTip> tips = new ArrayList<>();
        tips.add(new BookImportTemplateTip("ISBN", "是", "10 位或 13 位，可含连字符"));
        tips.add(new BookImportTemplateTip("书名", "是", "不超过 200 字"));
        tips.add(new BookImportTemplateTip("定价", "是", "非负数，如 39.80"));
        tips.add(new BookImportTemplateTip("作者", "否", "不超过 200 字"));
        tips.add(new BookImportTemplateTip("出版社", "否", "按名称匹配系统已有出版社，匹配不到将留空"));
        tips.add(new BookImportTemplateTip("出版日期", "否", "格式 yyyy-MM-dd，如 2026-01-01"));
        tips.add(new BookImportTemplateTip("分类", "否", "按名称匹配二级分类，匹配不到将留空"));
        tips.add(new BookImportTemplateTip("图书类型", "否", "填「普通」或「教材」，留空按普通处理"));
        tips.add(new BookImportTemplateTip("会员价", "否", "非负数，留空按等级折扣计算"));
        tips.add(new BookImportTemplateTip("版次", "否", "如 第 3 版"));
        tips.add(new BookImportTemplateTip("装帧", "否", "如 平装 / 精装"));
        tips.add(new BookImportTemplateTip("页数", "否", "整数"));
        tips.add(new BookImportTemplateTip("内容简介", "否", "任意文本"));
        tips.add(new BookImportTemplateTip("适用专业", "教材必填", "图书类型为教材时必填，否则仅导入图书主档"));
        tips.add(new BookImportTemplateTip("适用学期", "教材必填", "如 2026 秋季"));
        tips.add(new BookImportTemplateTip("开课院系", "教材必填", "如 计算机学院"));

        List<BookImportExcelRow> samples = new ArrayList<>();
        BookImportExcelRow sample = new BookImportExcelRow();
        sample.setIsbn("9787111544937");
        sample.setTitle("示例：深入理解计算机系统（导入前请删除本行）");
        sample.setPrice("139.00");
        sample.setAuthor("Randal E. Bryant");
        sample.setPublisherName("机械工业出版社");
        sample.setPublishDate("2016-11-01");
        sample.setCategoryName("理工");
        sample.setBookType("普通");
        sample.setMemberPrice("125.10");
        sample.setEdition("第 3 版");
        sample.setBinding("平装");
        sample.setPageCount("737");
        sample.setSummary("示例行，导入前请删除。");
        samples.add(sample);

        prepareDownload(response, "图书导入模板.xlsx");
        try (ExcelWriter writer = FastExcel.write(response.getOutputStream())
                .autoCloseStream(Boolean.FALSE).build())
        {
            WriteSheet dataSheet = FastExcel.writerSheet(0, SHEET_DATA)
                    .head(BookImportExcelRow.class).build();
            writer.write(samples, dataSheet);
            WriteSheet tipSheet = FastExcel.writerSheet(1, SHEET_TIP)
                    .head(BookImportTemplateTip.class).build();
            writer.write(tips, tipSheet);
            writer.finish();
        }
        catch (IOException e)
        {
            throw BizErrors.serverError("导入模板生成失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 内部：文件与 Excel
    // ==================================================================

    private void requireUploadable(MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            throw BizErrors.badRequest("请选择要导入的 Excel 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE)
        {
            throw BizErrors.payloadTooLarge("导入文件不能超过 10MB，当前 "
                    + (file.getSize() / 1024 / 1024) + "MB");
        }
        String extension = FileUploadUtils.getExtension(file);
        if (!Arrays.asList(ALLOWED_EXTENSIONS).contains(extension == null ? "" : extension.toLowerCase()))
        {
            throw BizErrors.ruleViolated("文件类型不受支持（." + extension + "），仅支持 xls / xlsx");
        }
    }

    private int resolveStrategy(Integer importStrategy)
    {
        if (importStrategy == null)
        {
            return BookImportBatch.STRATEGY_SKIP;
        }
        if (importStrategy != BookImportBatch.STRATEGY_SKIP
                && importStrategy != BookImportBatch.STRATEGY_OVERWRITE)
        {
            throw BizErrors.badRequest("重复ISBN策略取值只能是 1（跳过）或 2（覆盖更新）");
        }
        return importStrategy;
    }

    private ImportSheetListener readSheet(MultipartFile file)
    {
        ImportSheetListener listener = new ImportSheetListener();
        try (InputStream in = file.getInputStream())
        {
            FastExcel.read(in, BookImportExcelRow.class, listener)
                    .sheet(0)
                    .headRowNumber(1)
                    .doRead();
        }
        catch (IOException e)
        {
            throw BizErrors.badRequest("导入文件读取失败：" + e.getMessage());
        }
        catch (RuntimeException e)
        {
            throw BizErrors.ruleViolated("Excel 解析失败，请确认文件未损坏且为 xls/xlsx 格式："
                    + e.getMessage());
        }
        return listener;
    }

    private void requireMandatoryHeaders(Set<String> headNames)
    {
        List<String> missing = new ArrayList<>();
        for (String header : MANDATORY_HEADERS)
        {
            if (!headNames.contains(header))
            {
                missing.add(header);
            }
        }
        if (!missing.isEmpty())
        {
            throw BizErrors.ruleViolated("Excel 表头缺少必填列：" + String.join("、", missing)
                    + "。请下载标准导入模板后填写");
        }
    }

    /**
     * Excel 读取监听器：同时收表头与数据行。
     *
     * <p>⚠️ FastExcel 的 {@code ReadListener} **没有** EasyExcel 的
     * {@code invokeHeadMap(Map&lt;Integer,String&gt;, AnalysisContext)}，只有
     * {@code invokeHead(Map&lt;Integer, ReadCellData&lt;?&gt;&gt;, AnalysisContext)} ——
     * 表头要从 {@code ReadCellData.getStringValue()} 自己取。照抄 EasyExcel 的写法编译不过。
     */
    private static final class ImportSheetListener implements ReadListener<BookImportExcelRow>
    {
        private final List<BookImportExcelRow> rows = new ArrayList<>();
        /** 与 {@link #rows} 一一对应的**真实 Excel 行索引**（0 基） */
        private final List<Integer> rowIndexes = new ArrayList<>();
        private final Set<String> headNames = new LinkedHashSet<>();

        @Override
        public void invokeHead(Map<Integer, ReadCellData<?>> headMap, AnalysisContext context)
        {
            for (ReadCellData<?> cell : headMap.values())
            {
                String value = (cell == null) ? null : cell.getStringValue();
                if (value != null && !value.isBlank())
                {
                    headNames.add(value.trim());
                }
            }
        }

        @Override
        public void invoke(BookImportExcelRow data, AnalysisContext context)
        {
            rows.add(data);
            // ⚠️ 行号只能从读上下文取，不能按「第几个数据行」顺序推算：
            // FastExcel 默认**不回调整行空白的行**，中间夹一个空行，
            // 顺序推算出的行号会比 Excel 里少 1，操作员照着失败明细回原文件会改错行（实测踩到）。
            rowIndexes.add(context.readRowHolder().getRowIndex());
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context)
        {
            // 无后置处理：行已在 invoke 里累积
        }

        Set<String> getHeadNames()
        {
            return headNames;
        }

        List<BookImportExcelRow> getRows()
        {
            return rows;
        }

        List<Integer> getRowIndexes()
        {
            return rowIndexes;
        }
    }

    private boolean notBlankRow(BookImportExcelRow row)
    {
        return !isBlank(row.getIsbn()) || !isBlank(row.getTitle()) || !isBlank(row.getPrice());
    }

    // ==================================================================
    // 内部：逐行校验
    // ==================================================================

    /**
     * 校验一行并产出明细行实体。
     *
     * <p>判定顺序有意为之：**错误优先、且一发现就返回** —— 必填缺失/格式非法是硬伤，
     * 再叠加「出版社不存在」这类警告只会干扰操作员定位首要问题。
     *
     * <p>解析结果同时写进 {@code rawData}：**存的是归一化后的值**（ISBN 去连字符、
     * 名称换成 ID、日期规范化），执行阶段直接照单建 {@code Book}，不必重跑一遍匹配 ——
     * 也就不会因为出版社中途改名而导入成另一个 ID。
     */
    private BookImportRow validateRow(int rowNo, BookImportExcelRow raw, int strategy,
            Set<String> existingIsbns, Map<String, Long> publisherByName,
            Map<String, Long> categoryByName, Set<String> seenIsbns)
    {
        BookImportRow row = new BookImportRow();
        row.setRowNo(rowNo);
        row.setTitle(isBlank(raw.getTitle()) ? "" : raw.getTitle().trim());
        // 明细行存**归一化后**的 ISBN：这样 978-7-115-42802-8 与 9787115428028 在预览里
        // 一眼能看出是同一本，失败明细里的「ISBN 已存在」也能对上号。
        // 归一化不出东西时退回原始文本，让错误行仍有可辨认的标识。
        String isbn = IsbnUtils.normalize(raw.getIsbn());
        row.setIsbn(isBlank(isbn) ? (isBlank(raw.getIsbn()) ? "" : raw.getIsbn().trim()) : isbn);

        List<String> warnings = new ArrayList<>();

        // ---- 必填与格式（错误） ----
        if (isBlank(isbn))
        {
            return reject(row, "ISBN 不能为空");
        }
        if (isbn.length() > 20)
        {
            return reject(row, "ISBN 不能超过 20 个字符");
        }
        if (!IsbnUtils.isFormatOk(isbn))
        {
            return reject(row, "ISBN 格式不正确（应为 10 位或 13 位数字，可含连字符）：" + isbn);
        }
        String title = trimToNull(raw.getTitle());
        if (title == null)
        {
            return reject(row, "书名不能为空");
        }
        if (title.length() > 200)
        {
            return reject(row, "书名不能超过 200 个字符");
        }
        String priceRaw = trimToNull(raw.getPrice());
        if (priceRaw == null)
        {
            return reject(row, "定价不能为空");
        }
        BigDecimal price = toDecimal(priceRaw);
        if (price == null)
        {
            return reject(row, "定价不是合法数字：" + priceRaw);
        }
        if (price.signum() < 0)
        {
            return reject(row, "定价不能为负：" + priceRaw);
        }
        // 文件内重复：同一份文件里同 ISBN 出现两次，即便策略是「覆盖更新」
        // 也无法判断以哪一行为准，直接判错让操作员自己合并
        if (!seenIsbns.add(isbn))
        {
            return reject(row, "文件内 ISBN 重复：" + isbn);
        }

        // ---- 与库内已有 ISBN 冲突（警告，按策略在执行阶段处理） ----
        if (existingIsbns.contains(isbn))
        {
            warnings.add(strategy == BookImportBatch.STRATEGY_OVERWRITE
                    ? "ISBN 已存在，将覆盖更新"
                    : "ISBN 已存在，将跳过");
        }

        // ---- 关联名称匹配（警告 + 留空） ----
        String publisherName = trimToNull(raw.getPublisherName());
        Long publisherId = (publisherName == null) ? null : publisherByName.get(publisherName);
        if (publisherName != null && publisherId == null)
        {
            warnings.add("出版社「" + publisherName + "」不存在，将留空");
        }
        String categoryName = trimToNull(raw.getCategoryName());
        Long categoryId = (categoryName == null) ? null : categoryByName.get(categoryName);
        if (categoryName != null && categoryId == null)
        {
            warnings.add("分类「" + categoryName + "」不存在或不是二级分类，将留空");
        }

        // ---- 可选数值/日期列（警告 + 留空） ----
        BigDecimal memberPrice = null;
        String memberPriceRaw = trimToNull(raw.getMemberPrice());
        if (memberPriceRaw != null)
        {
            memberPrice = toDecimal(memberPriceRaw);
            if (memberPrice == null || memberPrice.signum() < 0)
            {
                warnings.add("会员价不是合法非负数字（" + memberPriceRaw + "），将留空");
                memberPrice = null;
            }
        }
        Long pageCount = null;
        String pageCountRaw = trimToNull(raw.getPageCount());
        if (pageCountRaw != null)
        {
            pageCount = toLong(pageCountRaw);
            if (pageCount == null || pageCount < 0)
            {
                warnings.add("页数不是合法非负整数（" + pageCountRaw + "），将留空");
                pageCount = null;
            }
        }
        String publishDateRaw = trimToNull(raw.getPublishDate());
        String publishDate = null;
        if (publishDateRaw != null)
        {
            LocalDate parsed = parseStrictDate(publishDateRaw);
            if (parsed == null)
            {
                warnings.add("出版日期格式无法识别（" + publishDateRaw + "），将留空");
            }
            else
            {
                publishDate = DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, parsed);
            }
        }

        // ---- 图书类型 ----
        int bookType = BOOK_TYPE_NORMAL;
        String bookTypeRaw = trimToNull(raw.getBookType());
        if (bookTypeRaw != null)
        {
            if ("教材".equals(bookTypeRaw) || "2".equals(bookTypeRaw))
            {
                bookType = BOOK_TYPE_TEXTBOOK;
            }
            else if (!"普通".equals(bookTypeRaw) && !"1".equals(bookTypeRaw))
            {
                warnings.add("图书类型「" + bookTypeRaw + "」无法识别，按「普通」处理");
            }
        }

        // ---- 教材扩展完整性（原型 A-10 校验规则之一） ----
        String major = trimToNull(raw.getMajor());
        String term = trimToNull(raw.getTerm());
        String college = trimToNull(raw.getCollege());
        if (bookType == BOOK_TYPE_TEXTBOOK)
        {
            List<String> lack = new ArrayList<>();
            if (major == null)
            {
                lack.add("适用专业");
            }
            if (term == null)
            {
                lack.add("适用学期");
            }
            if (college == null)
            {
                lack.add("开课院系");
            }
            if (!lack.isEmpty())
            {
                warnings.add("教材扩展信息不完整（缺 " + String.join("、", lack) + "），仅导入图书主档");
            }
        }

        // ---- 归一化快照 ----
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("isbn", isbn);
        snapshot.put("title", title);
        snapshot.put("author", trimToNull(raw.getAuthor()));
        snapshot.put("publisherId", publisherId);
        snapshot.put("publisherName", publisherName);
        snapshot.put("categoryId", categoryId);
        snapshot.put("publishDate", publishDate);
        snapshot.put("bookType", bookType);
        snapshot.put("price", price.toPlainString());
        snapshot.put("memberPrice", memberPrice == null ? null : memberPrice.toPlainString());
        snapshot.put("edition", trimToNull(raw.getEdition()));
        snapshot.put("binding", trimToNull(raw.getBinding()));
        snapshot.put("pageCount", pageCount);
        snapshot.put("summary", trimToNull(raw.getSummary()));
        snapshot.put("major", major);
        snapshot.put("term", term);
        snapshot.put("college", college);
        row.setRawData(snapshot);

        if (warnings.isEmpty())
        {
            row.setRowStatus(BookImportRow.ROW_VALID);
            row.setErrorMsg("");
        }
        else
        {
            row.setRowStatus(BookImportRow.ROW_WARN);
            row.setErrorMsg(joinWarnings(warnings));
        }
        return row;
    }

    private BookImportRow reject(BookImportRow row, String message)
    {
        row.setRowStatus(BookImportRow.ROW_ERROR);
        row.setErrorMsg(message);
        row.setRawData(new LinkedHashMap<>());
        return row;
    }

    private String joinWarnings(List<String> warnings)
    {
        return truncate(String.join("；", warnings));
    }

    // ==================================================================
    // 内部：索引加载
    // ==================================================================

    private Map<String, Long> loadPublisherIndex()
    {
        Map<String, Long> index = new HashMap<>();
        for (Publisher publisher : publisherMapper.selectList(null))
        {
            if (!isBlank(publisher.getPublisherName()))
            {
                index.putIfAbsent(publisher.getPublisherName().trim(), publisher.getPublisherId());
            }
        }
        return index;
    }

    /** 只收二级分类 —— 一级分类是分组，挂上去会让分类树统计失真（与 BookServiceImpl 一致） */
    private Map<String, Long> loadCategoryIndex()
    {
        Map<String, Long> index = new HashMap<>();
        for (Category category : categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .eq(Category::getCategoryLevel, CATEGORY_LEVEL_LEAF)))
        {
            if (!isBlank(category.getCategoryName()))
            {
                index.putIfAbsent(category.getCategoryName().trim(), category.getCategoryId());
            }
        }
        return index;
    }

    private Set<String> loadExistingIsbns(List<BookImportExcelRow> rows)
    {
        Set<String> isbns = new HashSet<>();
        for (BookImportExcelRow row : rows)
        {
            String isbn = IsbnUtils.normalize(row.getIsbn());
            if (!isBlank(isbn))
            {
                isbns.add(isbn);
            }
        }
        if (isbns.isEmpty())
        {
            return Collections.emptySet();
        }
        // 一次 IN 查询而不是逐行查：1000 行的文件逐行查就是 1000 次 SELECT
        return bookMapper.selectList(Wrappers.<Book>lambdaQuery()
                        .select(Book::getIsbn)
                        .in(Book::getIsbn, isbns))
                .stream()
                .map(Book::getIsbn)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Book findBookByIsbn(String isbn)
    {
        if (isBlank(isbn))
        {
            return null;
        }
        return bookMapper.selectOne(Wrappers.<Book>lambdaQuery()
                .eq(Book::getIsbn, isbn)
                .last("limit 1"));
    }

    // ==================================================================
    // 内部：进度与失败明细
    // ==================================================================

    private void refreshProgress(Long batchId, int processed, int total)
    {
        int percent = (total <= 0) ? 100 : (int) (processed * 100L / total);
        baseMapper.update(null, Wrappers.<BookImportBatch>lambdaUpdate()
                .eq(BookImportBatch::getBatchId, batchId)
                .set(BookImportBatch::getProgress, Math.min(percent, 99)));
    }

    /**
     * 把执行失败落到行上（状态刷 3、原因写 error_msg）。
     *
     * <p>这样失败明细**存在库里**而不只在内存里 —— {@code GET /error-file} 才能现算，
     * 不必依赖执行结束时落盘的那份快照文件。
     */
    private void markRowFailed(Long rowId, String reason)
    {
        BookImportRow patch = new BookImportRow();
        patch.setRowId(rowId);
        patch.setRowStatus(BookImportRow.ROW_ERROR);
        patch.setErrorMsg(reason);
        importRowMapper.updateById(patch);
    }

    private void markBatchFailed(Long batchId, String message)
    {
        baseMapper.update(null, Wrappers.<BookImportBatch>lambdaUpdate()
                .eq(BookImportBatch::getBatchId, batchId)
                .set(BookImportBatch::getStatus, BookImportBatch.STATUS_FAILED)
                .set(BookImportBatch::getProgress, 100)
                .set(BookImportBatch::getRemark, truncate("导入失败：" + message)));
    }

    private List<BookImportErrorRow> collectFailures(Long batchId)
    {
        List<BookImportErrorRow> failures = new ArrayList<>();
        List<BookImportRow> rows = importRowMapper.selectList(Wrappers.<BookImportRow>lambdaQuery()
                .eq(BookImportRow::getBatchId, batchId)
                .eq(BookImportRow::getRowStatus, BookImportRow.ROW_ERROR)
                .orderByAsc(BookImportRow::getRowNo));
        for (BookImportRow row : rows)
        {
            failures.add(new BookImportErrorRow(row.getRowNo(), row.getIsbn(), row.getTitle(),
                    row.getErrorMsg()));
        }
        return failures;
    }

    /** 落盘一份失败明细快照，回填 {@code fail_file_url} */
    private String writeErrorFileToDisk(Long batchId, List<BookImportErrorRow> failures)
    {
        BookImportBatch batch = baseMapper.selectById(batchId);
        String batchNo = (batch == null) ? String.valueOf(batchId) : batch.getBatchNo();
        String fileName = "import-error-" + batchNo + ".xlsx";
        File dir = new File(RuoYiConfig.getImportPath());
        if (!dir.exists() && !dir.mkdirs())
        {
            log.warn("图书导入：失败明细目录创建失败 {}", dir.getAbsolutePath());
            return "";
        }
        File target = new File(dir, fileName);
        try
        {
            FastExcel.write(target, BookImportErrorRow.class).sheet(SHEET_DATA).doWrite(failures);
        }
        catch (RuntimeException e)
        {
            log.warn("图书导入：失败明细落盘失败 batchId={} 原因={}", batchId, e.getMessage());
            return "";
        }
        return "/import/" + fileName;
    }

    /**
     * 统一设置下载响应头。
     *
     * <p>{@code FileUtils.setAttachmentResponseHeader} 抛受检的
     * {@code UnsupportedEncodingException}，集中在这里兜住，避免每个下载端点各写一遍 try。
     */
    private void prepareDownload(HttpServletResponse response, String fileName)
    {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        try
        {
            FileUtils.setAttachmentResponseHeader(response, fileName);
        }
        catch (UnsupportedEncodingException e)
        {
            throw BizErrors.serverError("下载文件名编码失败：" + e.getMessage());
        }
    }

    private void writeWorkbook(HttpServletResponse response, String fileName, String sheetName,
            List<BookImportErrorRow> rows)
    {
        prepareDownload(response, fileName + ".xlsx");
        try (ExcelWriter writer = FastExcel.write(response.getOutputStream())
                .autoCloseStream(Boolean.FALSE).build())
        {
            WriteSheet sheet = FastExcel.writerSheet(0, sheetName)
                    .head(BookImportErrorRow.class).build();
            writer.write(rows, sheet);
            writer.finish();
        }
        catch (IOException e)
        {
            throw BizErrors.serverError("失败明细生成失败：" + e.getMessage());
        }
    }

    // ==================================================================
    // 内部：取值与格式
    // ==================================================================

    /** 批次号：IMP + 秒级时间戳 + 6 位随机。唯一索引 uk_batch_no 是最后一道兜底 */
    private String generateBatchNo()
    {
        String stamp = DateUtils.dateTimeNow();
        int random = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return BATCH_NO_PREFIX + stamp + random;
    }

    // ISBN 归一化与格式校验已抽到 {@link com.tbgbook.bookstore.book.support.IsbnUtils} ——
    // ISBN 识别填充（IsbnLookupServiceImpl）要用同一套口径，两处各写一份会漂移：
    // 一旦不一致，就会出现「识别填充接受的 ISBN，导入判为非法」这种自相矛盾的行为

    private BigDecimal toDecimal(String value)
    {
        try
        {
            return new BigDecimal(value.trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private Long toLong(String value)
    {
        try
        {
            return Long.valueOf(value.trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private String asString(Object value)
    {
        return (value == null) ? null : String.valueOf(value);
    }

    private Long asLong(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        return toLong(String.valueOf(value));
    }

    private Integer asInt(Object value)
    {
        Long l = asLong(value);
        return (l == null) ? null : l.intValue();
    }

    private BigDecimal asDecimal(Object value)
    {
        return (value == null) ? null : toDecimal(String.valueOf(value));
    }

    private LocalDate asLocalDate(Object value)
    {
        return (value == null) ? null : parseStrictDate(String.valueOf(value));
    }

    /**
     * 严格解析出版日期。
     *
     * <p>⚠️ **不能用 {@code DateUtils.parseDate}**：它底层是
     * {@code org.apache.commons.lang3.time.DateUtils.parseDate}，默认**宽松模式** ——
     * {@code 2020-13-45}（月份 13、日 45 都是错的）会被「顺延」成 2021-02-14 且**不报错**，
     * 操作员于是拿到一个静默错误的出版日期（实测踩到）。
     *
     * <p>这里关掉宽松模式，并要求**整串匹配**（{@code ParsePosition} 走到底）——
     * 否则 {@code SimpleDateFormat.parse} 会只吃掉能匹配的前缀就返回。
     *
     * <p>2026-09-22：返回值由 {@code java.util.Date} 改为 {@link LocalDate}
     * （`publish_date` 是纯 `date` 列）。严格性靠 {@code SimpleDateFormat.setLenient(false)}
     * 保留 —— {@code DateTimeFormatter} 的 STRICT 模式要求用 {@code uuuu} 而非 {@code yyyy}，
     * 与这里的 {@code DATE_PATTERNS} 字面量不兼容，故仍走 SimpleDateFormat 再转 LocalDate。
     *
     * @param value 待解析文本
     * @return 解析结果；格式不符时返回 {@code null}（调用方降级为警告 + 留空）
     */
    private LocalDate parseStrictDate(String value)
    {
        String text = value.trim();
        for (String pattern : DATE_PATTERNS)
        {
            SimpleDateFormat format = new SimpleDateFormat(pattern);
            format.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date parsed = format.parse(text, position);
            if (parsed != null && position.getIndex() == text.length())
            {
                return DateUtils.toLocalDate(parsed);
            }
        }
        return null;
    }

    private String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }

    private String truncate(String value)
    {
        if (value == null)
        {
            return null;
        }
        return (value.length() > MAX_MSG_LEN) ? value.substring(0, MAX_MSG_LEN) : value;
    }

    /** 把异常转成可回显到失败明细的中文原因（ServiceException 带业务文案，其余兜底） */
    private String resolveReason(Exception e)
    {
        String message = e.getMessage();
        if (message == null || message.isBlank())
        {
            return "导入失败：" + e.getClass().getSimpleName();
        }
        return truncate(message);
    }
}
