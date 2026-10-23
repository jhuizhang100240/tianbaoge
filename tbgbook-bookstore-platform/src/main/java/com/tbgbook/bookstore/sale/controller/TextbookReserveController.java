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

import com.tbgbook.bookstore.sale.domain.PickupRequest;
import com.tbgbook.bookstore.sale.domain.ReserveCancelRequest;
import com.tbgbook.bookstore.sale.domain.TextbookReserve;
import com.tbgbook.bookstore.sale.service.ITextbookReserveService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 教材预订 Controller（A-30 教材核销，FR-4.3.10）。
 *
 * <p><b>预订即锁库存、不收定金</b>：新增成功后图书的 qty_locked 增加、qty_available 减少
 * （写 biz_type=12 流水）。到店核销时按实际取书数量释放锁定并另行结算付款
 * （§4.3 规则 7，2026-09-21 已确认）—— 故本资源的金额只是「预订清单的价值」，
 * 真正的收款发生在收银台。
 *
 * <p><b>支持分批取书</b>：pickup 每次只释放本次数量，lockedQty 累加，
 * 累计达到 qty 时置 status=4 已取完；挂清单的预订同步回写清单明细的已取数。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/reserve")
public class TextbookReserveController extends BaseController
{
    private final ITextbookReserveService textbookReserveService;

    @PreAuthorize("@ss.hasPermi('sales:textbook:list')")
    @GetMapping("/list")
    public TableDataInfo list(TextbookReserve reserve)
    {
        startPage();
        List<TextbookReserve> list = textbookReserveService.selectTextbookReserveList(reserve);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('sales:textbook:list')")
    @GetMapping("/{reserveId}")
    public AjaxResult getInfo(@PathVariable("reserveId") Long reserveId)
    {
        return success(textbookReserveService.selectTextbookReserveById(reserveId));
    }

    /** 新增教材预订（**锁定库存**；单号、金额、取货码、状态一律服务端生成）。 */
    @PreAuthorize("@ss.hasPermi('sales:textbook:add')")
    @Log(title = "教材预订", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody TextbookReserve reserve)
    {
        return toAjax(textbookReserveService.addTextbookReserve(reserve));
    }

    /** 修改教材预订（**仅 2待到店取书**；只接受数量，变更同步调整锁定库存）。 */
    @PreAuthorize("@ss.hasPermi('sales:textbook:edit')")
    @Log(title = "教材预订", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody TextbookReserve reserve)
    {
        return toAjax(textbookReserveService.updateTextbookReserve(reserve));
    }

    /** 删除教材预订（**仅 2待到店取书**；删除会释放未取的锁定库存）。 */
    @PreAuthorize("@ss.hasPermi('sales:textbook:remove')")
    @Log(title = "教材预订", businessType = BusinessType.DELETE)
    @DeleteMapping("/{reserveIds}")
    public AjaxResult remove(@PathVariable Long[] reserveIds)
    {
        return toAjax(textbookReserveService.removeTextbookReserves(reserveIds));
    }

    /**
     * 教材取书核销（**支持分批**）。
     *
     * <p>取货码传了就必须一致；不传则不校验（到店报单号也能取）。
     */
    @PreAuthorize("@ss.hasPermi('sales:textbook:pickup')")
    @Log(title = "教材取书核销", businessType = BusinessType.UPDATE)
    @PostMapping("/{reserveId}/pickup")
    public AjaxResult pickup(@PathVariable("reserveId") Long reserveId,
            @Valid @RequestBody PickupRequest request)
    {
        return success(textbookReserveService.pickup(reserveId, request));
    }

    /** 取消教材预订（**释放全部未取的锁定库存**；已部分取书不可取消）。 */
    @PreAuthorize("@ss.hasPermi('sales:textbook:cancel')")
    @Log(title = "取消教材预订", businessType = BusinessType.UPDATE)
    @PostMapping("/{reserveId}/cancel")
    public AjaxResult cancel(@PathVariable("reserveId") Long reserveId,
            @Valid @RequestBody ReserveCancelRequest request)
    {
        return toAjax(textbookReserveService.cancel(reserveId, request.getReason()));
    }
}
