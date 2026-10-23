package com.tbgbook.bookstore.book.controller;

import java.util.List;

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

import com.tbgbook.bookstore.book.domain.Publisher;
import com.tbgbook.bookstore.book.service.IPublisherService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 出版社 Controller。
 *
 * <p>本类是**项目样板**：后续 80 余张业务表的 Controller 照此复制
 * （{@code plan/backend-plan.md} 阶段一任务 1.4）。
 *
 * <p>相对代码生成器产物的**人工裁剪点**（逐条对照 {@code docs/api-documentation.md} §出版社）：
 * <ol>
 *   <li><b>删掉 export 端点</b> —— 接口文档的出版社只有 5 个端点，没有导出。
 *       生成器会给每张表都加 export，必须按文档删</li>
 *   <li><b>详情权限串由 {@code book:publisher:query} 改为 {@code book:publisher:list}</b>
 *       —— 文档里详情接口用的就是 {@code :list}（同一权限管列表与详情）</li>
 *   <li><b>删除走 Service 的 {@code removePublishers} 而不是 {@code removeByIds}</b>
 *       —— 前者带删除保护（被图书引用时返回 422），后者会直接删掉</li>
 *   <li>补 {@code @Validated} / {@code @Valid}（NFR-M2 参数校验前置）</li>
 * </ol>
 *
 * <p>权限串与路径一一对应（RuoYi 风格非严格 REST，见根 CLAUDE.md §6.1）：
 * 修改时主键在**请求体**的 {@code publisherId}，不在路径。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/book/publisher")
public class PublisherController extends BaseController
{
    private final IPublisherService publisherService;

    /**
     * 查询出版社列表。
     *
     * <p>分页一律 {@code startPage() → 查 → getDataTable(list)}
     * —— **不要用 MP 的 {@code selectPage}/{@code IPage}**，未注册分页插件时它们会静默返回全表
     * 且 {@code total} 为 0（后端 CLAUDE.md §五）。
     */
    @PreAuthorize("@ss.hasPermi('book:publisher:list')")
    @GetMapping("/list")
    public TableDataInfo list(Publisher publisher)
    {
        startPage();
        List<Publisher> list = publisherService.selectPublisherList(publisher);
        return getDataTable(list);
    }

    /**
     * 获取出版社详细信息。
     */
    @PreAuthorize("@ss.hasPermi('book:publisher:list')")
    @GetMapping("/{publisherId}")
    public AjaxResult getInfo(@PathVariable("publisherId") Long publisherId)
    {
        return success(publisherService.getById(publisherId));
    }

    /**
     * 新增出版社。
     */
    @PreAuthorize("@ss.hasPermi('book:publisher:add')")
    @Log(title = "出版社", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody Publisher publisher)
    {
        return toAjax(publisherService.save(publisher));
    }

    /**
     * 修改出版社。
     */
    @PreAuthorize("@ss.hasPermi('book:publisher:edit')")
    @Log(title = "出版社", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody Publisher publisher)
    {
        return toAjax(publisherService.updateById(publisher));
    }

    /**
     * 删除出版社。
     *
     * <p>被图书引用时返回 422 并附带引用数量，不静默删除、不级联删图书。
     */
    @PreAuthorize("@ss.hasPermi('book:publisher:remove')")
    @Log(title = "出版社", businessType = BusinessType.DELETE)
    @DeleteMapping("/{publisherIds}")
    public AjaxResult remove(@PathVariable Long[] publisherIds)
    {
        return toAjax(publisherService.removePublishers(publisherIds));
    }
}
