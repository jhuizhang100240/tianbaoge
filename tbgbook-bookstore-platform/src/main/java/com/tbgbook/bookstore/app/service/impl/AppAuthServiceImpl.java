package com.tbgbook.bookstore.app.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tbgbook.bookstore.app.client.AppWechatClient;
import com.tbgbook.bookstore.app.client.WechatSession;
import com.tbgbook.bookstore.app.config.AppProperties;
import com.tbgbook.bookstore.app.domain.request.AppLoginRequest;
import com.tbgbook.bookstore.app.domain.request.StudentCertRequest;
import com.tbgbook.bookstore.app.security.AppContext;
import com.tbgbook.bookstore.app.security.AppTokenService;
import com.tbgbook.bookstore.app.service.IAppAuthService;
import com.tbgbook.bookstore.app.support.AppQrCode;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.domain.MemberStudent;
import com.tbgbook.bookstore.member.domain.StudentSubmitRequest;
import com.tbgbook.bookstore.member.service.IMemberService;
import com.tbgbook.bookstore.member.service.IMemberStudentService;
import com.tbgbook.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;

/**
 * 会员端认证 Service 实现。
 *
 * @author tbgbook
 */
@Service
@RequiredArgsConstructor
public class AppAuthServiceImpl implements IAppAuthService
{
    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");

    private final IMemberService memberService;

    private final IMemberStudentService memberStudentService;

    private final AppTokenService tokenService;

    private final AppWechatClient wechatClient;

    private final AppProperties appProperties;

    @Override
    public Map<String, Object> login(AppLoginRequest request)
    {
        String code = request.getCode().trim();
        boolean mock = appProperties.getWechat().isMockEnabled() && code.startsWith("mock-");
        String phone;
        if (mock)
        {
            phone = StringUtils.hasText(request.getPhoneCode())
                    ? request.getPhoneCode().trim()
                    : code.substring("mock-".length());
            if (!PHONE.matcher(phone).matches())
            {
                throw BizErrors.ruleViolated("手机号解密失败");
            }
        }
        else
        {
            WechatSession session = wechatClient.code2Session(code);
            phone = wechatClient.phone(session, request.getPhoneCode(),
                    request.getEncryptedData(), request.getIv());
        }

        Member member = memberService.getOne(new LambdaQueryWrapper<Member>()
                .eq(Member::getPhone, phone).last("limit 1"));
        boolean isNew = false;
        if (member == null)
        {
            Member created = new Member();
            created.setPhone(phone);
            created.setNickname("微信用户");
            created.setAvatar("");
            created.setGender(0);
            created.setRegisterSource(1);
            memberService.addMember(created);
            member = memberService.getById(created.getMemberId());
            isNew = true;
        }
        if (member.getStatus() != null && member.getStatus() == 4)
        {
            throw BizErrors.conflict("该手机号已被注销状态账号占用（需联系门店）");
        }
        if (member.getStatus() != null && member.getStatus() != 1)
        {
            throw BizErrors.conflict("账号状态异常，请联系门店");
        }

        Map<String, Object> data = memberData(member);
        data.put("token", tokenService.createToken(member.getMemberId()));
        data.put("expiresIn", tokenService.getExpireSeconds());
        data.put("isNewMember", isNew);
        data.put("memberQrUrl", AppQrCode.dataUri("MEMBER:" + member.getMemberNo()));
        return data;
    }

    @Override
    public Map<String, Object> submitStudentCert(StudentCertRequest request)
    {
        StudentSubmitRequest submit = new StudentSubmitRequest();
        submit.setMemberId(AppContext.requireMemberId());
        submit.setSchool(request.getSchool());
        submit.setStudentNo(request.getStudentNo());
        submit.setRealName(request.getRealName());
        submit.setMajor(request.getMajor());
        submit.setGrade(request.getGrade());
        submit.setCertImageUrl(request.getCertImageUrl());
        submit.setCertType(2);
        MemberStudent student = memberStudentService.submit(submit);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("certStatus", student.getCertStatus());
        data.put("applyTime", student.getApplyTime());
        return data;
    }

    @Override
    public Map<String, Object> studentCertStatus()
    {
        Long memberId = AppContext.requireMemberId();
        MemberStudent student;
        try
        {
            student = memberStudentService.selectMemberStudentById(memberId);
        }
        catch (ServiceException e)
        {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("certStatus", 0);
            empty.put("certType", null);
            empty.put("school", "");
            empty.put("major", "");
            empty.put("grade", "");
            empty.put("rejectReason", "");
            empty.put("auditTime", null);
            return empty;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("certStatus", student.getCertStatus());
        data.put("certType", student.getCertType());
        data.put("school", student.getSchool());
        data.put("major", student.getMajor());
        data.put("grade", student.getGrade());
        data.put("rejectReason", student.getRejectReason());
        data.put("auditTime", student.getAuditTime());
        return data;
    }

    @Override
    public Map<String, Object> refresh()
    {
        Long memberId = AppContext.requireMemberId();
        Member member = memberService.selectMemberById(memberId);
        if (member.getStatus() == null || member.getStatus() != 1)
        {
            throw BizErrors.unauthorized("账号状态异常，请重新登录");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", tokenService.createToken(memberId));
        data.put("expiresIn", tokenService.getExpireSeconds());
        return data;
    }

    private Map<String, Object> memberData(Member member)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("memberId", member.getMemberId());
        data.put("memberNo", member.getMemberNo());
        data.put("nickname", member.getNickname());
        data.put("avatar", member.getAvatar());
        data.put("levelId", member.getLevelId());
        data.put("levelName", member.getLevelName());
        data.put("studentStatus", member.getStudentStatus());
        data.put("phone", maskPhone(member.getPhone()));
        return data;
    }

    private String maskPhone(String phone)
    {
        if (phone == null || phone.length() < 7)
        {
            return "";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
