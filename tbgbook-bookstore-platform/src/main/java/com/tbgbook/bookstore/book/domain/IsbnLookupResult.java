package com.tbgbook.bookstore.book.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ISBN 识别填充的响应（{@code POST /book/book/isbn-lookup} 的 {@code data}）。
 *
 * <p>在 {@link IsbnInfo} 之上加三样：查询结论（{@code lookupStatus}）与「本 ISBN 是否已建档」
 * （{@code existingBookId} / {@code existingBookNo}）。
 *
 * <p><b>为什么这个字段叫 {@code lookupStatus} 而不是 {@code infoStatus}</b>（C17，2026-09-22 用户裁决）：
 * 初版契约把它写成 {@code infoStatus}，而 {@code Book.infoStatus} 是「**建档信息完整度**」
 * （口径：作者 + 出版社 + 分类 + 定价 四项齐备，见 {@code BookServiceImpl#computeInfoStatus}）——
 * 同一个名字两套语义。本响应表达的是「**外部书库是否命中**」，与完整度无关，
 * 因此改名为 {@code lookupStatus}。
 *
 * <p>顺带说明为什么不能沿用建档口径：外部接口**给不出 {@code publisherId} / {@code categoryId}**
 * （只给出版社名字），所以按建档口径算本响应**恒为 2、没有信息量**。
 *
 * @author tbgbook
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IsbnLookupResult extends IsbnInfo
{
    private static final long serialVersionUID = 1L;

    /** 外部书库命中（或本地已建档） */
    public static final int LOOKUP_STATUS_HIT = 1;

    /** 外部书库未命中或调用失败 —— 前端据此提示「信息待补全」，仍允许手工录入建档 */
    public static final int LOOKUP_STATUS_PENDING = 2;

    /** 外部书库是否命中（1 命中 / 2 未命中或失败），语义见类注释 */
    private Integer lookupStatus;

    /**
     * 该 ISBN 在本地**已有主记录**时的图书ID。
     *
     * <p>非 {@code null} 表示本次查询是为了「避免重复建档」—— 接口文档把这种情况定为
     * **409**，响应里带上已存在图书的 {@code bookId}，前端据此直接跳转到该图书而不是新建。
     */
    private Long existingBookId;

    /** 同上，已存在图书的图书编号（前端展示用） */
    private String existingBookNo;
}
