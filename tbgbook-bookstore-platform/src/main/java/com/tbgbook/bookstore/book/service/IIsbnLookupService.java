package com.tbgbook.bookstore.book.service;

import com.tbgbook.bookstore.book.domain.IsbnLookupResult;

/**
 * ISBN 识别填充 服务层（FR-4.1.1 规则 1）。
 *
 * @author tbgbook
 */
public interface IIsbnLookupService
{
    /**
     * 按 ISBN 查外部书库，回填书目信息。
     *
     * <p><b>不抛「未命中」</b>：调用失败或未命中都正常返回，只是把
     * {@code infoStatus} 置为 2 —— FR-4.1.1 规则 1 要求「调用失败不阻断建档」。
     * 只有 **ISBN 格式非法**（422）与 **基础设施不可用**（503，如白名单未放行）才抛异常。
     *
     * <p>⚠️ <b>调用方需注意</b>：当返回值的 {@code existingBookId} 非空时，表示该 ISBN
     * 在本地**已有主记录**，接口文档要求这种情况回 **409** 并把已存在的 {@code bookId}
     * 带给前端。协议适配（选 200 还是 409 的信封）由 Controller 做，本层只负责查出来。
     *
     * @param rawIsbn 原始 ISBN（可含连字符与空格）
     * @return 书目信息与查询结论
     */
    IsbnLookupResult lookup(String rawIsbn);
}
