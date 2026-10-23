package com.tbgbook.bookstore.app.client;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbgbook.bookstore.common.exception.BizErrors;
import com.tbgbook.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;

/**
 * 微信小程序服务端 API（code2Session / 手机号）。
 *
 * <p>生产配置 `wechat.appId` / `wechat.appSecret`；开发默认 mock 登录，不访问微信。
 *
 * @author tbgbook
 */
@Component
@RequiredArgsConstructor
public class AppWechatClient
{
    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.builder().baseUrl("https://api.weixin.qq.com").build();

    @Value("${wechat.appId:}")
    private String appId;

    @Value("${wechat.appSecret:}")
    private String appSecret;

    public WechatSession code2Session(String code)
    {
        try
        {
            JsonNode node = restClient.get()
                    .uri("/sns/jscode2session?appid={appid}&secret={secret}&js_code={code}"
                                    + "&grant_type=authorization_code",
                            appId, appSecret, code)
                    .retrieve().body(JsonNode.class);
            if (node == null || node.has("errcode"))
            {
                throw BizErrors.ruleViolated("code 无效或已使用");
            }
            String openid = node.path("openid").asText("");
            String sessionKey = node.path("session_key").asText("");
            if (!StringUtils.hasText(openid) || !StringUtils.hasText(sessionKey))
            {
                throw BizErrors.ruleViolated("code 无效或已使用");
            }
            return new WechatSession(openid, sessionKey);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (RuntimeException e)
        {
            throw BizErrors.ruleViolated("微信登录失败，请重试");
        }
    }

    public String phone(WechatSession session, String phoneCode, String encryptedData, String iv)
    {
        if (StringUtils.hasText(phoneCode))
        {
            try
            {
                String accessToken = accessToken();
                JsonNode node = restClient.post()
                        .uri("/wxa/business/getuserphonenumber?access_token={token}", accessToken)
                        .body(Map.of("code", phoneCode))
                        .retrieve().body(JsonNode.class);
                String phone = node == null ? "" : node.path("phone_info").path("phoneNumber").asText("");
                if (StringUtils.hasText(phone))
                {
                    return phone;
                }
            }
            catch (RuntimeException e)
            {
                // 回退到 encryptedData 解密
            }
        }
        if (StringUtils.hasText(encryptedData) && StringUtils.hasText(iv))
        {
            try
            {
                String plain = decrypt(session.sessionKey(), encryptedData, iv);
                String phone = objectMapper.readTree(plain).path("phoneNumber").asText("");
                if (StringUtils.hasText(phone))
                {
                    return phone;
                }
            }
            catch (Exception ignored)
            {
                // 统一抛 422
            }
        }
        throw BizErrors.ruleViolated("手机号解密失败");
    }

    private String accessToken()
    {
        JsonNode node = restClient.get()
                .uri("/cgi-bin/token?grant_type=client_credential&appid={appid}&secret={secret}",
                        appId, appSecret)
                .retrieve().body(JsonNode.class);
        String token = node == null ? "" : node.path("access_token").asText("");
        if (!StringUtils.hasText(token))
        {
            throw BizErrors.ruleViolated("微信 access_token 获取失败");
        }
        return token;
    }

    private String decrypt(String sessionKey, String encryptedData, String iv) throws Exception
    {
        byte[] keyBytes = Base64.getDecoder().decode(sessionKey);
        byte[] ivBytes = Base64.getDecoder().decode(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        Key key = new SecretKeySpec(keyBytes, "AES");
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(ivBytes));
        return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedData)), StandardCharsets.UTF_8);
    }

}
