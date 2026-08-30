package com.agentflow.service;

import com.agentflow.common.BizException;
import com.agentflow.common.PasswordUtil;
import com.agentflow.entity.User;
import com.agentflow.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

/**
 * 邮箱注册服务
 */
@Slf4j
@Service
public class RegistrationService {

    private static final String PASSWORD_INVALID_MESSAGE = "密码需为8-72个字符，不能包含控制字符";
    private static final String USERNAME_SUFFIX_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int USERNAME_MAX_PREFIX_LENGTH = 24;

    private final SecureRandom random = new SecureRandom();

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EmailVerificationService emailVerificationService;

    /**
     * 邮箱注册：校验验证码后创建用户，返回自动生成的用户名
     */
    @Transactional
    public String register(String rawEmail, String code, String password) {
        String email = emailVerificationService.normalize(rawEmail);
        validatePassword(password);
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0) {
            throw new BizException(409, "该邮箱已注册，请直接登录");
        }
        emailVerificationService.verifyAndConsume(email, code);

        String username = generateUsername(email);
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(PasswordUtil.encode(password));
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "该邮箱已注册，请直接登录");
        }
        log.info("新用户注册成功: {} ({})", username, email);
        return username;
    }

    /**
     * 基于邮箱前缀生成唯一用户名（前缀 + 下划线 + 4 位随机字符）
     */
    private String generateUsername(String email) {
        String prefix = email.substring(0, email.indexOf('@')).replaceAll("[^a-z0-9_]", "");
        if (prefix.isEmpty()) {
            prefix = "user";
        }
        if (prefix.length() > USERNAME_MAX_PREFIX_LENGTH) {
            prefix = prefix.substring(0, USERNAME_MAX_PREFIX_LENGTH);
        }
        for (int i = 0; i < 5; i++) {
            StringBuilder suffix = new StringBuilder("_");
            for (int j = 0; j < 4; j++) {
                suffix.append(USERNAME_SUFFIX_CHARS.charAt(random.nextInt(USERNAME_SUFFIX_CHARS.length())));
            }
            String candidate = prefix + suffix;
            if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, candidate)) == 0) {
                return candidate;
            }
        }
        throw new BizException(500, "注册失败，请稍后重试");
    }

    private void validatePassword(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 8
                || password.codePointCount(0, password.length()) > 72
                || password.codePoints().anyMatch(Character::isISOControl)) {
            throw new BizException(400, PASSWORD_INVALID_MESSAGE);
        }
    }
}
