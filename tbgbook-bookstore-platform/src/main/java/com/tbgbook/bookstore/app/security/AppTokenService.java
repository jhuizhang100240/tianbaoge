package com.tbgbook.bookstore.app.security;

import java.util.Date;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

/**
 * 会员端 JWT：与后台 `TokenService` 的 LoginUser 体系完全隔离。
 *
 * @author tbgbook
 */
@Component
public class AppTokenService
{
    @Value("${token.secret}")
    private String secret;

    @Value("${tbgbook.app.token-expire-seconds:1800}")
    private int expireSeconds;

    public String createToken(Long memberId)
    {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(String.valueOf(memberId))
                .setId(UUID.randomUUID().toString())
                .claim("typ", "app")
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expireSeconds * 1000L))
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    public Long parseMemberId(String token)
    {
        Claims claims = Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
        if (!"app".equals(claims.get("typ")))
        {
            throw new IllegalArgumentException("not an app token");
        }
        return Long.valueOf(claims.getSubject());
    }

    public int getExpireSeconds()
    {
        return expireSeconds;
    }
}
