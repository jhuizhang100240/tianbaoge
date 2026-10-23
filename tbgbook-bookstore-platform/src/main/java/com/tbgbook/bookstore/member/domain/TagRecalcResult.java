package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 规则标签重算结果（`POST /member/tag/{tagId}/recalc` 的 {@code data}）。
 *
 * @author tbgbook
 */
@Data
public class TagRecalcResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 重算后覆盖会员数 */
    private Integer memberCount;

    /** 本次新增关联数 */
    private Integer added;

    /** 本次移除关联数 */
    private Integer removed;
}
