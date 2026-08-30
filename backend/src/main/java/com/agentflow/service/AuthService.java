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

    /**
     * 默认用户名（通过环境变量配置，不配置则禁用默认账户）
     */
    @Value("${agentflow.default-username:}")
    private String defaultUsername;

    /**
     * 默认密码（通过环境变量配置，不配置则禁用默认账户）
     */
    @Value("${agentflow.default-password:}")
    private String defaultPassword;

    /**
     * 默认账户邮箱（默认账户仅支持邮箱登录）
     */
    @Value("${agentflow.default-email:}")
    private String defaultEmail;

    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_PREFIX = "auth:refresh:";

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
        if (user != null) {
            if (PasswordUtil.matches(password, user.getPasswordHash())) {
                return issueTokens(user.getUsername());
            }
            return null;
        }
        // 数据库未命中时，回退到环境变量配置的默认账户（仅匹配邮箱）
        if (matchesDefaultAccount(normalizedEmail, password)) {
            return issueTokens(defaultUsername);
        }
        return null;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean matchesDefaultAccount(String normalizedEmail, String password) {
        String configuredEmail = normalizeEmail(defaultEmail);
        return !configuredEmail.isEmpty()
                && configuredEmail.equals(normalizedEmail)
                && defaultPassword != null && !defaultPassword.isEmpty()
                && defaultPassword.equals(password);
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
        String accessToken = createAccessToken(username);
        String refreshToken = createRefreshToken();

        stringRedisTemplate.opsForValue().set(
                buildRefreshTokenKey(refreshToken),
                username,
                Duration.ofHours(refreshTokenExpirationHours)
        );

        return new AuthTokens(accessToken, refreshToken, username, getEmailByUsername(username));
    }

    /**
     * 根据用户名解析邮箱（默认账户使用配置邮箱，数据库用户查表，兜底返回用户名）
     */
    public String getEmailByUsername(String username) {
        if (username == null) {
            return null;
        }
        if (defaultUsername != null && !defaultUsername.isEmpty() && defaultUsername.equals(username)) {
            String configuredEmail = normalizeEmail(defaultEmail);
            if (!configuredEmail.isEmpty()) {
                return configuredEmail;
            }
        }
        if (userMapper != null) {
            User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            if (user != null && user.getEmail() != null && !user.getEmail().isEmpty()) {
                return user.getEmail();
            }
        }
        return username;
    }

    private String createAccessToken(String username) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenExpirationMinutes, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(username)
                .claim("tokenType", ACCESS_TOKEN_TYPE)
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

    public record AuthTokens(String accessToken, String refreshToken, String username, String email) {
    }
}
