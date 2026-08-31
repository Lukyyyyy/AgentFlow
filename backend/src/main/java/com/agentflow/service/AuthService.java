package com.agentflow.service;

import com.agentflow.common.PasswordUtil;
import com.agentflow.config.JwtSecretProvider;
import com.agentflow.entity.User;
import com.agentflow.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * 认证服务
 */
@Service
public class AuthService {

    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_PREFIX = "auth:v2:refresh:";

    @Autowired
    private JwtSecretProvider jwtSecretProvider;

    @Value("${agentflow.auth.access-token-expiration-minutes:120}")
    private long accessTokenExpirationMinutes;

    @Value("${agentflow.auth.refresh-token-expiration-hours:168}")
    private long refreshTokenExpirationHours;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired(required = false)
    private UserMapper userMapper;

    /**
     * 用户登录（仅支持邮箱）
     */
    public AuthTokens login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        User user = findUserByEmail(normalizedEmail);
        if (user != null && PasswordUtil.matches(password, user.getPasswordHash())) {
            return issueTokens(user);
        }
        return null;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private User findUserByEmail(String normalizedEmail) {
        if (userMapper == null || normalizedEmail.isEmpty()) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, normalizedEmail));
    }

    public AuthTokens refresh(String refreshToken) {
        String username = getUsernameByRefreshToken(refreshToken);
        if (username == null) {
            return null;
        }

        revokeRefreshToken(refreshToken);
        return issueTokens(username);
    }
    
    /**
     * 用户登出
     */
    public void logout(String refreshToken) {
        revokeRefreshToken(refreshToken);
    }
    
    /**
     * 验证 Token
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return ACCESS_TOKEN_TYPE.equals(claims.get("tokenType"))
                    && claims.get("userId", Long.class) != null
                    && claims.getExpiration() != null
                    && claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * 获取 Token 对应的用户名
     */
    public String getUsernameByToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return ACCESS_TOKEN_TYPE.equals(claims.get("tokenType")) ? claims.getSubject() : null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public Long getUserIdByToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return ACCESS_TOKEN_TYPE.equals(claims.get("tokenType"))
                    ? claims.get("userId", Long.class)
                    : null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        stringRedisTemplate.delete(buildRefreshTokenKey(refreshToken));
    }

    /**
     * 为指定用户签发访问/刷新令牌（注册成功后自动登录复用）
     */
    public AuthTokens issueTokens(String username) {
        User user = userMapper == null ? null : userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new IllegalStateException("用户不存在");
        }
        return issueTokens(user);
    }

    private AuthTokens issueTokens(User user) {
        String accessToken = createAccessToken(user);
        String refreshToken = createRefreshToken();

        stringRedisTemplate.opsForValue().set(
                buildRefreshTokenKey(refreshToken),
                user.getUsername(),
                Duration.ofHours(refreshTokenExpirationHours)
        );

        return new AuthTokens(accessToken, refreshToken, user.getId(), user.getUsername(), user.getEmail());
    }

    /**
     * 根据用户名解析邮箱。
     */
    public String getEmailByUsername(String username) {
        if (username == null) {
            return null;
        }
        if (userMapper != null) {
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            if (user != null && user.getEmail() != null && !user.getEmail().isEmpty()) {
                return user.getEmail();
            }
        }
        return username;
    }

    private String createAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenExpirationMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("tokenType", ACCESS_TOKEN_TYPE)
                .claim("userId", user.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(getSigningKey())
                .compact();
    }

    private String createRefreshToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String getUsernameByRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        return stringRedisTemplate.opsForValue().get(buildRefreshTokenKey(refreshToken));
    }

    private String buildRefreshTokenKey(String refreshToken) {
        return REFRESH_TOKEN_PREFIX + refreshToken;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecretProvider.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public record AuthTokens(String accessToken, String refreshToken, Long userId, String username, String email) {
    }
}
