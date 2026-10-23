package com.tbgbook.bookstore.book.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

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
import org.springframework.web.multipart.MultipartFile;

import com.tbgbook.bookstore.book.domain.BookImportBatch;
import com.tbgbook.bookstore.book.domain.BookImportExecuteRequest;
import com.tbgbook.bookstore.book.domain.excel.BookImportParseResult;
import com.tbgbook.bookstore.book.service.IBookImportBatchService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 图书批量导入批次 Controller（页面 A-10 图书批量导入）。
 *
 * <p>本资源在接口文档里是 **5 个 CRUD 端点 + 4 个业务动作端点**，动作端点全部挂在
 * 同一路径前缀下（{@code /book/import/...}），因此 9 个端点共用一个 Controller。
 * ⚠️ {@code GET /template} 与 {@code GET /{batchId}} 同前缀不同特异性 ——
 * Spring 的 PathPattern 匹配按特异性排序，字面量段优先于变量段，不会把
 * {@code template} 当成 batchId。
 *
 * <p><b>两步式导入</b>：{@code POST /parse} 解析校验并返回逐行结果（人工确认），
 * {@code POST /{batchId}/execute} 才真正落库，且**异步执行** —— 提交后返回快照，
 * 真实结果轮询 {@code GET /{batchId}}。
 *
 * <p>相对代码生成器产物的**人工裁剪点**：
 * <ol>
 *   <li>补 4 个业务动作端点（生成器只产出 CRUD）</li>
 *   <li>详情权限串由 {@code :query} 改为 {@code :list}</li>
 *   <li>补 {@code @Validated} / {@code @Valid}（NFR-M2）</li>
 * </ol>
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/book/import")
public class BookImportBatchController extends BaseController
{
    private final IBookImportBatchService importBatchService;

    /**
     * 查询导入批次列表。
     */
    @PreAuthorize("@ss.hasPermi('book:import:list')")
    @GetMapping("/list")
    public TableDataInfo list(BookImportBatch batch)
    {
        startPage();
        List<BookImportBatch> list = importBatchService.selectImportBatchList(batch);
        return getDataTable(list);
    }

    /**
     * 获取导入批次详细信息（执行阶段前端轮询本接口拿 {@code progress}）。
     */
    @PreAuthorize("@ss.hasPermi('book:import:list')")
    @GetMapping("/{batchId}")
    public AjaxResult getInfo(@PathVariable("batchId") Long batchId)
    {
        return success(importBatchService.selectImportBatchById(batchId));
    }

    /**
     * 新增导入批次（手工登记，不含明细行）。
     */
    @PreAuthorize("@ss.hasPermi('book:import:add')")
    @Log(title = "图书批量导入", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody BookImportBatch batch)
    {
        return toAjax(importBatchService.addImportBatch(batch));
    }

    /**
     * 修改导入批次（只能改描述性字段，计数列由服务端独占）。
     */
    @PreAuthorize("@ss.hasPermi('book:import:edit')")
    @Log(title = "图书批量导入", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody BookImportBatch batch)
    {
        return toAjax(importBatchService.updateImportBatch(batch));
    }

    /**
     * 删除导入批次（**主从级联**：明细行一并删除）。
     */
    @PreAuthorize("@ss.hasPermi('book:import:remove')")
    @Log(title = "图书批量导入", businessType = BusinessType.DELETE)
    @DeleteMapping("/{batchIds}")
    public AjaxResult remove(@PathVariable Long[] batchIds)
    {
        return toAjax(importBatchService.removeImportBatches(batchIds));
    }

    /**
     * 第一步：上传并解析 Excel（只解析校验，**不写图书**）。
     *
     * <p>响应 {@code data} 为 {@link BookImportParseResult}：汇总计数 + 逐行校验结果，
     * 供页面步骤③的预览表格渲染。
     *
     * <p>重复 ISBN 策略取 {@code importStrategy}（接口文档声明，1跳过 / 2覆盖更新）。
     * 另外**兼容若依 {@code ExcelImportDialog} 组件的 {@code updateSupport} 参数**
     * （0→跳过、1→覆盖更新）—— 该组件把参数名写死在 URL 拼接里，
     * 不接受它就只能在 A-10 页面另写一个上传组件。两者同时传时以 {@code importStrategy} 为准。
     *
     * <p>⚠️ {@code @PreAuthorize} 在 {@code @Valid} 之后执行，因此参数非法的请求
     * 即便调用者无权限也会先拿到 400 而不是 403。
     */
    @PreAuthorize("@ss.hasPermi('book:import:parse')")
    @Log(title = "图书批量导入", businessType = BusinessType.IMPORT)
    @PostMapping("/parse")
    public AjaxResult parse(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "importStrategy", required = false) Integer importStrategy,
            @RequestParam(value = "updateSupport", required = false) Integer updateSupport)
    {
        Integer strategy = (importStrategy != null) ? importStrategy
                : (updateSupport == null ? null : (updateSupport == 1 ? 2 : 1));
        return success(importBatchService.parseExcel(file, strategy));
    }

    /**
     * 第二步：提交执行（**异步**）。
     *
     * <p>返回的是提交瞬间的快照（{@code status=3}、{@code progress=0}、
     * {@code successRows=0}），真实结果靠轮询 {@code GET /book/import/{batchId}}。
     *
     * <p>批次状态不是 2校验完成时返回 422；并发重复提交时，只有第一次能拿到状态位。
     */
    @PreAuthorize("@ss.hasPermi('book:import:execute')")
    @Log(title = "图书批量导入", businessType = BusinessType.IMPORT)
    @PostMapping("/{batchId}/execute")
    public AjaxResult execute(@PathVariable("batchId") Long batchId,
            @RequestBody(required = false) BookImportExecuteRequest request)
    {
        Boolean skipWarnRows = (request == null) ? null : request.getSkipWarnRows();
        return success(importBatchService.submitExecute(batchId, skipWarnRows));
    }

    /**
     * 下载导入失败明细（文件流）。
     *
     * <p>内容现算：解析阶段判为错误的行 + 执行阶段失败的行。
     */
    @PreAuthorize("@ss.hasPermi('book:import:list')")
    @GetMapping("/{batchId}/error-file")
    public void errorFile(@PathVariable("batchId") Long batchId, HttpServletResponse response)
    {
        importBatchService.writeErrorFile(batchId, response);
    }

    /**
     * 下载标准导入模板（文件流）。
     */
    @PreAuthorize("@ss.hasPermi('book:import:list')")
    @GetMapping("/template")
    public void template(HttpServletResponse response)
    {
        importBatchService.writeTemplate(response);
    }
}
