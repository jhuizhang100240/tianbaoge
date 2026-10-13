package com.tbgbook.bookstore.ai.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

/**
 * AI 域只读统计查询（推荐候选；选品复用）。
 *
 * @author tbgbook
 */
public interface AiStatsMapper
{
    /**
     * 热销候选：近 90 天借阅 + 累计销量热度（在售、未删除）。
     */
    List<Map<String, Object>> selectHotBooks(@Param("limit") int limit);

    /**
     * 新书候选：按建档时间倒序（在售、未删除）。
     */
    List<Map<String, Object>> selectNewBooks(@Param("limit") int limit);
}
