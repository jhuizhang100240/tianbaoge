package com.tbgbook.bookstore.app.security;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final AppTokenService tokenService;

    private final ObjectMapper objectMapper;

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
        if (memberId == null && !isPublic(uri))
        {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    AjaxResult.error(401, "登录已失效，请重新登录")));
            return false;
        }
        if (memberId != null)
        {
            AppContext.setMemberId(memberId);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex)
    {
        AppContext.clear();
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
