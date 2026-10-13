package com.tbgbook.bookstore.ai.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.ai.domain.AiCallLog;

/**
 * AI 调用明细 Mapper（写入走 insert，聚合走 XML）。
 *
 * @author tbgbook
 */
public interface AiCallLogMapper extends BaseMapper<AiCallLog>
{
    List<Map<String, Object>> selectCostSummary(@Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end, @Param("format") String format, @Param("scene") Integer scene);

    Map<String, Object> selectCacheHitStats(@Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end, @Param("scene") Integer scene);
}
