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

import com.tbgbook.bookstore.book.domain.TextbookList;
import com.tbgbook.bookstore.book.service.ITextbookListService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 教材清单 Controller（页面 A-12 清单管理 / A-13 清单详情头部）。
 *
 * <p><b>契约里本资源有 7 个端点</b>：5 个 CRUD（接口文档 §教材清单）
 * + 2 个动作端点（同文档 §业务动作：发布 / 结束）。动作端点在契约与种子数据里
 * 本来就有（权限串 {@code book:textbook:publish} / {@code :finish} 已由
 * {@code docs/db/06_perm_and_config_fix.sql} 种下并授权给店长与库管员），
 * 不是本轮新开的接口。
 *
 * <p>相对代码生成器产物的**人工裁剪点**：① 删掉 export 端点（文档里没有）；
 * ② 详情权限串 {@code :query} → {@code :list}；③ 删除走带删除保护的 Service 方法；
 * ④ 补 {@code @Validated} / {@code @Valid}（NFR-M2）；⑤ 补两个动作端点。
 *
 * <p>状态流转的入口**有两个**（{@code PUT} 的 {@code status} 字段与下面两个动作端点），
 * 二者在 Service 里走同一套校验 —— 详见 {@code TextbookListServiceImpl#updateTextbookList}。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/book/textbook")
public class TextbookListController extends BaseController
{
    private final ITextbookListService textbookListService;

    /**
     * 查询教材清单列表。
     */
    @PreAuthorize("@ss.hasPermi('book:textbook:list')")
    @GetMapping("/list")
    public TableDataInfo list(TextbookList textbookList)
    {
        startPage();
        List<TextbookList> list = textbookListService.selectTextbookList(textbookList);
        return getDataTable(list);
    }

    /**
     * 获取教材清单详情。
     */
    @PreAuthorize("@ss.hasPermi('book:textbook:list')")
    @GetMapping("/{listId}")
    public AjaxResult getInfo(@PathVariable("listId") Long listId)
    {
        return success(textbookListService.selectTextbookListById(listId));
    }

    /**
     * 新增教材清单。
     *
     * <p>{@code listNo} 由服务端生成，状态固定为草稿(1) —— 传其它状态会 422。
     */
    @PreAuthorize("@ss.hasPermi('book:textbook:add')")
    @Log(title = "教材清单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody TextbookList textbookList)
    {
        return toAjax(textbookListService.addTextbookList(textbookList));
    }

    /**
     * 修改教材清单（主键在请求体的 {@code listId}，不在路径）。
     *
     * <p>请求体里的 {@code status} 会走与发布/结束相同的流转校验。
     */
    @PreAuthorize("@ss.hasPermi('book:textbook:edit')")
    @Log(title = "教材清单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody TextbookList textbookList)
    {
        return toAjax(textbookListService.updateTextbookList(textbookList));
    }

    /**
     * 删除教材清单。
     *
     * <p>只有草稿可删；明细随主表级联删除。
     */
    @PreAuthorize("@ss.hasPermi('book:textbook:remove')")
    @Log(title = "教材清单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{listIds}")
    public AjaxResult remove(@PathVariable Long[] listIds)
    {
        return toAjax(textbookListService.removeTextbookLists(listIds));
    }

    /**
     * 发布教材清单 —— 发布后会员端可见并接受预订（流程五）。
     *
     * <p>前置：清单非空、每行明细都已关联有效图书；已是发布状态返回 409。
     */
    @PreAuthorize("@ss.hasPermi('book:textbook:publish')")
    @Log(title = "教材清单", businessType = BusinessType.UPDATE)
    @PostMapping("/{listId}/publish")
    public AjaxResult publish(@PathVariable("listId") Long listId)
    {
        return toAjax(textbookListService.publish(listId));
    }

    /**
     * 结束教材清单 —— 停止接受新预订，剩余库存转常规销售（流程五）。
     *
     * <p>前置：当前必须是已发布，否则 422。
     */
    @PreAuthorize("@ss.hasPermi('book:textbook:finish')")
    @Log(title = "教材清单", businessType = BusinessType.UPDATE)
    @PostMapping("/{listId}/finish")
    public AjaxResult finish(@PathVariable("listId") Long listId)
    {
        return toAjax(textbookListService.finish(listId));
    }
}
