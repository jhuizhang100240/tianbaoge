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

import com.tbgbook.bookstore.member.domain.MemberStudent;
import com.tbgbook.bookstore.member.domain.StudentAuditRequest;
import com.tbgbook.bookstore.member.domain.StudentSubmitRequest;
import com.tbgbook.bookstore.member.service.IMemberStudentService;
import com.tbgbook.common.annotation.Log;
import com.tbgbook.common.core.controller.BaseController;
import com.tbgbook.common.core.domain.AjaxResult;
import com.tbgbook.common.core.page.TableDataInfo;
import com.tbgbook.common.enums.BusinessType;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 会员教育信息与学号认证 Controller（页面 A-44/A-45，FR-4.4.1）。
 *
 * <p>学号脱敏（NFR-S8，前 4 位 + ****）由实体上的 {@code @Sensitive} 在序列化层完成；
 * 管理员明文豁免沿用 A34 口径。
 *
 * @author tbgbook
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/member/member/student")
public class MemberStudentController extends BaseController
{
    private final IMemberStudentService memberStudentService;

    /**
     * 查询学号认证列表（可按会员 / 认证状态 / 方式 / 学校 / 专业 / 年级筛选）。
     */
    @PreAuthorize("@ss.hasPermi('member:member:list')")
    @GetMapping("/list")
    public TableDataInfo list(MemberStudent student)
    {
        startPage();
        List<MemberStudent> list = memberStudentService.selectMemberStudentList(student);
        return getDataTable(list);
    }

    /**
     * 获取学号认证详情。
     */
    @PreAuthorize("@ss.hasPermi('member:member:list')")
    @GetMapping("/{memberId}")
    public AjaxResult getInfo(@PathVariable("memberId") Long memberId)
    {
        return success(memberStudentService.selectMemberStudentById(memberId));
    }

    /**
     * 新增教育信息（初始未认证；一会员一条）。
     */
    @PreAuthorize("@ss.hasPermi('member:member:add')")
    @Log(title = "学号认证", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody MemberStudent student)
    {
        return toAjax(memberStudentService.addMemberStudent(student));
    }

    /**
     * 修改教育信息（审核中 / 已认证不可改）。
     */
    @PreAuthorize("@ss.hasPermi('member:member:edit')")
    @Log(title = "学号认证", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Valid @RequestBody MemberStudent student)
    {
        return toAjax(memberStudentService.updateMemberStudent(student));
    }

    /**
     * 删除教育信息（审核中 / 已认证不可删）。
     */
    @PreAuthorize("@ss.hasPermi('member:member:remove')")
    @Log(title = "学号认证", businessType = BusinessType.DELETE)
    @DeleteMapping("/{memberIds}")
    public AjaxResult remove(@PathVariable Long[] memberIds)
    {
        return toAjax(memberStudentService.removeMemberStudents(memberIds));
    }

    /**
     * 提交学号认证（置 1 审核中；审核中/已认证 409，学号重复 422）。
     */
    @PreAuthorize("@ss.hasPermi('member:member:submit')")
    @Log(title = "学号认证", businessType = BusinessType.UPDATE)
    @PostMapping("/submit")
    public AjaxResult submit(@RequestBody StudentSubmitRequest request)
    {
        return success(memberStudentService.submit(request));
    }

    /**
     * 审核学号认证（通过回填真实姓名并置 student_status=2；拒绝必填原因）。
     */
    @PreAuthorize("@ss.hasPermi('member:member:audit')")
    @Log(title = "学号认证", businessType = BusinessType.UPDATE)
    @PostMapping("/{memberId}/audit")
    public AjaxResult audit(@PathVariable("memberId") Long memberId,
            @RequestBody StudentAuditRequest request)
    {
        return success(memberStudentService.audit(memberId, request));
    }
}
