package com.tbgbook.bookstore.app.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

/**
 * 会员端目录查询（只读）。
 */
public interface AppCatalogMapper
{
    List<Map<String, Object>> searchBooks(@Param("keyword") String keyword,
            @Param("categoryId") Long categoryId, @Param("bookType") Integer bookType,
            @Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice,
            @Param("onlyAvailable") boolean onlyAvailable, @Param("sortBy") String sortBy,
            @Param("offset") int offset, @Param("limit") int limit);

    long countBooks(@Param("keyword") String keyword, @Param("categoryId") Long categoryId,
            @Param("bookType") Integer bookType, @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice, @Param("onlyAvailable") boolean onlyAvailable);

    List<Map<String, Object>> searchUsedBooks(@Param("keyword") String keyword,
            @Param("categoryId") Long categoryId, @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice, @Param("sortBy") String sortBy,
            @Param("offset") int offset, @Param("limit") int limit);

    long countSearchUsedBooks(@Param("keyword") String keyword, @Param("categoryId") Long categoryId,
            @Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice);

    Map<String, Object> bookById(@Param("bookId") Long bookId);

    Map<String, Object> bookByIsbn(@Param("isbn") String isbn);

    Map<String, Object> bookStockSummary(@Param("bookId") Long bookId);

    List<Map<String, Object>> stockByLocation(@Param("bookId") Long bookId);

    List<Map<String, Object>> publishedSummaries(@Param("bookId") Long bookId);

    List<Map<String, Object>> usedBooksByBook(@Param("bookId") Long bookId);

    List<Map<String, Object>> relatedBooks(@Param("categoryId") Long categoryId,
            @Param("excludeBookId") Long excludeBookId, @Param("limit") int limit);

    List<Map<String, Object>> categories();

    List<Map<String, Object>> hotBooks(@Param("limit") int limit);

    List<Map<String, Object>> activePromotions();

    List<Map<String, Object>> personalizedBooks(@Param("memberId") Long memberId,
            @Param("limit") int limit);

    Map<String, Object> usedBookById(@Param("usedBookId") Long usedBookId);

    Map<String, Object> usedBookByBarCode(@Param("barCode") String barCode);

    List<Map<String, Object>> usedBookList(@Param("keyword") String keyword,
            @Param("categoryId") Long categoryId, @Param("conditionGradeId") Long conditionGradeId,
            @Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice,
            @Param("sortBy") String sortBy, @Param("offset") int offset, @Param("limit") int limit);

    long countUsedBookList(@Param("keyword") String keyword, @Param("categoryId") Long categoryId,
            @Param("conditionGradeId") Long conditionGradeId, @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice);
}
