package com.tbgbook.bookstore.ai.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.ai.domain.KbDoc;
import com.tbgbook.bookstore.ai.domain.KbUploadRequest;
import com.tbgbook.bookstore.ai.service.IKbChunkService;
import com.tbgbook.bookstore.ai.service.IKbDocService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 知识库文档 Controller（`/ai/kb`，权限串与接口契约逐条对齐）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/kb")
public class KbDocController extends BaseController
{
    private final IKbDocService kbDocService;

    private final IKbChunkService kbChunkService;

    /**
     * 查询知识库文档列表。
     */
    @PreAuthorize("@ss.hasPermi('ai:kb:list')")
    @GetMapping("/list")
    public TableDataInfo list(KbDoc kbDoc)
    {
        startPage();
        return getDataTable(kbDocService.selectKbDocList(kbDoc));
    }

    /**
     * 查询文档详情。
     */
    @PreAuthorize("@ss.hasPermi('ai:kb:list')")
    @GetMapping("/{docId}")
    public AjaxResult getInfo(@PathVariable("docId") Long docId)
    {
        return success(kbDocService.getById(docId));
    }

    /**
     * 新增文档（管理端手工建档，不做解析）。
     */
    @PreAuthorize("@ss.hasPermi('ai:kb:add')")
    @Log(title = "知识库文档", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody KbDoc kbDoc)
    {
        return toAjax(kbDocService.addKbDoc(kbDoc));
    }

    /**
     * 修改文档（含停用/启用的向量可见性同步）。
     */
    @PreAuthorize("@ss.hasPermi('ai:kb:edit')")
    @Log(title = "知识库文档", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody KbDoc kbDoc)
    {
        return toAjax(kbDocService.updateKbDoc(kbDoc));
    }

    /**
     * 删除文档（先删向量点与 OSS 对象，再逻辑删文档、物理删切片）。
     */
    @PreAuthorize("@ss.hasPermi('ai:kb:remove')")
    @Log(title = "知识库文档", businessType = BusinessType.DELETE)
    @DeleteMapping("/{docIds}")
    public AjaxResult remove(@PathVariable("docIds") Long[] docIds)
    {
        return toAjax(kbDocService.removeKbDocs(docIds));
    }

    /**
     * 上传文档：multipart 校验 → OSS 存储 → 同步解析+切片 → 落库待向量化。
     */
    @PreAuthorize("@ss.hasPermi('ai:kb')")
    @Log(title = "知识库文档", businessType = BusinessType.INSERT)
    @PostMapping("/upload")
    public AjaxResult upload(KbUploadRequest request)
    {
        return success(kbDocService.upload(request));
    }

    /**
     * 查看文档切片（A-72）：列表 + 汇总（成功/失败/零命中）。
     */
    @PreAuthorize("@ss.hasPermi('ai:kb')")
    @GetMapping("/{docId}/chunks")
    public AjaxResult chunks(@PathVariable("docId") Long docId,
            @RequestParam(value = "vectorStatus", required = false) Integer vectorStatus,
            @RequestParam(value = "onlyZeroHit", required = false, defaultValue = "false") Boolean onlyZeroHit)
    {
        Map<String, Object> data = new HashMap<>(2);
        data.put("list", kbChunkService.selectChunkList(docId, vectorStatus, Boolean.TRUE.equals(onlyZeroHit)));
        data.put("summary", kbChunkService.summarize(docId));
        return success(data);
    }
}
