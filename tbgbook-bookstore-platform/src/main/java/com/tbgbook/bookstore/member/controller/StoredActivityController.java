package com.tbgbook.bookstore.member.controller;

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

import com.tbgbook.bookstore.member.domain.StoredActivity;
import com.tbgbook.bookstore.member.service.IMemberBalanceService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 储值充值活动 Controller（页面 A-48，FR-4.4.5）。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/member/stored-activity")
public class StoredActivityController extends BaseController
{
    private final IMemberBalanceService memberBalanceService;

    /**
     * 查询充值活动列表（可按名称 / 状态 / 是否推荐 / 档位与创建时间区间筛选）。
     */
    @PreAuthorize("@ss.hasPermi('member:balance:list')")
    @GetMapping("/list")
    public TableDataInfo list(StoredActivity activity)
    {
        startPage();
        List<StoredActivity> list = memberBalanceService.selectStoredActivityList(activity);
        return getDataTable(list);
    }

    /**
     * 获取充值活动详情。
     */
    @PreAuthorize("@ss.hasPermi('member:balance:list')")
    @GetMapping("/{activityId}")
    public AjaxResult getInfo(@PathVariable("activityId") Long activityId)
    {
        return success(memberBalanceService.selectStoredActivityById(activityId));
    }

    /**
     * 新增充值活动。
     */
    @PreAuthorize("@ss.hasPermi('member:balance:add')")
    @Log(title = "储值充值活动", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody StoredActivity activity)
    {
        return toAjax(memberBalanceService.addStoredActivity(activity));
    }

    /**
     * 修改充值活动。
     */
    @PreAuthorize("@ss.hasPermi('member:balance:edit')")
    @Log(title = "储值充值活动", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody StoredActivity activity)
    {
        return toAjax(memberBalanceService.updateStoredActivity(activity));
    }

    /**
     * 删除充值活动（物理删除；流水不挂活动 ID，不构成引用）。
     */
    @PreAuthorize("@ss.hasPermi('member:balance:remove')")
    @Log(title = "储值充值活动", businessType = BusinessType.DELETE)
    @DeleteMapping("/{activityIds}")
    public AjaxResult remove(@PathVariable Long[] activityIds)
    {
        return toAjax(memberBalanceService.removeStoredActivities(activityIds));
    }
}
