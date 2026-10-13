package com.tbgbook.bookstore.ai.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 敏感信息脱敏（NFR-S8）：消息内容落库前统一调用。
 *
 * <p>规则：① 11 位手机号（1[3-9] 开头）→ {@code 138****1234}；
 * ② 「学号」后跟的 4~20 位字母数字 → 保留前 4 后 2。其余内容不动
 * （避免误伤订单号一类业务编号）。
 *
 * @author tbgbook
 */
@Component
public class SensitiveMasker
{
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");

    private static final Pattern STUDENT_NO =
            Pattern.compile("(学号[:：]?\\s*)([0-9A-Za-z]{4})[0-9A-Za-z]+([0-9A-Za-z]{2})");

    public String mask(String text)
    {
        if (!StringUtils.hasText(text))
        {
            return text;
        }
        String masked = PHONE.matcher(text).replaceAll(match ->
                match.group(1).substring(0, 3) + "****" + match.group(1).substring(7));
        Matcher student = STUDENT_NO.matcher(masked);
        return student.replaceAll("$1$2******$3");
    }
}
