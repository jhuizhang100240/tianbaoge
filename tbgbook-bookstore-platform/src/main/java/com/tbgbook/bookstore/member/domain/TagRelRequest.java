package com.tbgbook.bookstore.member.domain;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * 手工打标 / 取消打标请求体（`POST /member/tag/rel/mark` 与 `/remove`）。
 *
 * <p>必填与取值校验放 Service（契约错误码为 422 / 404），故此处不加 Jakarta 注解。
 *
 * @author tbgbook
 */
@Data
public class TagRelRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 会员ID */
    private Long memberId;

    /** 标签ID数组 */
    private List<Long> tagIds;
}
