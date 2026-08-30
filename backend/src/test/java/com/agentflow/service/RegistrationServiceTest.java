package com.agentflow.service;

import com.agentflow.common.BizException;
import com.agentflow.entity.User;
import com.agentflow.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RegistrationServiceTest {

    private RegistrationService service;
    private UserMapper userMapper;
    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        service = new RegistrationService();
        userMapper = mock(UserMapper.class);
        emailVerificationService = mock(EmailVerificationService.class);

        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenReturn(1);
        when(emailVerificationService.normalize(any())).thenAnswer(invocation -> {
            String email = invocation.getArgument(0);
            return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        });

        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "emailVerificationService", emailVerificationService);
    }

    @Test
    void shouldRegisterUserWithGeneratedUsername() {
        String username = service.register(" User@Example.COM ", "123456", "password123");

        assertNotNull(username);
        assertTrue(username.startsWith("user_"));
        verify(emailVerificationService).verifyAndConsume("user@example.com", "123456");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("user@example.com", captor.getValue().getEmail());
        assertNotEquals("password123", captor.getValue().getPasswordHash());
    }

    @Test
    void shouldRejectWeakPassword() {
        BizException e = assertThrows(BizException.class,
                () -> service.register("user@example.com", "123456", "short"));
        assertEquals(400, e.getCode());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void shouldRejectDuplicateEmail() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        BizException e = assertThrows(BizException.class,
                () -> service.register("user@example.com", "123456", "password123"));
        assertEquals(409, e.getCode());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void shouldRejectWhenCodeVerificationFails() {
        doThrow(new BizException(400, "验证码错误或已过期"))
                .when(emailVerificationService).verifyAndConsume(anyString(), anyString());

        BizException e = assertThrows(BizException.class,
                () -> service.register("user@example.com", "999999", "password123"));
        assertEquals(400, e.getCode());
        verify(userMapper, never()).insert(any(User.class));
    }
}
