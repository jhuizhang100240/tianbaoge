package com.tbgbook.bookstore.member.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.member.domain.MemberTagRel;

/**
 * 会员标签关系 Mapper 接口。
 *
 * <p>{@code selectMemberMetricValues} 是规则标签重算的取数口：
 * 按指标白名单选一条固定表达式，返回每个会员的指标值，**比较运算在 Java 侧完成** ——
 * 避免把运算符拼进 SQL（NFR-S6 禁止 {@code ${}}）。
 *
 * @author tbgbook
 */
public interface MemberTagRelMapper extends BaseMapper<MemberTagRel>
{
    /**
     * 查询标签关系列表。
     *
     * @param rel 查询条件
     * @return 关系集合
     */
    List<MemberTagRel> selectMemberTagRelList(MemberTagRel rel);

    /** 某标签当前关联的全部会员ID（重算时求差集用） */
    List<Long> selectRelMemberIdsByTag(@Param("tagId") Long tagId);

    /** 删除某标签下指定会员的关联（重算移除；memberIds 非空才调用） */
    int deleteByTagAndMembers(@Param("tagId") Long tagId, @Param("memberIds") List<Long> memberIds);

    /** 批量插入关联（重算新增；memberIds 非空才调用） */
    int insertRelBatch(@Param("tagId") Long tagId, @Param("memberIds") List<Long> memberIds,
            @Param("source") int source, @Param("createBy") String createBy,
            @Param("now") LocalDateTime now);

    /**
     * 规则标签取数：返回 {@code [{memberId, metricValue}]}。
     *
     * @param metric      指标 key（来自 {@code TagRule} 白名单）
     * @param windowStart 近 30 天窗口起点（**应用侧时间**，A32 约束）
     * @param now         当前时间（应用侧；register_days 计算用）
     */
    List<Map<String, Object>> selectMemberMetricValues(@Param("metric") String metric,
            @Param("windowStart") LocalDateTime windowStart, @Param("now") LocalDateTime now);
}
