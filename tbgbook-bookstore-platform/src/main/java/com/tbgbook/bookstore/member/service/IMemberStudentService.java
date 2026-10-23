package com.tbgbook.bookstore.member.service;

import java.util.List;

import com.tbgbook.bookstore.member.domain.MemberStudent;
import com.tbgbook.bookstore.member.domain.StudentAuditRequest;
import com.tbgbook.bookstore.member.domain.StudentSubmitRequest;

/**
 * 会员教育信息与学号认证 Service 接口（阶段五步骤③a，FR-4.4.1）。
 *
 * <p>认证生命周期：未认证(0) / 已拒绝(3) →（submit）→ 审核中(1) →（audit）→ 已认证(2) / 已拒绝(3)。
 * 认证状态经 {@code biz_member.student_status} 冗余同步到会员主档。
 *
 * @author tbgbook
 */
public interface IMemberStudentService
{
    /**
     * 查询学号认证列表。
     *
     * @param student 查询条件
     * @return 记录集合（学号脱敏由序列化层完成）
     */
    List<MemberStudent> selectMemberStudentList(MemberStudent student);

    /**
     * 查询学号认证详情。
     *
     * @param memberId 会员ID
     * @return 记录；不存在时抛 404
     */
    MemberStudent selectMemberStudentById(Long memberId);

    /**
     * 新增教育信息（初始为未认证；一会员一条）。
     *
     * @param student 教育信息
     * @return 影响行数
     */
    int addMemberStudent(MemberStudent student);

    /**
     * 修改教育信息（**审核中 / 已认证不可改**；学号与其他审核中/已认证记录查重）。
     *
     * @param student 教育信息
     * @return 影响行数
     */
    int updateMemberStudent(MemberStudent student);

    /**
     * 删除教育信息（**审核中 / 已认证不可删**）。
     *
     * @param memberIds 会员主键数组
     * @return 影响行数
     */
    int removeMemberStudents(Long[] memberIds);

    /**
     * 提交学号认证：置 1 审核中；同一会员在审核中/已认证时 409，学号已被他人认证时 422。
     *
     * @param request 提交请求
     * @return 提交后的记录
     */
    MemberStudent submit(StudentSubmitRequest request);

    /**
     * 审核学号认证：通过（2）回填会员真实姓名并置 student_status=2；拒绝（3）必填原因并置 3。
     *
     * @param memberId 会员ID
     * @param request  审核请求
     * @return 审核后的记录
     */
    MemberStudent audit(Long memberId, StudentAuditRequest request);
}
