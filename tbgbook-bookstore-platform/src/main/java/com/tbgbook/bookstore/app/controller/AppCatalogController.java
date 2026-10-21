package com.tbgbook.bookstore.app.controller;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.app.service.IAppCatalogService;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;

import lombok.RequiredArgsConstructor;

/**
 * 会员端首页 / 查书 / 二手书 Controller（B-03~B-06、B-15、B-20）。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/app")
public class AppCatalogController extends BaseController
{
    private final IAppCatalogService catalogService;

    @GetMapping("/home/index")
    public AjaxResult home()
    {
        return success(catalogService.home());
    }

    @GetMapping("/home/recommend")
    public AjaxResult recommend(
            @RequestParam(value = "positionCode", required = false, defaultValue = "home_guess") String positionCode,
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize)
    {
        return success(catalogService.recommend(positionCode, pageNum, pageSize));
    }

    @GetMapping("/book/search")
    public AjaxResult search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "bookType", required = false) Integer bookType,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "onlyAvailable", required = false, defaultValue = "false") boolean onlyAvailable,
            @RequestParam(value = "includeUsed", required = false, defaultValue = "false") boolean includeUsed,
            @RequestParam(value = "sortBy", required = false, defaultValue = "default") String sortBy,
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize)
    {
        return success(catalogService.search(keyword, categoryId, bookType, minPrice, maxPrice,
                onlyAvailable, includeUsed, sortBy, pageNum, pageSize));
    }

    @GetMapping("/book/scan")
    public AjaxResult scan(@RequestParam("code") String code)
    {
        return success(catalogService.scan(code));
    }

    @GetMapping("/book/hot-keywords")
    public AjaxResult hotKeywords()
    {
        return success(catalogService.hotKeywords());
    }

    @GetMapping("/book/categories")
    public AjaxResult categories()
    {
        return success(catalogService.categoryTree());
    }

    @GetMapping("/book/{bookId}")
    public AjaxResult bookDetail(@PathVariable("bookId") Long bookId)
    {
        return success(catalogService.bookDetail(bookId));
    }

    @GetMapping("/book/{bookId}/summary")
    public AjaxResult bookSummary(@PathVariable("bookId") Long bookId)
    {
        return success(catalogService.bookSummary(bookId));
    }

    @GetMapping("/usedbook/list")
    public AjaxResult usedBookList(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "conditionGradeId", required = false) Long conditionGradeId,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "sortBy", required = false, defaultValue = "new") String sortBy,
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") int pageNum,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize)
    {
        return success(catalogService.usedBookList(keyword, categoryId, conditionGradeId,
                minPrice, maxPrice, sortBy, pageNum, pageSize));
    }

    @GetMapping("/usedbook/{usedBookId}")
    public AjaxResult usedBookDetail(@PathVariable("usedBookId") Long usedBookId)
    {
        return success(catalogService.usedBookDetail(usedBookId));
    }
}
