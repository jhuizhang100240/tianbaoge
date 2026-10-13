package com.tbgbook.bookstore.ai.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.ai.domain.KbChunk;
import com.tbgbook.bookstore.ai.index.IndexingPipeline;
import com.tbgbook.bookstore.ai.service.IKbChunkService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 知识库切片 Controller（维护语义：改内容即重嵌入、删除即删点）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/kb/chunk")
public class KbChunkController extends BaseController
{
    private final IKbChunkService kbChunkService;

    private final IndexingPipeline indexingPipeline;

    /**
     * 切片列表（分页）。
     */
    @PreAuthorize("@ss.hasPermi('ai:kb:list')")
    @GetMapping("/list")
    public TableDataInfo list(KbChunk kbChunk)
    {
        startPage();
        return getDataTable(kbChunkService.selectChunkList(kbChunk.getDocId(), kbChunk.getVectorStatus(), false));
    }

    /**
     * 切片详情。
     */
    @PreAuthorize("@ss.hasPermi('ai:kb:list')")
    @GetMapping("/{chunkId}")
    public AjaxResult getInfo(@PathVariable("chunkId") Long chunkId)
    {
        return success(kbChunkService.getById(chunkId));
    }

    /**
     * 手工补切片（落 vectorStatus=0 待嵌入）。
     */
    @PreAuthorize("@ss.hasPermi('ai:kb:add')")
    @Log(title = "知识库切片", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody KbChunk kbChunk)
    {
        kbChunkService.addChunk(kbChunk);
        return success();
    }

    /**
     * 修改切片（内容变更 → 删旧点并重新排队向量化）。
     */
    @PreAuthorize("@ss.hasPermi('ai:kb:edit')")
    @Log(title = "知识库切片", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody KbChunk kbChunk)
    {
        kbChunkService.updateChunk(kbChunk);
        return success();
    }

    /**
     * 删除切片（先删 Qdrant 点）。
     */
    @PreAuthorize("@ss.hasPermi('ai:kb:remove')")
    @Log(title = "知识库切片", businessType = BusinessType.DELETE)
    @DeleteMapping("/{chunkIds}")
    public AjaxResult remove(@PathVariable("chunkIds") Long[] chunkIds)
    {
        kbChunkService.removeChunks(chunkIds);
        return success();
    }

    /**
     * 重试向量化（仅失败切片；成功后若文档无剩余失败则自动激活）。
     */
    @PreAuthorize("@ss.hasPermi('ai:kb')")
    @Log(title = "知识库切片", businessType = BusinessType.UPDATE)
    @PostMapping("/{chunkId}/retry")
    public AjaxResult retry(@PathVariable("chunkId") Long chunkId)
    {
        return success(indexingPipeline.retryChunk(chunkId));
    }
}
