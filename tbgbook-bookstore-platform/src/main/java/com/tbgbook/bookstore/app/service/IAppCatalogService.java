package com.tbgbook.bookstore.app.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 会员端首页 / 查书 / 二手书 Service。
 */
public interface IAppCatalogService
{
    Map<String, Object> home();

    Map<String, Object> recommend(String positionCode, int pageNum, int pageSize);

    Map<String, Object> search(String keyword, Long categoryId, Integer bookType, BigDecimal minPrice,
            BigDecimal maxPrice, boolean onlyAvailable, boolean includeUsed, String sortBy,
            int pageNum, int pageSize);

    Map<String, Object> scan(String code);

    Map<String, Object> bookDetail(Long bookId);

    Map<String, Object> bookSummary(Long bookId);

    List<Map<String, Object>> hotKeywords();

    List<Map<String, Object>> categoryTree();

    Map<String, Object> usedBookList(String keyword, Long categoryId, Long conditionGradeId,
            BigDecimal minPrice, BigDecimal maxPrice, String sortBy, int pageNum, int pageSize);

    Map<String, Object> usedBookDetail(Long usedBookId);
}
