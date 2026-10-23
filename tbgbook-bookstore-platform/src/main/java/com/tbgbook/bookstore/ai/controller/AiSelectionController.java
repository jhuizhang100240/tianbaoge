package com.tbgbook.bookstore.ai.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.ai.service.ISelectionService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * AI 选品 Controller（`/ai/selection`）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/selection")
public class AiSelectionController extends BaseController
{
    private final ISelectionService selectionService;

    /**
     * 智能选品与补货建议（AI 不可用 503）。
     */
    @PreAuthorize("@ss.hasPermi('ai:selection:list')")
    @GetMapping("/suggest")
    public AjaxResult suggest(@RequestParam(value = "suggestType", required = false) Integer suggestType,
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit)
    {
        return success(selectionService.suggest(suggestType, limit));
    }

    /**
     * 采纳建议并转采购入库单草稿（不依赖 AI）。
     */
    @PreAuthorize("@ss.hasPermi('ai:selection:adopt')")
    @Log(title = "AI 选品采纳", businessType = BusinessType.INSERT)
    @PostMapping("/adopt")
    @SuppressWarnings("unchecked")
    public AjaxResult adopt(@RequestBody Map<String, Object> request)
    {
        Long[] bookIds = toBookIds(request.get("bookIds"));
        Map<String, Object> suggestQty = request.get("suggestQty") instanceof Map
                ? (Map<String, Object>) request.get("suggestQty") : null;
        String remark = request.get("remark") == null ? null : String.valueOf(request.get("remark"));
        return success(selectionService.adopt(bookIds, suggestQty, remark));
    }

    @SuppressWarnings("unchecked")
    private Long[] toBookIds(Object value)
    {
        if (!(value instanceof Iterable))
        {
            return new Long[0];
        }
        List<Long> ids = new ArrayList<>();
        for (Object item : (Iterable<Object>) value)
        {
            ids.add(Long.valueOf(String.valueOf(item)));
        }
        return ids.toArray(new Long[0]);
    }
}
