package com.tbgbook.bookstore.ai.service;

import java.util.Map;

/**
 * AI 选品与补货建议 Service。
 *
 * @author tbgbook
 */
public interface ISelectionService
{
    /**
     * 选品建议（结合库存/销量/借阅/季节；AI 不可用 503）。
     */
    Map<String, Object> suggest(Integer suggestType, Integer limit);

    /**
     * 采纳建议并转采购入库单草稿（不依赖 AI；不走库存变动）。
     */
    Map<String, Object> adopt(Long[] bookIds, Map<String, Object> suggestQty, String remark);
}
