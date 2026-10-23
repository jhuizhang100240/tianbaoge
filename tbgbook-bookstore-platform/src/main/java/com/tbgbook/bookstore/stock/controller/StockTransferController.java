package com.tbgbook.bookstore.stock.controller;

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

import com.tbgbook.bookstore.stock.domain.StockTransfer;
import com.tbgbook.bookstore.stock.domain.StockTransferConfirmRequest;
import com.tbgbook.bookstore.stock.domain.StockTransferVoidRequest;
import com.tbgbook.bookstore.stock.service.IStockTransferService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 库存调拨单 Controller（页面 A-26 库存调拨）。
 *
 * <p><b>两个动作端点</b>：{@code POST /{transferId}/confirm} 是书真正换库位的动作
 * （FR-4.2.10），{@code POST /{transferId}/void} 反向挪回。
 *
 * <p>⚠️ <b>字面量路径与 {@code /{transferId}} 同段位</b>：明细子资源挂在
 * {@code /inventory/transfer/item}，与 {@code GET /inventory/transfer/{transferId}} 竞争同一段位。
 * Spring 的字面量模式优先于路径变量，故 {@code /item/list} 不会被解析成
 * {@code transferId="item"}（那还会因 {@code Long} 转换失败报 400）。
 * 与 {@code StockInController} 是同一个坑 —— 处理方式一样：
 * **让优先级只有单一出处**，故两个控制器都放在 {@code stock/controller} 下并列声明。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/inventory/transfer")
public class StockTransferController extends BaseController
{
    private final IStockTransferService stockTransferService;

    /**
     * 查询库存调拨单列表（含调出/调入库位的编码、名称、区域）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:transfer:list')")
    @GetMapping("/list")
    public TableDataInfo list(StockTransfer transfer)
    {
        startPage();
        List<StockTransfer> list = stockTransferService.selectStockTransferList(transfer);
        return getDataTable(list);
    }

    /**
     * 获取库存调拨单详情（**含明细**）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:transfer:list')")
    @GetMapping("/{transferId}")
    public AjaxResult getInfo(@PathVariable("transferId") Long transferId)
    {
        return success(stockTransferService.selectStockTransferById(transferId));
    }

    /**
     * 新增库存调拨单（草稿，**不动库存**）。
     *
     * <p>请求体是「单据头 + {@code items} 明细数组」，两者同事务落库。
     */
    @PreAuthorize("@ss.hasPermi('inventory:transfer:add')")
    @Log(title = "库存调拨单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody StockTransfer transfer)
    {
        return toAjax(stockTransferService.addStockTransfer(transfer));
    }

    /**
     * 修改库存调拨单（**仅草稿**；明细整体替换）。
     */
    @PreAuthorize("@ss.hasPermi('inventory:transfer:edit')")
    @Log(title = "库存调拨单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody StockTransfer transfer)
    {
        return toAjax(stockTransferService.updateStockTransfer(transfer));
    }

    /**
     * 删除库存调拨单（**仅草稿**，主从级联）。
     *
     * <p>已调拨的单据要用 {@code /void} 作废（会把书挪回原库位），不能直接删。
     */
    @PreAuthorize("@ss.hasPermi('inventory:transfer:remove')")
    @Log(title = "库存调拨单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{transferIds}")
    public AjaxResult remove(@PathVariable Long[] transferIds)
    {
        return toAjax(stockTransferService.removeStockTransfers(transferIds));
    }

    /**
     * 确认调拨 —— <b>书真正换库位的动作</b>。
     *
     * <p>请求体可省略（{@code transferTime} 是唯一字段且选填）。
     * 调出库位扣减、调入库位累加，同一事务内写两条流水。重复提交返回 <b>409</b>，
     * 不会静默把书再挪一遍。
     */
    @PreAuthorize("@ss.hasPermi('inventory:transfer:confirm')")
    @Log(title = "库存调拨单", businessType = BusinessType.UPDATE)
    @PostMapping("/{transferId}/confirm")
    public AjaxResult confirm(@PathVariable("transferId") Long transferId,
            @RequestBody(required = false) StockTransferConfirmRequest request)
    {
        return success(stockTransferService.confirm(transferId,
                request == null ? null : request.getTransferTime()));
    }

    /**
     * 作废调拨单。已调拨的单据会**反向挪回库存**并写两条红字流水。
     *
     * <p>{@code reason} 必填（契约如此）。本表没有作废原因列，该值并入 {@code remark}，
     * 见 {@code StockTransferServiceImpl#appendReason}。
     *
     * <p>⚠️ 权限是 {@code inventory:transfer:void}（**不是** {@code :remove}）：
     * 删除权只授给店长，而 {@code 07_role_seed.sql} 的横切规则②要求非店长
     * 「走作废 / 红冲业务动作」，故作废单独一个权限串，授给店长 + 库管员。
     */
    @PreAuthorize("@ss.hasPermi('inventory:transfer:void')")
    @Log(title = "库存调拨单", businessType = BusinessType.UPDATE)
    @PostMapping("/{transferId}/void")
    public AjaxResult voidTransfer(@PathVariable("transferId") Long transferId,
            @Valid @RequestBody StockTransferVoidRequest request)
    {
        return toAjax(stockTransferService.voidTransfer(transferId, request.getReason()));
    }
}
