package com.tbgbook.bookstore.borrow.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tbgbook.bookstore.borrow.domain.BorrowRecord;
import com.tbgbook.bookstore.borrow.domain.BorrowRenew;
import com.tbgbook.bookstore.borrow.domain.LendRequest;
import com.tbgbook.bookstore.borrow.domain.OverdueNotifyRequest;
import com.tbgbook.bookstore.borrow.domain.RenewRequest;
import com.tbgbook.bookstore.borrow.domain.ReturnRequest;
import com.tbgbook.bookstore.borrow.service.IBorrowRecordService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 借阅单 Controller（页面 A-53~A-56，FR-4.5.2 ~ FR-4.5.5 / FR-4.5.8 / FR-4.5.9）。
 *
 * <p>一单一册；借还走动作端点 {@code /borrow/lend} 与 {@code /borrow/return}，
 * 续借走 {@code /borrow/record/{borrowId}/renew}；资源 CRUD 仅供后台补录与维护。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/borrow")
public class BorrowRecordController extends BaseController
{
    private final IBorrowRecordService borrowRecordService;

    // ==================================================================
    // 借阅单 CRUD
    // ==================================================================

    /**
     * 查询借阅单列表（可按单号 / 会员 / 图书 / ISBN / 条码 / 逾期 / 状态与借出时间筛选）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:record:list')")
    @GetMapping("/record/list")
    public TableDataInfo list(BorrowRecord record)
    {
        startPage();
        List<BorrowRecord> list = borrowRecordService.selectBorrowRecordList(record);
        return getDataTable(list);
    }

    /**
     * 逾期清单与罚金试算（FR-4.5.5、A-56）。
     *
     * @param status 借阅状态（不传 = 在借 1 与已逾期 3）
     */
    @PreAuthorize("@ss.hasPermi('borrow:overdue:list')")
    @GetMapping("/record/overdue")
    public AjaxResult overdue(@RequestParam(value = "memberId", required = false) Long memberId,
            @RequestParam(value = "minOverdueDays", required = false) Integer minOverdueDays,
            @RequestParam(value = "status", required = false) Integer status)
    {
        return success(borrowRecordService.overdueList(memberId, minOverdueDays, status));
    }

    /**
     * 获取借阅单详情。
     */
    @PreAuthorize("@ss.hasPermi('borrow:record:list')")
    @GetMapping("/record/{borrowId}")
    public AjaxResult getInfo(@PathVariable("borrowId") Long borrowId)
    {
        return success(borrowRecordService.selectBorrowRecordById(borrowId));
    }

    /**
     * 后台补录借阅单（不联动库存；正常借出走 {@code POST /borrow/lend}）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:record:add')")
    @Log(title = "借阅单", businessType = BusinessType.INSERT)
    @PostMapping("/record")
    public AjaxResult add(@Valid @RequestBody BorrowRecord record)
    {
        return toAjax(borrowRecordService.addBorrowRecord(record));
    }

    /**
     * 修改借阅单（仅备注与取书库位可改）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:record:edit')")
    @Log(title = "借阅单", businessType = BusinessType.UPDATE)
    @PutMapping("/record")
    public AjaxResult edit(@RequestBody BorrowRecord record)
    {
        return toAjax(borrowRecordService.updateBorrowRecord(record));
    }

    /**
     * 删除借阅单（仅无任何流转痕迹的误录单可删）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:record:remove')")
    @Log(title = "借阅单", businessType = BusinessType.DELETE)
    @DeleteMapping("/record/{borrowIds}")
    public AjaxResult remove(@PathVariable Long[] borrowIds)
    {
        return toAjax(borrowRecordService.removeBorrowRecords(borrowIds));
    }

    // ==================================================================
    // 借出 / 归还 / 续借
    // ==================================================================

    /**
     * 借书办理（资格四道校验 + 库存联动）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:lend:list')")
    @Log(title = "借书办理", businessType = BusinessType.INSERT)
    @PostMapping("/lend")
    public AjaxResult lend(@RequestBody LendRequest request)
    {
        return success(borrowRecordService.lend(request));
    }

    /**
     * 还书办理（逾期判定 + 罚金计算 + 库存回补）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:return:list')")
    @Log(title = "还书办理", businessType = BusinessType.UPDATE)
    @PostMapping("/return")
    public AjaxResult returnBook(@RequestBody ReturnRequest request)
    {
        return success(borrowRecordService.returnBook(request));
    }

    /**
     * 续借办理（次数上限 + 无他人预约 + 未逾期）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:record:renew')")
    @Log(title = "借阅续借", businessType = BusinessType.UPDATE)
    @PostMapping("/record/{borrowId}/renew")
    public AjaxResult renew(@PathVariable("borrowId") Long borrowId,
            @RequestBody(required = false) RenewRequest request)
    {
        return success(borrowRecordService.renew(borrowId, request));
    }

    /**
     * 查询续借记录列表。
     */
    @PreAuthorize("@ss.hasPermi('borrow:record:list')")
    @GetMapping("/renew/list")
    public TableDataInfo renewList(BorrowRenew renew)
    {
        startPage();
        List<BorrowRenew> list = borrowRecordService.selectBorrowRenewList(renew);
        return getDataTable(list);
    }

    /**
     * 获取续借记录详情。
     */
    @PreAuthorize("@ss.hasPermi('borrow:record:list')")
    @GetMapping("/renew/{renewId}")
    public AjaxResult renewInfo(@PathVariable("renewId") Long renewId)
    {
        return success(borrowRecordService.selectBorrowRenewById(renewId));
    }

    // ==================================================================
    // 催还 / 统计
    // ==================================================================

    /**
     * 批量发送催还提醒（站内信；真实微信推送留阶段九）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:overdue:notify')")
    @Log(title = "逾期催还", businessType = BusinessType.OTHER)
    @PostMapping("/overdue/notify")
    public AjaxResult notifyOverdue(@RequestBody OverdueNotifyRequest request)
    {
        return success(borrowRecordService.notifyOverdue(request));
    }

    /**
     * 借阅统计（趋势 / 热门图书 / 周期分布 / 逾期率 / 等级占比）。
     */
    @PreAuthorize("@ss.hasPermi('borrow:record:list')")
    @GetMapping("/statistics/summary")
    public AjaxResult statistics(
            @RequestParam("startDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(value = "granularity", defaultValue = "day") String granularity)
    {
        return success(borrowRecordService.statistics(startDate, endDate, granularity));
    }
}
