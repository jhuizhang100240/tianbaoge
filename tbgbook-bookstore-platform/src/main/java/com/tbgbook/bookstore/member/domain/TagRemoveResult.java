package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;

import lombok.Data;

/**
 * 取消手工标签结果（`POST /member/tag/rel/remove` 的 {@code data}）。
 *
 * @author tbgbook
 */
@Data
public class TagRemoveResult implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 实际解除数 */
    private Integer removed;
}
