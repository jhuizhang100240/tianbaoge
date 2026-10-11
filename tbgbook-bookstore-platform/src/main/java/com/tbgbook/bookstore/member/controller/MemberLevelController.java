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

import com.tbgbook.bookstore.member.domain.MemberLevel;
import com.tbgbook.bookstore.member.service.IMemberLevelService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 会员等级 Controller（页面 A-46，FR-4.4.3）。
 *
 * <p>默认等级全表唯一、默认等级与被引用等级禁删，规则都在
 * {@code MemberLevelServiceImpl}，此处只做权限与出入参。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/member/level")
public class MemberLevelController extends BaseController
{
    private final IMemberLevelService memberLevelService;

    /**
     * 查询会员等级列表（可按编码 / 名称 / 状态与创建时间区间筛选）。
     */
    @PreAuthorize("@ss.hasPermi('member:level:list')")
    @GetMapping("/list")
    public TableDataInfo list(MemberLevel level)
    {
        startPage();
        List<MemberLevel> list = memberLevelService.selectMemberLevelList(level);
        return getDataTable(list);
    }

    /**
     * 获取会员等级详情。
     */
    @PreAuthorize("@ss.hasPermi('member:level:list')")
    @GetMapping("/{levelId}")
    public AjaxResult getInfo(@PathVariable("levelId") Long levelId)
    {
        return success(memberLevelService.selectMemberLevelById(levelId));
    }

    /**
     * 新增会员等级（设为默认时同事务清除其他默认标记）。
     */
    @PreAuthorize("@ss.hasPermi('member:level:add')")
    @Log(title = "会员等级", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody MemberLevel level)
    {
        return toAjax(memberLevelService.addMemberLevel(level));
    }

    /**
     * 修改会员等级（不允许取消唯一的默认等级）。
     */
    @PreAuthorize("@ss.hasPermi('member:level:edit')")
    @Log(title = "会员等级", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody MemberLevel level)
    {
        return toAjax(memberLevelService.updateMemberLevel(level));
    }

    /**
     * 删除会员等级（默认等级与被会员引用的等级禁止删除）。
     */
    @PreAuthorize("@ss.hasPermi('member:level:remove')")
    @Log(title = "会员等级", businessType = BusinessType.DELETE)
    @DeleteMapping("/{levelIds}")
    public AjaxResult remove(@PathVariable Long[] levelIds)
    {
        return toAjax(memberLevelService.removeMemberLevels(levelIds));
    }
}
