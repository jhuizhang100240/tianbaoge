package com.tbgbook.bookstore.book.service.impl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tbgbook.bookstore.book.domain.Book;
import com.tbgbook.bookstore.book.domain.IsbnInfo;
import com.tbgbook.bookstore.book.domain.IsbnLookupResult;
import com.tbgbook.bookstore.book.isbn.IsbnLookupProperties;
import com.tbgbook.bookstore.book.isbn.IsbnProvider;
import com.tbgbook.bookstore.book.mapper.BookMapper;
import com.tbgbook.bookstore.book.service.IIsbnLookupService;
import com.tbgbook.bookstore.book.support.IsbnUtils;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.common.utils.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * ISBN 识别填充 服务层实现。
 *
 * <p><b>执行顺序</b>：ISBN 归一化与格式校验 → **先查本地**（已有主记录就直接把 bookId 交回去，
 * 既省一次出网调用，也正是「避免重复建档」的目的）→ 再查外部书库 → 组装结果。
 *
 * <p><b>不加 {@code @Transactional}</b>：本条链路含**外部 HTTP 调用**，而根 CLAUDE.md §6.2
 * 明令「事务里不做远程调用」—— 外部 API 超时会把数据库连接一起占住。
 * 本方法全程只读，也不需要事务。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class IsbnLookupServiceImpl implements IIsbnLookupService
{
    private static final Logger log = LoggerFactory.getLogger(IsbnLookupServiceImpl.class);

    private final BookMapper bookMapper;

    private final IsbnLookupProperties properties;

    /** 全部可插拔适配器，按 {@code isbn-lookup.provider} 选用 */
    private final List<IsbnProvider> providers;

    @Override
    public IsbnLookupResult lookup(String rawIsbn)
    {
        String isbn = IsbnUtils.normalize(rawIsbn);
        if (!IsbnUtils.isFormatOk(isbn))
        {
            throw BizErrors.ruleViolated(
                    "ISBN 格式不正确（应为 10 位或 13 位数字，可含连字符）：" + rawIsbn);
        }

        IsbnLookupResult result = new IsbnLookupResult();
        result.setIsbn(isbn);

        // ① 本地已有主记录 —— 不查外部，直接把 bookId 交给前端去跳转（契约要求此时回 409）
        Book existing = findExisting(isbn);
        if (existing != null)
        {
            result.setExistingBookId(existing.getBookId());
            result.setExistingBookNo(existing.getBookNo());
            result.setTitle(existing.getTitle());
            result.setLookupStatus(IsbnLookupResult.LOOKUP_STATUS_HIT);
            return result;
        }

        // ② 查外部书库
        IsbnProvider provider = pickProvider();
        result.setSource(provider.source());
        IsbnInfo info = null;
        try
        {
            info = provider.lookup(isbn).orElse(null);
        }
        catch (RuntimeException e)
        {
            // 降级：不阻断建档，只标记「信息待补全」（FR-4.1.1 规则 1）。
            // 打 warn 不打 error —— 外部依赖抖动是预期内的可恢复问题，不该淹没真实故障
            log.warn("ISBN 书目查询降级 isbn={} provider={} 原因={}", isbn, provider.source(), e.toString());
        }

        if (info == null)
        {
            result.setLookupStatus(IsbnLookupResult.LOOKUP_STATUS_PENDING);
            return result;
        }
        // 属性名两边对齐，直接拷；再补上查询结论
        BeanUtils.copyProperties(info, result);
        result.setLookupStatus(IsbnLookupResult.LOOKUP_STATUS_HIT);
        return result;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /**
     * 查本地是否已有该 ISBN 的主记录。
     *
     * <p>走 MP 的 lambda 查询，{@code @TableLogic} 会自动排除已逻辑删除的行 ——
     * 与建档时的唯一性判断（{@code uk_isbn} + 显式存在性校验）口径一致：
     * 已删除的书不占用 ISBN。
     */
    private Book findExisting(String isbn)
    {
        return bookMapper.selectOne(Wrappers.<Book>lambdaQuery()
                .eq(Book::getIsbn, isbn)
                .last("limit 1"));
    }

    /** 按配置选适配器；缺失即基础设施配置问题（503），不是用户输入问题 */
    private IsbnProvider pickProvider()
    {
        String wanted = properties.getProvider();
        for (IsbnProvider provider : providers)
        {
            if (provider.source().equalsIgnoreCase(StringUtils.trim(wanted)))
            {
                return provider;
            }
        }
        throw BizErrors.serviceUnavailable("ISBN 书目查询未找到供应商「" + wanted + "」的实现");
    }
}
