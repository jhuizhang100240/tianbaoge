package com.tbgbook.bookstore.member.service;

import java.math.BigDecimal;
import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.tbgbook.bookstore.member.domain.Member;

/**
 * 会员 Service 接口。
 *
 * @author tbgbook
 */
public interface IMemberService extends IService<Member>
{
    /**
     * 查询会员列表。
     *
     * @param member 查询条件
     * @return 会员集合（手机号脱敏由序列化层完成）
     */
    List<Member> selectMemberList(Member member);

    /**
     * 查询会员详情。
     *
     * @param memberId 会员ID
     * @return 会员；不存在时抛 404
     */
    Member selectMemberById(Long memberId);

    /**
     * 新增会员（服务端生成卡号 `M`+手机号、分配默认等级、初始化储值账户）。
     *
     * @param member 会员
     * @return 影响行数
     */
    int addMember(Member member);

    /**
     * 修改会员（汇总类字段与状态不接受写入；换手机号时同步重算卡号）。
     *
     * @param member 会员
     * @return 影响行数
     */
    int updateMember(Member member);

    /**
     * 批量删除会员（**仅限无任何业务痕迹的误录会员**；有痕迹抛 422，引导走注销）。
     *
     * @param memberIds 会员主键数组
     * @return 影响行数
     */
    int removeMembers(Long[] memberIds);

    /**
     * 消费汇总：次数 +1、金额累加、最近消费时间刷新（结算 / 取货核销调用）。
     *
     * @param memberId 会员ID
     * @param amount   实付金额
     * @return 影响行数
     */
    int addConsume(Long memberId, BigDecimal amount);

    /**
     * 注销会员：校验无未结清业务后做匿名化（个人信息覆写、标签关系解除、学号与证件清空），
     * 保留 memberId / memberNo 与业务流水（§4.4 规则 4）。
     *
     * @param memberId 会员ID
     * @param reason   注销原因（必填）
     * @return 影响行数
     */
    int cancelMember(Long memberId, String reason);
}
