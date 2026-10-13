package com.tbgbook.bookstore.ai.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.ai.domain.RecommendConfig;
import com.tbgbook.bookstore.ai.domain.RecommendPin;
import com.tbgbook.bookstore.ai.service.IRecommendService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 推荐位 Controller（`/ai/recommend`）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/recommend")
public class AiRecommendController extends BaseController
{
    private final IRecommendService recommendService;

    @PreAuthorize("@ss.hasPermi('ai:recommend:list')")
    @GetMapping("/list")
    public TableDataInfo list(RecommendConfig recommendConfig)
    {
        startPage();
        return getDataTable(recommendService.selectRecommendConfigList(recommendConfig));
    }

    @PreAuthorize("@ss.hasPermi('ai:recommend:list')")
    @GetMapping("/{configId}")
    public AjaxResult getInfo(@PathVariable("configId") Long configId)
    {
        return success(recommendService.getById(configId));
    }

    @PreAuthorize("@ss.hasPermi('ai:recommend:add')")
    @Log(title = "AI 推荐位", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody RecommendConfig recommendConfig)
    {
        return toAjax(recommendService.addRecommendConfig(recommendConfig));
    }

    @PreAuthorize("@ss.hasPermi('ai:recommend:edit')")
    @Log(title = "AI 推荐位", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody RecommendConfig recommendConfig)
    {
        return toAjax(recommendService.updateRecommendConfig(recommendConfig));
    }

    @PreAuthorize("@ss.hasPermi('ai:recommend:remove')")
    @Log(title = "AI 推荐位", businessType = BusinessType.DELETE)
    @DeleteMapping("/{configIds}")
    public AjaxResult remove(@PathVariable("configIds") Long[] configIds)
    {
        return toAjax(recommendService.removeRecommendConfigs(configIds));
    }

    /**
     * 设置推荐位置顶（整体替换；含下架图书整体拒绝）。
     */
    @PreAuthorize("@ss.hasPermi('ai:recommend')")
    @Log(title = "AI 推荐位", businessType = BusinessType.UPDATE)
    @PutMapping("/{configId}/pins")
    public AjaxResult replacePins(@PathVariable("configId") Long configId,
            @RequestBody Map<String, Object> request)
    {
        return success(recommendService.replacePins(configId, parsePins(request.get("pins"))));
    }

    /**
     * 推荐位效果预览（区分算法/人工通道；AI 停用时按兜底策略并置 fallbackUsed）。
     */
    @PreAuthorize("@ss.hasPermi('ai:recommend')")
    @GetMapping("/{configId}/preview")
    public AjaxResult preview(@PathVariable("configId") Long configId)
    {
        return success(recommendService.preview(configId));
    }

    @SuppressWarnings("unchecked")
    private List<RecommendPin> parsePins(Object value)
    {
        List<RecommendPin> pins = new ArrayList<>();
        if (!(value instanceof Iterable))
        {
            return pins;
        }
        for (Object item : (Iterable<Object>) value)
        {
            if (!(item instanceof Map))
            {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) item;
            RecommendPin pin = new RecommendPin();
            pin.setBookId(row.get("bookId") == null ? null : Long.valueOf(String.valueOf(row.get("bookId"))));
            pin.setPinOrder(row.get("pinOrder") == null ? null
                    : Integer.valueOf(String.valueOf(row.get("pinOrder"))));
            pins.add(pin);
        }
        return pins;
    }
}
