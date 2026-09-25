package com.tbgbook.bookstore.sale.controller;

import java.util.List;

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

import com.tbgbook.bookstore.sale.domain.Promotion;
import com.tbgbook.bookstore.sale.domain.PromotionStatusRequest;
import com.tbgbook.bookstore.sale.service.IPromotionService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 促销活动 Controller（页面 A-32~A-34 促销管理，FR-4.3.7）。
 *
 * <p><b>状态流转只走 {@code POST /{promotionId}/status}</b>：增改都不接受 {@code status}
 * （契约的 `skip`）—— 那个动作端点带「活动商品不能为空」的校验，若增改能直接写状态就绕过了它。
 *
 * <p>⚠️ <b>路径段位</b>：活动商品子资源挂在 {@code /sales/promotion/book}，
 * 与 {@code GET /sales/promotion/{promotionId}} **段数不同**（4 vs 3），
 * 故不存在「字面量 vs 路径变量」的优先级问题（对照 {@code StockWarningController#scan}：
 * 那里段数相同、且动作端点是 POST 而详情是 GET，才需要说明行为）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/promotion")
public class PromotionController extends BaseController
{
    private final IPromotionService promotionService;

    /**
     * 查询促销活动列表（可按活动编号/名称/类型/范围/状态/是否特价与创建时间区间筛选）。
     */
    @PreAuthorize("@ss.hasPermi('sales:promotion:list')")
    @GetMapping("/list")
    public TableDataInfo list(Promotion promotion)
    {
        startPage();
        List<Promotion> list = promotionService.selectPromotionList(promotion);
        return getDataTable(list);
    }

    /**
     * 获取促销活动详情。
     */
    @PreAuthorize("@ss.hasPermi('sales:promotion:list')")
    @GetMapping("/{promotionId}")
    public AjaxResult getInfo(@PathVariable("promotionId") Long promotionId)
    {
        return success(promotionService.selectPromotionById(promotionId));
    }

    /**
     * 新增促销活动（**状态恒为 1草稿**，活动编号服务端生成）。
     */
    @PreAuthorize("@ss.hasPermi('sales:promotion:add')")
    @Log(title = "促销活动", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody Promotion promotion)
    {
        return toAjax(promotionService.addPromotion(promotion));
    }

    /**
     * 修改促销活动（**仅 1草稿 / 4已停用**；进行中的要先停用）。
     */
    @PreAuthorize("@ss.hasPermi('sales:promotion:edit')")
    @Log(title = "促销活动", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody Promotion promotion)
    {
        return toAjax(promotionService.updatePromotion(promotion));
    }

    /**
     * 删除促销活动（**主从级联**：活动商品随之删除；进行中的不可删）。
     */
    @PreAuthorize("@ss.hasPermi('sales:promotion:remove')")
    @Log(title = "促销活动", businessType = BusinessType.DELETE)
    @DeleteMapping("/{promotionIds}")
    public AjaxResult remove(@PathVariable Long[] promotionIds)
    {
        return toAjax(promotionService.removePromotions(promotionIds));
    }

    /**
     * 活动启停（状态流转）。
     *
     * <p>启用时校验「活动商品不能为空」（422），并对与其它进行中活动的时间区间重叠
     * 给出**非阻断告警**（{@code warnings}）—— 同一商品命中多个活动时按优先级取一、不相加，
     * 所以重叠不是错误，只是提醒。
     */
    @PreAuthorize("@ss.hasPermi('sales:promotion:status')")
    @Log(title = "促销活动", businessType = BusinessType.UPDATE)
    @PostMapping("/{promotionId}/status")
    public AjaxResult changeStatus(@PathVariable("promotionId") Long promotionId,
            @Valid @RequestBody PromotionStatusRequest request)
    {
        return success(promotionService.changeStatus(promotionId, request.getStatus()));
    }
}
