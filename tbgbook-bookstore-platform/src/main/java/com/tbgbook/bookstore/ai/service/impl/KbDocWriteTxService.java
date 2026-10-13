package com.tbgbook.bookstore.ai.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tbgbook.bookstore.ai.domain.KbChunk;
import com.tbgbook.bookstore.ai.domain.KbDoc;
import com.tbgbook.bookstore.ai.domain.KbUploadResult;
import com.tbgbook.bookstore.ai.mapper.KbChunkMapper;
import com.tbgbook.bookstore.ai.mapper.KbDocMapper;
import com.tbgbook.bookstore.ai.service.IKbChunkService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;

/**
 * 知识库落库事务边界。
 *
 * <p>独立成 Bean 的原因：上传主流程里有 OSS 远程调用（禁止进事务，见根 CLAUDE.md §6.2），
 * 事务只能包住「插文档 + 批插切片 + 回填 chunk_total」这一小段；同类内部方法调用不走代理，
 * 故拆成独立事务服务（与后端 CLAUDE.md 的「事务失效要绕开」约定一致）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class KbDocWriteTxService
{
    private final KbDocMapper kbDocMapper;

    private final KbChunkMapper kbChunkMapper;

    private final IKbChunkService kbChunkService;

    @Transactional(rollbackFor = Exception.class)
    public KbUploadResult persist(KbDoc kbDoc, List<KbChunk> chunks)
    {
        kbDocMapper.insert(kbDoc);
        for (KbChunk chunk : chunks)
        {
            chunk.setDocId(kbDoc.getDocId());
        }
        kbChunkService.saveBatch(chunks, 500);
        kbDoc.setChunkTotal(chunks.size());
        kbDocMapper.updateById(kbDoc);
        return new KbUploadResult(kbDoc.getDocId(), kbDoc.getIndexStatus(), chunks.size());
    }

    /**
     * 重建代次落库：插入新切片行（旧行保持，检索由 Qdrant active 控制），文档回待处理。
     */
    @Transactional(rollbackFor = Exception.class)
    public KbUploadResult stageRebuild(KbDoc doc, List<KbChunk> chunks, int chunkSize, int chunkOverlap)
    {
        for (KbChunk chunk : chunks)
        {
            chunk.setDocId(doc.getDocId());
        }
        kbChunkService.saveBatch(chunks, 500);
        KbDoc update = new KbDoc();
        update.setDocId(doc.getDocId());
        update.setChunkSize(chunkSize);
        update.setChunkOverlap(chunkOverlap);
        update.setChunkTotal(chunks.size());
        update.setChunkOk(0);
        update.setChunkFailed(0);
        update.setIndexStatus(1);
        update.setFailReason("");
        kbDocMapper.updateById(update);
        return new KbUploadResult(doc.getDocId(), 1, chunks.size());
    }

    /**
     * 索引激活：删除本代次之外的切片行（补集删除，覆盖历史残留），文档置已完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public void activate(long docId, List<Long> keepChunkIds, int total, int ok, int failed)
    {
        if (!keepChunkIds.isEmpty())
        {
            kbChunkMapper.delete(new LambdaQueryWrapper<KbChunk>()
                    .eq(KbChunk::getDocId, docId)
                    .notIn(KbChunk::getChunkId, keepChunkIds));
        }
        KbDoc update = new KbDoc();
        update.setDocId(docId);
        update.setChunkTotal(total);
        update.setChunkOk(ok);
        update.setChunkFailed(failed);
        update.setIndexStatus(3);
        update.setIndexTime(java.time.LocalDateTime.now());
        update.setFailReason("");
        kbDocMapper.updateById(update);
    }
}
