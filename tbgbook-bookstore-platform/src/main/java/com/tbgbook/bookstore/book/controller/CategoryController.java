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

import com.tbgbook.bookstore.book.domain.Category;
import com.tbgbook.bookstore.book.service.ICategoryService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 图书分类 Controller。
 *
 * <p>相对代码生成器产物的**人工裁剪点**（逐条对照 `docs/api-documentation.md` §图书分类）：
 * <ol>
 *   <li><b>删掉 export 端点</b> —— 文档里图书分类只有 5 个端点，没有导出</li>
 *   <li><b>详情权限串由 {@code :query} 改为 {@code :list}</b> —— 文档里详情用的就是 {@code :list}</li>
 *   <li><b>删除走 Service 的 {@code removeCategories}</b>（带删除保护），不用 {@code removeByIds}</li>
 *   <li>补 {@code @Validated} / {@code @Valid}（NFR-M2）</li>
 * </ol>
 *
 * <p><b>列表返回扁平集合</b>，由前端用 `handleTree` 组装成树（RuoYi 的部门/菜单页同此约定）——
 * 后端不返回嵌套结构，避免分页与树形互相打架。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/book/category")
public class CategoryController extends BaseController
{
    private final ICategoryService categoryService;

    /**
     * 查询图书分类列表。
     */
    @PreAuthorize("@ss.hasPermi('book:category:list')")
    @GetMapping("/list")
    public TableDataInfo list(Category category)
    {
        startPage();
        List<Category> list = categoryService.selectCategoryList(category);
        return getDataTable(list);
    }

    /**
     * 获取图书分类详细信息。
     */
    @PreAuthorize("@ss.hasPermi('book:category:list')")
    @GetMapping("/{categoryId}")
    public AjaxResult getInfo(@PathVariable("categoryId") Long categoryId)
    {
        return success(categoryService.getById(categoryId));
    }

    /**
     * 新增图书分类。
     *
     * <p>{@code ancestors} 与 {@code categoryLevel} 由服务端推导，客户端传了也会被覆盖。
     */
    @PreAuthorize("@ss.hasPermi('book:category:add')")
    @Log(title = "图书分类", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody Category category)
    {
        return toAjax(categoryService.addCategory(category));
    }

    /**
     * 修改图书分类。
     */
    @PreAuthorize("@ss.hasPermi('book:category:edit')")
    @Log(title = "图书分类", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody Category category)
    {
        return toAjax(categoryService.updateCategory(category));
    }

    /**
     * 删除图书分类。
     *
     * <p>有子分类或已被图书引用时返回 422，不静默删除、不级联删图书。
     */
    @PreAuthorize("@ss.hasPermi('book:category:remove')")
    @Log(title = "图书分类", businessType = BusinessType.DELETE)
    @DeleteMapping("/{categoryIds}")
    public AjaxResult remove(@PathVariable Long[] categoryIds)
    {
        return toAjax(categoryService.removeCategories(categoryIds));
    }
}
