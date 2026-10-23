package com.tbgbook.bookstore.ai.controller;

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

import com.tbgbook.bookstore.ai.domain.RecommendPin;
import com.tbgbook.bookstore.ai.service.IRecommendService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 推荐位人工置顶 Controller（`/ai/recommend/pin`）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/recommend/pin")
public class AiRecommendPinController extends BaseController
{
    private final IRecommendService recommendService;

    @PreAuthorize("@ss.hasPermi('ai:recommend:list')")
    @GetMapping("/list")
    public TableDataInfo list(RecommendPin recommendPin)
    {
        startPage();
        return getDataTable(recommendService.selectRecommendPinList(recommendPin));
    }

    @PreAuthorize("@ss.hasPermi('ai:recommend:list')")
    @GetMapping("/{pinId}")
    public AjaxResult getInfo(@PathVariable("pinId") Long pinId)
    {
        RecommendPin query = new RecommendPin();
        query.setPinId(pinId);
        return success(recommendService.selectRecommendPinList(query).stream().findFirst().orElse(null));
    }

    @PreAuthorize("@ss.hasPermi('ai:recommend:add')")
    @Log(title = "AI 推荐位置顶", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody RecommendPin recommendPin)
    {
        return toAjax(recommendService.addRecommendPin(recommendPin));
    }

    @PreAuthorize("@ss.hasPermi('ai:recommend:edit')")
    @Log(title = "AI 推荐位置顶", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody RecommendPin recommendPin)
    {
        return toAjax(recommendService.updateRecommendPin(recommendPin));
    }

    @PreAuthorize("@ss.hasPermi('ai:recommend:remove')")
    @Log(title = "AI 推荐位置顶", businessType = BusinessType.DELETE)
    @DeleteMapping("/{pinIds}")
    public AjaxResult remove(@PathVariable("pinIds") Long[] pinIds)
    {
        return toAjax(recommendService.removeRecommendPins(pinIds));
    }
}
