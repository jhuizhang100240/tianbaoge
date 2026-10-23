package com.tbgbook.bookstore.borrow.controller;

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

import com.tbgbook.bookstore.borrow.domain.BorrowReserve;
import com.tbgbook.bookstore.borrow.service.IReserveService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import lombok.RequiredArgsConstructor;

/**
 * 借阅预约 Controller（页面 A-58，FR-4.5.6）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/borrow/reserve")
public class ReserveController extends BaseController
{
    private final IReserveService reserveService;

    /**
     * 查询预约列表（可按单号 / 会员 / 图书 / ISBN / 状态与创建时间筛选）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:reserve:list')")
    @GetMapping("/list")
    public TableDataInfo list(BorrowReserve reserve)
    {
        startPage();
        List<BorrowReserve> list = reserveService.selectReserveList(reserve);
        return getDataTable(list);
    }

    /**
     * 获取预约详情。
     */
    @PreAuthorize("@ss.hasPermi('borrow:reserve:list')")
    @GetMapping("/{reserveId}")
    public AjaxResult getInfo(@PathVariable("reserveId") Long reserveId)
    {
        return success(reserveService.selectReserveById(reserveId));
    }

    /**
     * 新增预约（同一会员同一图书不允许重复排队）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:reserve:add')")
    @Log(title = "借阅预约", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BorrowReserve reserve)
    {
        return toAjax(reserveService.addReserve(reserve));
    }

    /**
     * 修改预约（仅备注可改）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:reserve:edit')")
    @Log(title = "借阅预约", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BorrowReserve reserve)
    {
        return toAjax(reserveService.updateReserve(reserve));
    }

    /**
     * 删除预约（仅已取消 / 已过期可删）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:reserve:remove')")
    @Log(title = "借阅预约", businessType = BusinessType.DELETE)
    @DeleteMapping("/{reserveIds}")
    public AjaxResult remove(@PathVariable Long[] reserveIds)
    {
        return toAjax(reserveService.removeReserves(reserveIds));
    }

    /**
     * 到书通知（排队中 → 已到书待取，设置取书截止时间）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:reserve:notify')")
    @Log(title = "借阅预约", businessType = BusinessType.UPDATE)
    @PostMapping("/{reserveId}/notify")
    public AjaxResult notifyReserve(@PathVariable("reserveId") Long reserveId)
    {
        return success(reserveService.notifyReserve(reserveId));
    }

    /**
     * 取消预约（队列顺序重排）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:reserve:cancel')")
    @Log(title = "借阅预约", businessType = BusinessType.UPDATE)
    @PostMapping("/{reserveId}/cancel")
    public AjaxResult cancelReserve(@PathVariable("reserveId") Long reserveId,
            @RequestBody(required = false) java.util.Map<String, String> body)
    {
        String reason = body == null ? null : body.get("reason");
        return toAjax(reserveService.cancelReserve(reserveId, reason));
    }
}
