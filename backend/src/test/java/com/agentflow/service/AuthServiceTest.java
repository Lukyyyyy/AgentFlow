package com.agentflow.service;

import com.agentflow.config.JwtSecretProvider;
import com.agentflow.common.PasswordUtil;
import com.agentflow.entity.User;
import com.agentflow.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private static final String JWT_SECRET = "test-jwt-secret-key-for-auth-service-123456";
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin123";
    private static final String DEFAULT_EMAIL = "admin@example.com";

    @Test
    void shouldGenerateAndValidateJwtToken() {
        AuthService authService = createAuthService(new HashMap<>());

        AuthService.AuthTokens tokens = authService.login(DEFAULT_EMAIL, DEFAULT_PASSWORD);

        assertNotNull(tokens);
        assertNotNull(tokens.accessToken());
        assertNotNull(tokens.refreshToken());
        assertEquals(DEFAULT_EMAIL, tokens.email());
        assertEquals(7L, tokens.userId());
        assertTrue(authService.validateToken(tokens.accessToken()));
        assertEquals(DEFAULT_USERNAME, authService.getUsernameByToken(tokens.accessToken()));
    }

    @Test
    void shouldRefreshAcrossServiceInstances() {
        Map<String, String> redisStore = new HashMap<>();
        AuthService issuer = createAuthService(redisStore);
        AuthService validator = createAuthService(redisStore);

        AuthService.AuthTokens issued = issuer.login(DEFAULT_EMAIL, DEFAULT_PASSWORD);
        AuthService.AuthTokens refreshed = validator.refresh(issued.refreshToken());

        assertNotNull(issued);
        assertNotNull(refreshed);
        assertEquals(DEFAULT_EMAIL, refreshed.email());
        assertTrue(validator.validateToken(refreshed.accessToken()));
        assertEquals(DEFAULT_USERNAME, validator.getUsernameByToken(refreshed.accessToken()));
        assertNull(validator.refresh(issued.refreshToken()));
    }

    @Test
    void shouldRejectWrongCredentials() {
        AuthService authService = createAuthService(new HashMap<>());

        assertNull(authService.login(DEFAULT_EMAIL, "wrong-password"));
    }

    @Test
    void shouldRejectUsernameLogin() {
        AuthService authService = createAuthService(new HashMap<>(), false);

        // 仅支持邮箱登录：使用用户名即使密码正确也应拒绝
        assertNull(authService.login(DEFAULT_USERNAME, DEFAULT_PASSWORD));
    }

    private AuthService createAuthService(Map<String, String> redisStore) {
        return createAuthService(redisStore, true);
    }

    private AuthService createAuthService(Map<String, String> redisStore, boolean userExists) {
        AuthService authService = new AuthService();
        JwtSecretProvider jwtSecretProvider = mock(JwtSecretProvider.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        UserMapper userMapper = mock(UserMapper.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

        when(jwtSecretProvider.getSecret()).thenReturn(JWT_SECRET);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        User user = new User();
        user.setId(7L);
        user.setUsername(DEFAULT_USERNAME);
        user.setEmail(DEFAULT_EMAIL);
        user.setPasswordHash(PasswordUtil.encode(DEFAULT_PASSWORD));
        when(userMapper.selectOne(any())).thenReturn(userExists ? user : null);
        doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(valueOperations.get(anyString())).thenAnswer(invocation -> redisStore.get(invocation.getArgument(0)));
        when(redisTemplate.delete(anyString())).thenAnswer(invocation -> redisStore.remove(invocation.getArgument(0)) != null);

        ReflectionTestUtils.setField(authService, "jwtSecretProvider", jwtSecretProvider);
        ReflectionTestUtils.setField(authService, "accessTokenExpirationMinutes", 120L);
        ReflectionTestUtils.setField(authService, "refreshTokenExpirationHours", 168L);
        ReflectionTestUtils.setField(authService, "stringRedisTemplate", redisTemplate);
        ReflectionTestUtils.setField(authService, "userMapper", userMapper);
        return authService;
    }
}
