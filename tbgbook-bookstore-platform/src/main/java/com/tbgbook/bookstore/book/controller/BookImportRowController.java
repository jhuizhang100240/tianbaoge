package com.tbgbook.bookstore.book.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.book.domain.BookImportRow;
import com.tbgbook.bookstore.book.service.IBookImportRowService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 图书批量导入明细行 Controller（页面 A-10 步骤③的预览表格）。
 *
 * <p><b>本资源的写端点不在正常流程上</b>：明细行由 {@code POST /book/import/parse}
 * 生成，不经过这里。CRUD 五个端点照建是因为接口文档的「图书批量导入明细行」资源
 * 声明了 5 个端点（生成器按 child 资源产出），前端若按文档调用不应拿到 404。
 * 列表与详情才是实际使用的两个。
 *
 * <p>⚠️ 本表**没有 {@code del_flag}**（不属于基础数据表），因此删除是**物理删除** ——
 * 不能照抄基础数据表「删除即改标记」的写法。
 *
 * <p>相对代码生成器产物的**人工裁剪点**：详情权限串由 {@code :query} 改为 {@code :list}；
 * 补 {@code @Validated} / {@code @Valid}（NFR-M2）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/book/import/row")
public class BookImportRowController extends BaseController
{
    private final IBookImportRowService importRowService;

    /**
     * 查询明细行列表。
     *
     * <p>不返回 {@code rawData}（整行快照）—— 预览表格用不上，逐行带上是纯放大响应体。
     */
    @PreAuthorize("@ss.hasPermi('book:import:list')")
    @GetMapping("/list")
    public TableDataInfo list(BookImportRow row)
    {
        startPage();
        List<BookImportRow> list = importRowService.selectImportRowList(row);
        return getDataTable(list);
    }

    /**
     * 获取明细行详细信息。
     */
    @PreAuthorize("@ss.hasPermi('book:import:list')")
    @GetMapping("/{rowId}")
    public AjaxResult getInfo(@PathVariable("rowId") Long rowId)
    {
        return success(importRowService.getById(rowId));
    }

    /**
     * 新增明细行（正常流程不经此处）。
     */
    @PreAuthorize("@ss.hasPermi('book:import:add')")
    @Log(title = "图书导入明细行", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody BookImportRow row)
    {
        return toAjax(importRowService.save(row));
    }

    /**
     * 修改明细行（正常流程不经此处）。
     */
    @PreAuthorize("@ss.hasPermi('book:import:edit')")
    @Log(title = "图书导入明细行", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody BookImportRow row)
    {
        return toAjax(importRowService.updateById(row));
    }

    /**
     * 删除明细行（**物理删除**，本表无 {@code del_flag}）。
     */
    @PreAuthorize("@ss.hasPermi('book:import:remove')")
    @Log(title = "图书导入明细行", businessType = BusinessType.DELETE)
    @DeleteMapping("/{rowIds}")
    public AjaxResult remove(@PathVariable Long[] rowIds)
    {
        return toAjax(importRowService.removeByIds(List.of(rowIds)));
    }
}
