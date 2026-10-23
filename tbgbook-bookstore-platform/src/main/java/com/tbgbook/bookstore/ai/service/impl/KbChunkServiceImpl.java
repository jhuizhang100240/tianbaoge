package com.tbgbook.bookstore.ai.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.tbgbook.bookstore.ai.domain.KbChunk;
import com.tbgbook.bookstore.ai.domain.KbDoc;
import com.tbgbook.bookstore.ai.mapper.KbChunkMapper;
import com.tbgbook.bookstore.ai.mapper.KbDocMapper;
import com.tbgbook.bookstore.ai.service.IKbChunkService;
import com.tbgbook.bookstore.ai.vector.QdrantClient;
import com.tbgbook.bookstore.ai.vector.QdrantException;
import com.tbgbook.bookstore.common.exception.BizErrors;

import lombok.RequiredArgsConstructor;

/**
 * 知识库切片 Service 实现（维护语义：保持 MySQL 与 Qdrant 一致）。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class KbChunkServiceImpl extends ServiceImpl<KbChunkMapper, KbChunk> implements IKbChunkService
{
    private final KbDocMapper kbDocMapper;

    private final QdrantClient qdrantClient;

    @Override
    public List<KbChunk> selectChunkList(Long docId, Integer vectorStatus, boolean onlyZeroHit)
    {
        if (docId == null)
        {
            throw BizErrors.badRequest("文档ID不能为空");
        }
        LambdaQueryWrapper<KbChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbChunk::getDocId, docId);
        wrapper.eq(vectorStatus != null, KbChunk::getVectorStatus, vectorStatus);
        wrapper.eq(onlyZeroHit, KbChunk::getHitCount, 0);
        wrapper.orderByAsc(KbChunk::getSeqNo);
        return list(wrapper);
    }

    @Override
    public Map<String, Integer> summarize(Long docId)
    {
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("total", Math.toIntExact(count(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocId, docId))));
        summary.put("ok", Math.toIntExact(count(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getDocId, docId).eq(KbChunk::getVectorStatus, 1))));
        summary.put("failed", Math.toIntExact(count(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getDocId, docId).eq(KbChunk::getVectorStatus, 2))));
        summary.put("zeroHit", Math.toIntExact(count(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getDocId, docId).eq(KbChunk::getHitCount, 0))));
        return summary;
    }

    @Override
    public void addChunk(KbChunk chunk)
    {
        if (chunk == null || chunk.getDocId() == null)
        {
            throw BizErrors.badRequest("文档ID不能为空");
        }
        if (!StringUtils.hasText(chunk.getContent()))
        {
            throw BizErrors.badRequest("切片内容不能为空");
        }
        if (kbDocMapper.selectById(chunk.getDocId()) == null)
        {
            throw BizErrors.notFound("文档", chunk.getDocId());
        }
        chunk.setChunkId(null);
        if (chunk.getSeqNo() == null)
        {
            chunk.setSeqNo(nextSeqNo(chunk.getDocId()));
        }
        chunk.setCharCount(chunk.getContent().length());
        chunk.setVectorId(UUID.randomUUID().toString());
        chunk.setVectorStatus(0);
        chunk.setFailReason("");
        chunk.setHitCount(0);
        save(chunk);
        syncDocTotal(chunk.getDocId());
        markDocPending(chunk.getDocId());
    }

    @Override
    public void updateChunk(KbChunk chunk)
    {
        if (chunk == null || chunk.getChunkId() == null)
        {
            throw BizErrors.badRequest("切片ID不能为空");
        }
        KbChunk existing = getById(chunk.getChunkId());
        if (existing == null)
        {
            throw BizErrors.notFound("切片", chunk.getChunkId());
        }
        chunk.setDocId(null);
        boolean contentChanged = StringUtils.hasText(chunk.getContent())
                && !chunk.getContent().equals(existing.getContent());
        if (contentChanged)
        {
            deleteVector(existing.getVectorId());
            chunk.setCharCount(chunk.getContent().length());
            chunk.setVectorId(UUID.randomUUID().toString());
            chunk.setVectorStatus(0);
            chunk.setFailReason("");
        }
        else
        {
            chunk.setContent(null);
            chunk.setCharCount(null);
            chunk.setVectorId(null);
            chunk.setVectorStatus(null);
            chunk.setFailReason(null);
        }
        chunk.setHitCount(null);
        updateById(chunk);
        syncDocTotal(existing.getDocId());
        if (contentChanged)
        {
            markDocPending(existing.getDocId());
        }
    }

    @Override
    public void removeChunks(Long[] chunkIds)
    {
        if (chunkIds == null || chunkIds.length == 0)
        {
            throw BizErrors.badRequest("请选择要删除的切片");
        }
        for (Long chunkId : chunkIds)
        {
            KbChunk existing = getById(chunkId);
            if (existing == null)
            {
                throw BizErrors.notFound("切片", chunkId);
            }
            deleteVector(existing.getVectorId());
            baseMapper.deleteById(chunkId);
            syncDocTotal(existing.getDocId());
        }
    }

    private void deleteVector(String vectorId)
    {
        if (!StringUtils.hasText(vectorId))
        {
            return;
        }
        try
        {
            qdrantClient.deleteByPointIds(List.of(vectorId));
        }
        catch (QdrantException e)
        {
            throw BizErrors.serviceUnavailable("向量库暂时不可用，切片未修改");
        }
    }

    private int nextSeqNo(Long docId)
    {
        KbChunk last = getOne(new LambdaQueryWrapper<KbChunk>()
                .eq(KbChunk::getDocId, docId)
                .orderByDesc(KbChunk::getSeqNo)
                .last("limit 1"), false);
        return last == null || last.getSeqNo() == null ? 1 : last.getSeqNo() + 1;
    }

    private void syncDocTotal(Long docId)
    {
        long total = count(new LambdaQueryWrapper<KbChunk>().eq(KbChunk::getDocId, docId));
        KbDoc update = new KbDoc();
        update.setDocId(docId);
        update.setChunkTotal(Math.toIntExact(total));
        kbDocMapper.updateById(update);
    }

    /**
     * 手工新增/改动的切片需要重新向量化：把文档置回待处理，交给索引扫描领取。
     */
    private void markDocPending(Long docId)
    {
        KbDoc update = new KbDoc();
        update.setDocId(docId);
        update.setIndexStatus(1);
        update.setFailReason("");
        kbDocMapper.updateById(update);
    }
}
