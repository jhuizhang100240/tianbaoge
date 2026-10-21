package com.tbgbook.bookstore.app.client;

/**
 * 微信 code2Session 结果。
 */
public record WechatSession(String openid, String sessionKey)
{
}
