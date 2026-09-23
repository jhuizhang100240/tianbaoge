package com.tbgbook.bookstore.stock.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.stock.domain.StockWarning;
import com.tbgbook.bookstore.stock.domain.WarningHandleRequest;
import com.tbgbook.bookstore.stock.domain.WarningScanRequest;
import com.tbgbook.bookstore.stock.service.IStockWarningService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 库存预警 Controller（页面 A-23 库存预警中心）。
 *
 * <p><b>只有两个写端点</b>：{@code /scan}（立即扫一次）与 {@code /handle}（改处置状态）。
 * 预警本身**没有 CRUD** —— 它是系统对账面的判断，手工造一条或删掉一条都会让判断失去依据。
 *
 * <p>⚠️ <b>{@code /scan} 与 {@code /{warningId}} 同段位，但**不冲突**</b>：
 * Spring 的「字面量模式优先于路径变量」是**按 HTTP 方法生效的** ——
 * 本类只有 {@code POST /scan} 没有 {@code GET /scan}，故 {@code POST /scan}
 * 不会落到任何 {@code {warningId}} 映射上，无歧义。
 * 而 <b>{@code GET /inventory/warning/scan} 会落进 {@code GET /{warningId}}</b>，
 * 于是 {@code "scan"} 转 {@code Long} 失败、返回
 * <b>400</b>（文案「参数[warningId]要求类型为：java.lang.Long，但输入值为：scan」）——
 * <b>这是正常行为而不是路由缺陷</b>：那个路径本来就没有 GET 语义。
 * <b>实测确认</b>（2026-09-23）：{{@code POST /scan}} → 200，{{@code GET /scan}} → 400。
 *
 * <p>对照 {@code StockController}：它的 {@code /scan} 是 <b>GET</b>，
 * 所以那里存在字面量 GET 映射、才真的「抢」在 {@code GET /{stockId}} 之前 ——
 * 同一个路径写法在两个资源上行为不同，差别只在方法。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory/warning")
public class StockWarningController extends BaseController
{
    private final IStockWarningService stockWarningService;

    /**
     * 查询库存预警列表。
     */
    @PreAuthorize("@ss.hasPermi('inventory:warning:list')")
    @GetMapping("/list")
    public TableDataInfo list(StockWarning warning)
    {
        startPage();
        List<StockWarning> list = stockWarningService.selectStockWarningList(warning);
        return getDataTable(list);
    }

    /**
     * 获取库存预警详情。
     */
    @PreAuthorize("@ss.hasPermi('inventory:warning:list')")
    @GetMapping("/{warningId}")
    public AjaxResult getInfo(@PathVariable("warningId") Long warningId)
    {
        return success(stockWarningService.selectStockWarningById(warningId));
    }

    /**
     * 手动触发一次预警扫描。
     *
     * <p>与定时任务「库存预警扫描」（每日 06:00）**同一套逻辑**，只是触发方式不同。
     * 请求体可省略（{@code warningType} 是唯一字段且选填，不传即四类全扫）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:warning:scan')")
    @Log(title = "库存预警扫描", businessType = BusinessType.UPDATE)
    @PostMapping("/scan")
    public AjaxResult scan(@RequestBody(required = false) WarningScanRequest request)
    {
        return success(stockWarningService.scan(request == null ? null : request.getWarningType()));
    }

    /**
     * 处置库存预警（置为已处理 / 已忽略）。
     *
     * <p>处置补货类（1补货 / 4零库存）预警时可顺带转一张采购入库单草稿
     * （{@code createStockIn=true}），转单与处置在同一事务内完成。
     */
    @PreAuthorize("@ss.hasPermi('inventory:warning:handle')")
    @Log(title = "库存预警处置", businessType = BusinessType.UPDATE)
    @PutMapping("/handle")
    public AjaxResult handle(@Valid @RequestBody WarningHandleRequest request)
    {
        return success(stockWarningService.handle(request));
    }
}
