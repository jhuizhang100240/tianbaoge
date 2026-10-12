package com.tbgbook.bookstore.ai.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tbgbook.bookstore.ai.domain.KbChunk;
import com.tbgbook.bookstore.ai.domain.KbDoc;
import com.tbgbook.bookstore.ai.domain.KbUploadResult;
import com.tbgbook.bookstore.ai.mapper.KbDocMapper;
import com.tbgbook.bookstore.ai.service.IKbChunkService;

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
}
