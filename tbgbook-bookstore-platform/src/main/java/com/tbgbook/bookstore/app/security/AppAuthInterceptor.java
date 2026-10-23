package com.tbgbook.bookstore.app.security;

import java.nio.charset.StandardCharsets;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbgbook.bookstore.member.domain.Member;
import com.tbgbook.bookstore.member.service.IMemberService;
import com.tbgbook.common.core.domain.AjaxResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * `/app/**` 会员认证拦截器：公开接口可选 Token，受保护接口必须有合法会员 Token。
 *
 * @author tbgbook
 */
@Component
@RequiredArgsConstructor
public class AppAuthInterceptor implements HandlerInterceptor
{
    private static final String MEMBER_WRITE_LOCK = AppAuthInterceptor.class.getName() + ".writeLock";

    private final AppTokenService tokenService;

    private final ObjectMapper objectMapper;

    private final IMemberService memberService;

    private final RedissonClient redissonClient;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception
    {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()))
        {
            return true;
        }
        String uri = request.getRequestURI();
        String token = bearer(request);
        Long memberId = null;
        if (StringUtils.hasText(token))
        {
            try
            {
                memberId = tokenService.parseMemberId(token);
            }
            catch (Exception ignored)
            {
                memberId = null;
            }
        }
        if (memberId != null)
        {
            Member member = memberService.selectMemberById(memberId);
            if (member == null || !Integer.valueOf(1).equals(member.getStatus())
                    || Integer.valueOf(1).equals(member.getAnonymized()))
            {
                memberId = null;
            }
        }
        if (memberId == null && !isPublic(uri))
        {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, 401, "登录已失效，请重新登录");
            return false;
        }
        if (memberId != null)
        {
            if (isMemberWrite(request) && !"/app/ai/chat".equals(uri))
            {
                RLock lock = redissonClient.getLock("tbgbook:member:write:" + memberId);
                try
                {
                    if (!lock.tryLock())
                    {
                        writeError(response, HttpServletResponse.SC_CONFLICT, 409,
                                "该会员已有写操作正在处理，请稍后重试");
                        return false;
                    }
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    writeError(response, HttpServletResponse.SC_CONFLICT, 409,
                            "会员写操作等待被中断，请重试");
                    return false;
                }
                request.setAttribute(MEMBER_WRITE_LOCK, lock);
            }
            AppContext.setMemberId(memberId);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex)
    {
        Object value = request.getAttribute(MEMBER_WRITE_LOCK);
        if (value instanceof RLock lock && lock.isHeldByCurrentThread())
        {
            lock.unlock();
        }
        AppContext.clear();
    }

    private boolean isMemberWrite(HttpServletRequest request)
    {
        String method = request.getMethod();
        return !("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method));
    }

    private void writeError(HttpServletResponse response, int httpStatus, int bodyCode, String message)
            throws Exception
    {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                AjaxResult.error(bodyCode, message)));
    }

    private boolean isPublic(String uri)
    {
        return "/app/auth/login".equals(uri)
                || uri.startsWith("/app/home/")
                || uri.startsWith("/app/book/")
                || uri.startsWith("/app/usedbook/")
                || "/app/reserve/textbook-list".equals(uri)
                || "/app/member/levels".equals(uri);
    }

    private String bearer(HttpServletRequest request)
    {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header))
        {
            return null;
        }
        return header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();
    }
}
