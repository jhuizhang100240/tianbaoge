package com.tbgbook.bookstore.ai.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.ai.core.AiRedisKeys;
import com.tbgbook.bookstore.ai.domain.KbChunk;
import com.tbgbook.bookstore.ai.domain.KbDoc;
import com.tbgbook.bookstore.ai.domain.KbReindexRequest;
import com.tbgbook.bookstore.ai.domain.KbUploadRequest;
import com.tbgbook.bookstore.ai.domain.KbUploadResult;
import com.tbgbook.bookstore.ai.index.Chunker;
import com.tbgbook.bookstore.ai.index.DocumentParserRegistry;
import com.tbgbook.bookstore.ai.mapper.KbChunkMapper;
import com.tbgbook.bookstore.ai.mapper.KbDocMapper;
import com.tbgbook.bookstore.ai.service.IKbDocService;
import com.tbgbook.bookstore.ai.vector.QdrantClient;
import com.tbgbook.bookstore.ai.vector.QdrantException;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.common.exception.ServiceException;
import com.tbgbook.system.service.ISysConfigService;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;

/**
 * 知识库文档 Service 实现（同步解析+切片，异步向量化由 Task 5 的索引扫描接管）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class KbDocServiceImpl extends ServiceImpl<KbDocMapper, KbDoc> implements IKbDocService
{
    private static final Logger log = LoggerFactory.getLogger(KbDocServiceImpl.class);

    private static final long STORE_ID = 1L;

    /** 契约：单文件 ≤ 20MB（容器层已放宽到 25MB 以便给出确定的 413） */
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    private static final Set<String> ALLOWED_FILE_TYPES = Set.of("pdf", "docx", "txt", "md");

    private final KbDocWriteTxService writeTxService;

    private final KbChunkMapper kbChunkMapper;

    private final DocumentParserRegistry parserRegistry;

    private final Chunker chunker;

    private final com.tbgbook.bookstore.ai.index.KbFileStorage kbFileStorage;

    private final ISysConfigService configService;

    private final QdrantClient qdrantClient;

    private final RedissonClient redissonClient;

    /** 解析专用线程池：把 CPU 密集解析从 Web 线程隔离，并支撑超时取消 */
    private final ExecutorService parseExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "kb-parse");
        thread.setDaemon(true);
        return thread;
    });

    @PreDestroy
    public void shutdownParseExecutor()
    {
        parseExecutor.shutdown();
    }

    @Override
    public List<KbDoc> selectKbDocList(KbDoc query)
    {
        LambdaQueryWrapper<KbDoc> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbDoc::getStoreId, STORE_ID);
        if (query != null)
        {
            wrapper.like(StringUtils.hasText(query.getDocName()), KbDoc::getDocName, query.getDocName());
            wrapper.eq(query.getDocType() != null, KbDoc::getDocType, query.getDocType());
            wrapper.eq(query.getIndexStatus() != null, KbDoc::getIndexStatus, query.getIndexStatus());
            wrapper.eq(query.getStatus() != null, KbDoc::getStatus, query.getStatus());
            wrapper.eq(StringUtils.hasText(query.getFileType()), KbDoc::getFileType, query.getFileType());
        }
        wrapper.orderByDesc(KbDoc::getDocId);
        return list(wrapper);
    }

    @Override
    public KbUploadResult upload(KbUploadRequest request)
    {
        validateUpload(request);
        String fileType = extension(request.getFile().getOriginalFilename());
        int chunkSize = request.getChunkSize() != null ? request.getChunkSize()
                : intConfig("biz.ai.kb.chunk.size", 500);
        int chunkOverlap = request.getChunkOverlap() != null ? request.getChunkOverlap()
                : intConfig("biz.ai.kb.chunk.overlap", 50);
        if (chunkSize <= chunkOverlap || chunkOverlap < 0)
        {
            throw BizErrors.ruleViolated("切片长度必须大于重叠长度");
        }

        byte[] content;
        try
        {
            content = request.getFile().getBytes();
        }
        catch (IOException e)
        {
            throw BizErrors.serverError("文件读取失败，请重试");
        }

        // 存储（OSS 或本地目录）在事务外
        String fileUrl = kbFileStorage.store(content, request.getFile().getOriginalFilename());

        // 同步解析 + 切片（受超时约束）
        ParsedText parsed = parseWithTimeout(fileType, content, chunkSize, chunkOverlap);
        if (!StringUtils.hasText(parsed.text()) || parsed.chunks().isEmpty())
        {
            throw BizErrors.ruleViolated("文档内容为空或无法解析");
        }

        KbDoc kbDoc = new KbDoc();
        kbDoc.setStoreId(STORE_ID);
        kbDoc.setDocName(request.getDocName());
        kbDoc.setDocType(request.getDocType());
        kbDoc.setFileUrl(fileUrl);
        kbDoc.setFileType(fileType);
        kbDoc.setFileSize(request.getFile().getSize());
        kbDoc.setVersion(StringUtils.hasText(request.getVersion()) ? request.getVersion() : "v1");
        kbDoc.setEffectiveScope(request.getEffectiveScope() == null ? "" : request.getEffectiveScope());
        kbDoc.setChunkSize(chunkSize);
        kbDoc.setChunkOverlap(chunkOverlap);
        kbDoc.setChunkTotal(0);
        kbDoc.setChunkOk(0);
        kbDoc.setChunkFailed(0);
        kbDoc.setIndexStatus(1);
        kbDoc.setFailReason("");
        kbDoc.setStatus(1);

        return writeTxService.persist(kbDoc, buildChunks(parsed.chunks()));
    }

    @Override
    public KbUploadResult reindex(Long docId, KbReindexRequest request)
    {
        if (docId == null)
        {
            throw BizErrors.badRequest("文档ID不能为空");
        }
        KbDoc doc = getById(docId);
        if (doc == null)
        {
            throw BizErrors.notFound("文档", docId);
        }
        // 待处理(1)也算「正在索引中」：避免在队列期间再叠一代次（契约 409）
        if (Integer.valueOf(1).equals(doc.getIndexStatus()) || Integer.valueOf(2).equals(doc.getIndexStatus()))
        {
            throw BizErrors.conflict("文档正在索引中");
        }
        int chunkSize = request != null && request.getChunkSize() != null ? request.getChunkSize()
                : (doc.getChunkSize() != null ? doc.getChunkSize() : intConfig("biz.ai.kb.chunk.size", 500));
        int chunkOverlap = request != null && request.getChunkOverlap() != null ? request.getChunkOverlap()
                : (doc.getChunkOverlap() != null ? doc.getChunkOverlap()
                        : intConfig("biz.ai.kb.chunk.overlap", 50));
        if (chunkSize <= chunkOverlap || chunkOverlap < 0)
        {
            throw BizErrors.ruleViolated("切片长度必须大于重叠长度");
        }
        boolean force = request != null && Boolean.TRUE.equals(request.getForce());
        boolean sameParams = doc.getChunkSize() != null && doc.getChunkOverlap() != null
                && chunkSize == doc.getChunkSize() && chunkOverlap == doc.getChunkOverlap();
        boolean noFailures = doc.getChunkFailed() == null || doc.getChunkFailed() == 0;
        if (!force && sameParams && Integer.valueOf(3).equals(doc.getIndexStatus()) && noFailures)
        {
            return new KbUploadResult(docId, doc.getIndexStatus(), doc.getChunkTotal());
        }

        byte[] content = kbFileStorage.read(doc.getFileUrl());
        ParsedText parsed = parseWithTimeout(doc.getFileType(), content, chunkSize, chunkOverlap);
        if (!StringUtils.hasText(parsed.text()) || parsed.chunks().isEmpty())
        {
            throw BizErrors.ruleViolated("文档内容为空或无法解析");
        }
        return writeTxService.stageRebuild(doc, buildChunks(parsed.chunks()), chunkSize, chunkOverlap);
    }

    @Override
    public int addKbDoc(KbDoc kbDoc)
    {
        if (kbDoc == null || !StringUtils.hasText(kbDoc.getDocName()))
        {
            throw BizErrors.badRequest("文档名称不能为空");
        }
        if (kbDoc.getDocType() == null || kbDoc.getDocType() < 1 || kbDoc.getDocType() > 5)
        {
            throw BizErrors.badRequest("文档类型取值 1-5");
        }
        int chunkSize = kbDoc.getChunkSize() != null ? kbDoc.getChunkSize()
                : intConfig("biz.ai.kb.chunk.size", 500);
        int chunkOverlap = kbDoc.getChunkOverlap() != null ? kbDoc.getChunkOverlap()
                : intConfig("biz.ai.kb.chunk.overlap", 50);
        if (chunkSize <= chunkOverlap || chunkOverlap < 0)
        {
            throw BizErrors.ruleViolated("切片长度必须大于重叠长度");
        }
        kbDoc.setDocId(null);
        kbDoc.setStoreId(STORE_ID);
        kbDoc.setChunkSize(chunkSize);
        kbDoc.setChunkOverlap(chunkOverlap);
        kbDoc.setChunkTotal(0);
        kbDoc.setChunkOk(0);
        kbDoc.setChunkFailed(0);
        kbDoc.setIndexStatus(1);
        kbDoc.setFailReason("");
        kbDoc.setStatus(kbDoc.getStatus() == null ? 1 : kbDoc.getStatus());
        kbDoc.setDelFlag(null);
        return save(kbDoc) ? 1 : 0;
    }

    @Override
    public int updateKbDoc(KbDoc kbDoc)
    {
        if (kbDoc == null || kbDoc.getDocId() == null)
        {
            throw BizErrors.badRequest("文档ID不能为空");
        }
        KbDoc existing = getById(kbDoc.getDocId());
        if (existing == null)
        {
            throw BizErrors.notFound("文档", kbDoc.getDocId());
        }
        if (kbDoc.getDocName() != null && !StringUtils.hasText(kbDoc.getDocName()))
        {
            throw BizErrors.badRequest("文档名称不能为空");
        }
        if (kbDoc.getDocType() != null && (kbDoc.getDocType() < 1 || kbDoc.getDocType() > 5))
        {
            throw BizErrors.badRequest("文档类型取值 1-5");
        }
        if (kbDoc.getStatus() != null && kbDoc.getStatus() != 1 && kbDoc.getStatus() != 2)
        {
            throw BizErrors.badRequest("状态取值 1启用 / 2停用");
        }

        // 只允许契约内的可写字段；其余显式清空避免客户端注入
        kbDoc.setStoreId(null);
        kbDoc.setVersion(null);
        kbDoc.setChunkTotal(null);
        kbDoc.setChunkOk(null);
        kbDoc.setChunkFailed(null);
        kbDoc.setIndexStatus(null);
        kbDoc.setIndexTime(null);
        kbDoc.setFailReason(null);
        kbDoc.setDelFlag(null);

        // 停用/启用先同步 Qdrant 可见性，再落库（失败则不落库，保持可重试）
        Integer newStatus = kbDoc.getStatus();
        if (newStatus != null && !newStatus.equals(existing.getStatus()))
        {
            try
            {
                if (newStatus == 2)
                {
                    qdrantClient.setPayloadActiveForDoc(kbDoc.getDocId(), false);
                }
                else
                {
                    Object gen = redissonClient.getBucket(AiRedisKeys.currentGen(kbDoc.getDocId())).get();
                    if (gen instanceof Number)
                    {
                        qdrantClient.setPayloadActive(kbDoc.getDocId(), ((Number) gen).longValue(), true);
                    }
                    else
                    {
                        log.warn("current gen missing for doc {}; skip qdrant re-enable, retrieval recheck covers it",
                                kbDoc.getDocId());
                    }
                }
            }
            catch (QdrantException e)
            {
                throw BizErrors.serviceUnavailable("向量库暂时不可用，状态未修改");
            }
        }
        return updateById(kbDoc) ? 1 : 0;
    }

    @Override
    public int removeKbDocs(Long[] docIds)
    {
        if (docIds == null || docIds.length == 0)
        {
            throw BizErrors.badRequest("请选择要删除的文档");
        }
        int count = 0;
        for (Long docId : docIds)
        {
            KbDoc existing = getById(docId);
            if (existing == null)
            {
                throw BizErrors.notFound("文档", docId);
            }
            if (Integer.valueOf(2).equals(existing.getIndexStatus()))
            {
                throw BizErrors.conflict("文档正在索引中，不能删除");
            }
            try
            {
                qdrantClient.deleteByDoc(docId);
            }
            catch (QdrantException e)
            {
                throw BizErrors.serviceUnavailable("向量库暂时不可用，文档未删除");
            }
            kbFileStorage.delete(existing.getFileUrl());
            kbChunkMapper.delete(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocId, docId));
            removeById(docId);
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private void validateUpload(KbUploadRequest request)
    {
        if (request == null || request.getFile() == null)
        {
            throw BizErrors.badRequest("请选择要上传的文档");
        }
        if (!StringUtils.hasText(request.getDocName()))
        {
            throw BizErrors.badRequest("文档名称不能为空");
        }
        if (request.getDocType() == null || request.getDocType() < 1 || request.getDocType() > 5)
        {
            throw BizErrors.badRequest("文档类型取值 1-5");
        }
        long size = request.getFile().getSize();
        if (size <= 0)
        {
            throw BizErrors.ruleViolated("文档内容为空或无法解析");
        }
        if (size > MAX_FILE_SIZE)
        {
            throw BizErrors.payloadTooLarge("单个文档不能超过 20MB");
        }
        String fileType = extension(request.getFile().getOriginalFilename());
        if (!ALLOWED_FILE_TYPES.contains(fileType))
        {
            throw BizErrors.ruleViolated("不支持的文件类型：" + (fileType.isEmpty() ? "未知" : fileType));
        }
    }

    private List<KbChunk> buildChunks(List<String> pieces)
    {
        List<KbChunk> chunks = new ArrayList<>(pieces.size());
        int seqNo = 1;
        for (String piece : pieces)
        {
            KbChunk chunk = new KbChunk();
            chunk.setSeqNo(seqNo++);
            chunk.setContent(piece);
            chunk.setCharCount(piece.length());
            chunk.setVectorId(UUID.randomUUID().toString());
            chunk.setVectorStatus(0);
            chunk.setFailReason("");
            chunk.setHitCount(0);
            chunks.add(chunk);
        }
        return chunks;
    }

    private ParsedText parseWithTimeout(String fileType, byte[] content, int chunkSize, int chunkOverlap)
    {
        int timeoutSeconds = intConfig("biz.ai.kb.parse.timeout.seconds", 60);
        CompletableFuture<ParsedText> future = CompletableFuture.supplyAsync(() -> {
            String text = parserRegistry.parse(fileType, content);
            return new ParsedText(text, chunker.chunk(text, chunkSize, chunkOverlap));
        }, parseExecutor);
        try
        {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        }
        catch (TimeoutException e)
        {
            future.cancel(true);
            throw BizErrors.ruleViolated("解析超时，请拆分文档后重试");
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw BizErrors.serverError("解析被中断，请重试");
        }
        catch (ExecutionException e)
        {
            Throwable cause = e.getCause();
            if (cause instanceof ServiceException)
            {
                throw (ServiceException) cause;
            }
            log.error("kb parse failed", cause);
            throw BizErrors.ruleViolated("文档内容为空或无法解析");
        }
    }

    private int intConfig(String key, int defaultValue)
    {
        String raw = configService.selectConfigByKey(key);
        if (!StringUtils.hasText(raw))
        {
            return defaultValue;
        }
        try
        {
            return Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException e)
        {
            log.warn("sys_config {} is not an integer: {}", key, raw);
            return defaultValue;
        }
    }

    private String extension(String filename)
    {
        if (filename == null)
        {
            return "";
        }
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index + 1).toLowerCase();
    }

    private record ParsedText(String text, List<String> chunks)
    {
    }
}
