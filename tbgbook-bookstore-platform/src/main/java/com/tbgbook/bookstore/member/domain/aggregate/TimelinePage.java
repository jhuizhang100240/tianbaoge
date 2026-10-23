package com.tbgbook.bookstore.member.domain.aggregate;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 会员时间轴分页结果（`GET /member/member/{memberId}/timeline` 的 `data`）。
 *
 * <p>形态与 RuoYi 的 {@code TableDataInfo} 对齐（{@code rows} / {@code total}），
 * 但时间轴是**多表 UNION 的自定义分页**，不走 PageHelper。
 *
 * @author tbgbook
 */
@Data
@AllArgsConstructor
public class TimelinePage implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 当页记录（按时间倒序） */
    private List<MemberTimelineEntry> rows;

    /** 符合条件的总条数 */
    private long total;
}
