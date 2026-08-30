package com.agentflow.service;

import com.agentflow.common.BizException;
import com.agentflow.common.PasswordUtil;
import com.agentflow.entity.User;
import com.agentflow.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 邮箱验证码服务（Redis 存储）
 * <p>规则与 NexusMind 保持一致：6 位数字、BCrypt 哈希存储、10 分钟有效、
 * 错误 5 次作废、同一邮箱每分钟最多 1 次、每小时最多 5 次。</p>
 */
@Service
public class EmailVerificationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern CODE_PATTERN = Pattern.compile("\\d{6}");
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final int MAX_SEND_PER_MINUTE = 1;
    private static final int MAX_SEND_PER_HOUR = 5;

    private static final String CODE_KEY_PREFIX = "auth:email-code:";
    private static final String ATTEMPTS_KEY_PREFIX = "auth:email-code:attempts:";
    private static final String MINUTE_LIMIT_KEY_PREFIX = "auth:email-code:limit:1m:";
    private static final String HOUR_LIMIT_KEY_PREFIX = "auth:email-code:limit:1h:";

    private final SecureRandom random = new SecureRandom();

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MailService mailService;

    @Autowired
    private UserMapper userMapper;

    /**
     * 发送注册验证码
     */
    public void sendRegistrationCode(String rawEmail) {
        String email = validateEmail(rawEmail);
        if (!mailService.isConfigured()) {
            throw new BizException(503, "邮件服务尚未配置，请联系管理员");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0) {
            throw new BizException(409, "该邮箱已注册，请直接登录");
        }
        enforceRateLimit(email);

        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        // 重新发送时覆盖旧验证码，旧的自动失效
        stringRedisTemplate.opsForValue().set(CODE_KEY_PREFIX + email, PasswordUtil.encode(code), CODE_TTL);
        stringRedisTemplate.delete(ATTEMPTS_KEY_PREFIX + email);
        mailService.sendVerificationCode(email, code);
    }

    /**
     * 校验并消费验证码（一次性），验证失败累计次数，超过上限后验证码作废
     */
    public void verifyAndConsume(String rawEmail, String code) {
        String email = validateEmail(rawEmail);
        String codeKey = CODE_KEY_PREFIX + email;
        String storedHash = stringRedisTemplate.opsForValue().get(codeKey);
        if (storedHash == null) {
            throw invalidCode();
        }
        if (code == null || !CODE_PATTERN.matcher(code).matches() || !PasswordUtil.matches(code, storedHash)) {
            recordFailureAndMaybeBurn(email, codeKey);
            throw invalidCode();
        }
        stringRedisTemplate.delete(codeKey);
        stringRedisTemplate.delete(ATTEMPTS_KEY_PREFIX + email);
    }

    /**
     * 归一化邮箱（去空格 + 转小写）
     */
    public String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private void recordFailureAndMaybeBurn(String email, String codeKey) {
        String attemptsKey = ATTEMPTS_KEY_PREFIX + email;
        Long attempts = stringRedisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            stringRedisTemplate.expire(attemptsKey, CODE_TTL);
        }
        if (attempts != null && attempts >= MAX_VERIFY_ATTEMPTS) {
            stringRedisTemplate.delete(codeKey);
            stringRedisTemplate.delete(attemptsKey);
        }
    }

    private void enforceRateLimit(String email) {
        String minuteKey = MINUTE_LIMIT_KEY_PREFIX + email;
        Long minuteCount = stringRedisTemplate.opsForValue().increment(minuteKey);
        if (minuteCount != null && minuteCount == 1) {
            stringRedisTemplate.expire(minuteKey, Duration.ofMinutes(1));
        }
        if (minuteCount != null && minuteCount > MAX_SEND_PER_MINUTE) {
            throw tooFrequent();
        }
        String hourKey = HOUR_LIMIT_KEY_PREFIX + email;
        Long hourCount = stringRedisTemplate.opsForValue().increment(hourKey);
        if (hourCount != null && hourCount == 1) {
            stringRedisTemplate.expire(hourKey, Duration.ofHours(1));
        }
        if (hourCount != null && hourCount > MAX_SEND_PER_HOUR) {
            throw tooFrequent();
        }
    }

    private String validateEmail(String rawEmail) {
        String email = normalize(rawEmail);
        if (!EMAIL_PATTERN.matcher(email).matches() || email.length() > 320) {
            throw new BizException(400, "邮箱格式不正确");
        }
        return email;
    }

    private BizException invalidCode() {
        return new BizException(400, "验证码错误或已过期");
    }

    private BizException tooFrequent() {
        return new BizException(429, "操作频繁，请稍后再试");
    }
}
