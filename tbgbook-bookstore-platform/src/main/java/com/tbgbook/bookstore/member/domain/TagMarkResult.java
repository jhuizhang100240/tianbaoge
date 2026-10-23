package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 手工打标结果（`POST /member/tag/rel/mark` 的 {@code data}）。
 *
 * @author tbgbook
 */
@Data
public class TagMarkResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 新增数 */
    private Integer added;

    /** 已存在跳过数 */
    private Integer skipped;
}
