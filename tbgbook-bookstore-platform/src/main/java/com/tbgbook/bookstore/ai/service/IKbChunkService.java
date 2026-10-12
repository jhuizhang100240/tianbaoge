package com.tbgbook.bookstore.ai.service;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.ai.domain.KbChunk;

/**
 * 知识库切片 Service（含人工维护语义：改内容即重嵌入、删除即删点）。
 *
 * @author tbgbook
 */
public interface IKbChunkService extends IService<KbChunk>
{
    /**
     * 按文档查切片（A-72）；可过滤向量化状态与「命中 0 次」。
     */
    List<KbChunk> selectChunkList(Long docId, Integer vectorStatus, boolean onlyZeroHit);

    /**
     * 汇总：total / ok / failed / zeroHit。
     */
    Map<String, Integer> summarize(Long docId);

    /**
     * 手工补切片（vectorStatus=0，等待索引扫描嵌入）。
     */
    void addChunk(KbChunk chunk);

    /**
     * 修改切片：内容变更 → 删旧点 + 置待处理；同步更新文档切片总数。
     */
    void updateChunk(KbChunk chunk);

    /**
     * 删除切片：先删 Qdrant 点（失败 503），再物理删行；同步更新文档切片总数。
     */
    void removeChunks(Long[] chunkIds);
}
