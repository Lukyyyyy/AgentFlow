package com.agentflow.service;

import com.agentflow.common.BizException;
import com.agentflow.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.*;

class EmailVerificationServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String CODE_KEY = "auth:email-code:" + EMAIL;

    private EmailVerificationService service;
    private Map<String, String> store;
    private MailService mailService;
    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        store = new HashMap<>();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        mailService = mock(MailService.class);
        userMapper = mock(UserMapper.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doAnswer(invocation -> {
            store.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(valueOperations.get(anyString())).thenAnswer(invocation -> store.get(invocation.getArgument(0)));
        when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long next = Long.parseLong(store.getOrDefault(key, "0")) + 1;
            store.put(key, Long.toString(next));
            return next;
        });
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(redisTemplate.delete(anyString())).thenAnswer(invocation -> store.remove(invocation.getArgument(0)) != null);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(mailService.isConfigured()).thenReturn(true);

        service = new EmailVerificationService();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redisTemplate);
        ReflectionTestUtils.setField(service, "mailService", mailService);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
    }

    @Test
    void shouldSendCodeAndStoreHash() {
        service.sendRegistrationCode("  User@Example.COM ");

        assertNotNull(store.get(CODE_KEY));
        verify(mailService).sendVerificationCode(eq(EMAIL), matches("\\d{6}"));
    }

    @Test
    void shouldRejectSecondSendWithinOneMinute() {
        service.sendRegistrationCode(EMAIL);

        BizException e = assertThrows(BizException.class, () -> service.sendRegistrationCode(EMAIL));
        assertEquals(429, e.getCode());
        verify(mailService, times(1)).sendVerificationCode(anyString(), anyString());
    }

    @Test
    void shouldRejectWhenEmailAlreadyRegistered() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        BizException e = assertThrows(BizException.class, () -> service.sendRegistrationCode(EMAIL));
        assertEquals(409, e.getCode());
    }

    @Test
    void shouldRejectWhenMailNotConfigured() {
        when(mailService.isConfigured()).thenReturn(false);

        BizException e = assertThrows(BizException.class, () -> service.sendRegistrationCode(EMAIL));
        assertEquals(503, e.getCode());
    }

    @Test
    void shouldConsumeCorrectCodeOnce() {
        service.sendRegistrationCode(EMAIL);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendVerificationCode(anyString(), codeCaptor.capture());

        service.verifyAndConsume("User@Example.com", codeCaptor.getValue());

        assertNull(store.get(CODE_KEY));
        BizException e = assertThrows(BizException.class,
                () -> service.verifyAndConsume(EMAIL, codeCaptor.getValue()));
        assertEquals(400, e.getCode());
    }

    @Test
    void shouldBurnCodeAfterFiveFailures() {
        service.sendRegistrationCode(EMAIL);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendVerificationCode(anyString(), codeCaptor.capture());
        String wrongCode = "000000".equals(codeCaptor.getValue()) ? "000001" : "000000";

        for (int i = 0; i < 5; i++) {
            BizException e = assertThrows(BizException.class, () -> service.verifyAndConsume(EMAIL, wrongCode));
            assertEquals(400, e.getCode());
        }

        assertNull(store.get(CODE_KEY));
        assertNull(store.get("auth:email-code:attempts:" + EMAIL));
    }
}
