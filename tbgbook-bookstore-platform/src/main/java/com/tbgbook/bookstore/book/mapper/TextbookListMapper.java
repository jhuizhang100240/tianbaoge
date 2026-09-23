package com.tbgbook.bookstore.book.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tbgbook.bookstore.book.domain.TextbookList;

/**
 * 教材清单 Mapper 接口。
 *
 * <p>方法名没按 {@code select<实体>List} 的字面套（那会得到 {@code selectTextbookListList}）——
 * 本实体名本身以 List 结尾，故取 {@code selectTextbookList}。
 *
 * @author tbgbook
 */
public interface TextbookListMapper extends BaseMapper<TextbookList>
{
    /**
     * 查询教材清单列表。
     *
     * @param textbookList 查询条件
     * @return 教材清单集合
     */
    List<TextbookList> selectTextbookList(TextbookList textbookList);

    /**
     * 状态流转的**带条件**更新（草稿→发布→结束）。
     *
     * <p>本表没有 {@code version} 列，所以用「条件 UPDATE + 校验影响行数」防并发：
     * {@code where list_id = ? and status = ?}（期望的起始状态）。两人同时点「发布」时，
     * 第二次拿到 0 行 —— 调用方据此报冲突，而不是静默重复发布。
     *
     * <p>{@code update_by} / {@code update_time} 由调用方显式给出 ——
     * XML 里的 update **不走** {@code MyMetaObjectHandler}（只对 MP 的
     * insert/updateById 生效），不显式写就会漏掉审计字段。
     *
     * @param patch          要写入的字段（{@code status} 必填）
     * @param expectedStatus 期望的当前状态；不匹配则不更新
     * @return 影响行数，0 表示状态已被他人改动
     */
    int transition(@Param("patch") TextbookList patch, @Param("expectedStatus") Integer expectedStatus);
}
