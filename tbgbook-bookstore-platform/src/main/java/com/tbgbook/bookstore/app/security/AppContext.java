package com.tbgbook.bookstore.app.security;

import com.tbgbook.bookstore.common.exception.BizErrors;

/**
 * 会员端当前请求上下文。
 *
 * @author tbgbook
 */
public final class AppContext
{
    private static final ThreadLocal<Long> MEMBER_ID = new ThreadLocal<>();

    private AppContext()
    {
    }

    public static void setMemberId(Long memberId)
    {
        MEMBER_ID.set(memberId);
    }

    public static Long getMemberId()
    {
        return MEMBER_ID.get();
    }

    public static Long requireMemberId()
    {
        Long memberId = MEMBER_ID.get();
        if (memberId == null)
        {
            throw BizErrors.unauthorized("登录已失效，请重新登录");
        }
        return memberId;
    }

    public static void clear()
    {
        MEMBER_ID.remove();
    }
}
