package com.tbgbook.bookstore.sale.controller;

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

import com.tbgbook.bookstore.sale.domain.PromotionBook;
import com.tbgbook.bookstore.sale.service.IPromotionBookService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 促销活动商品 Controller（A-34 活动商品配置）。
 *
 * <p>权限串全部复用 {@code sales:promotion:*}，不为子资源另设前缀（契约如此）——
 * 与 {@code /book/book/textbook}、{@code /inventory/stockin/item} 同一先例。
 *
 * <p>⚠️ <b>字面量 {@code /list} 与路径变量 {@code /{relId}} 同为 4 段</b>：
 * 两者**都在本控制器里**，Spring 的字面量模式优先于路径变量，故 {@code /list}
 * 不会被解析成 {@code relId="list"}（那还会因 {@code Long} 转换失败报 400）。
 * 放在同一个控制器里正是为了让这条优先级**只有单一出处**。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/promotion/book")
public class PromotionBookController extends BaseController
{
    private final IPromotionBookService promotionBookService;

    /**
     * 查询促销活动商品列表（含 JOIN 出来的书名与 ISBN）。
     */
    @PreAuthorize("@ss.hasPermi('sales:promotion:list')")
    @GetMapping("/list")
    public TableDataInfo list(PromotionBook item)
    {
        startPage();
        List<PromotionBook> list = promotionBookService.selectPromotionBookList(item);
        return getDataTable(list);
    }

    /**
     * 获取促销活动商品详情。
     */
    @PreAuthorize("@ss.hasPermi('sales:promotion:list')")
    @GetMapping("/{relId}")
    public AjaxResult getInfo(@PathVariable("relId") Long relId)
    {
        return success(promotionBookService.selectPromotionBookById(relId));
    }

    /**
     * 新增促销活动商品（**活动进行中时不可增删改**；图书已下架/停售时 422）。
     */
    @PreAuthorize("@ss.hasPermi('sales:promotion:add')")
    @Log(title = "促销活动商品", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody PromotionBook item)
    {
        return toAjax(promotionBookService.addPromotionBook(item));
    }

    /**
     * 修改促销活动商品（主键在请求体，不在路径 —— RuoYi 式非严格 REST）。
     */
    @PreAuthorize("@ss.hasPermi('sales:promotion:edit')")
    @Log(title = "促销活动商品", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody PromotionBook item)
    {
        return toAjax(promotionBookService.updatePromotionBook(item));
    }

    /**
     * 删除促销活动商品（支持批量，主键逗号分隔；**物理删除**，本表无 del_flag）。
     */
    @PreAuthorize("@ss.hasPermi('sales:promotion:remove')")
    @Log(title = "促销活动商品", businessType = BusinessType.DELETE)
    @DeleteMapping("/{relIds}")
    public AjaxResult remove(@PathVariable Long[] relIds)
    {
        return toAjax(promotionBookService.removePromotionBooks(relIds));
    }
}
