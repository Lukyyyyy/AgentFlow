package com.agentflow.service;

import com.agentflow.common.BizException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.ses.v20201002.SesClient;
import com.tencentcloudapi.ses.v20201002.models.SendEmailRequest;
import com.tencentcloudapi.ses.v20201002.models.Template;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 邮件服务，双发送通道：
 * <ul>
 *   <li>smtp（默认）：JavaMailSender 直发内联 HTML</li>
 *   <li>tencent-ses：腾讯云 SES API 模板发送（模板变量 code / minutes）</li>
 * </ul>
 * 通过 agentflow.mail.provider 切换。未配置完整时发送类操作返回 503。
 */
@Slf4j
@Service
public class MailService {

    private static final String PROVIDER_TENCENT_SES = "tencent-ses";
    private static final long CODE_VALIDITY_MINUTES = 10;
    private static final String CODE_SUBJECT = "AgentFlow 邮箱验证码";

    private final String provider;
    private final ObjectMapper objectMapper;

    // SMTP 通道
    private final String host;
    private final String from;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    // 腾讯云 SES 通道
    private final SesClient sesClient;
    private final String sesFromAddress;
    private final long sesVerificationTemplateId;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                       ObjectMapper objectMapper,
                       @Value("${spring.mail.host:}") String host,
                       @Value("${agentflow.mail.from:${spring.mail.username:}}") String from,
                       @Value("${agentflow.mail.provider:smtp}") String provider,
                       @Value("${agentflow.mail.tencent-ses.secret-id:}") String sesSecretId,
                       @Value("${agentflow.mail.tencent-ses.secret-key:}") String sesSecretKey,
                       @Value("${agentflow.mail.tencent-ses.region:ap-hongkong}") String sesRegion,
                       @Value("${agentflow.mail.tencent-ses.from-address:}") String sesFromAddress,
                       @Value("${agentflow.mail.tencent-ses.verification-template-id:0}") long sesVerificationTemplateId) {
        this.mailSenderProvider = mailSenderProvider;
        this.objectMapper = objectMapper;
        this.host = host;
        this.from = from;
        this.provider = provider == null ? "" : provider.trim().toLowerCase();
        this.sesFromAddress = sesFromAddress == null ? "" : sesFromAddress.trim();
        this.sesVerificationTemplateId = sesVerificationTemplateId;
        this.sesClient = sesSecretId == null || sesSecretId.isBlank() || sesSecretKey == null || sesSecretKey.isBlank()
                ? null
                : new SesClient(new Credential(sesSecretId.trim(), sesSecretKey.trim()), sesRegion == null ? "ap-hongkong" : sesRegion.trim());
    }

    /**
     * 当前通道是否已配置完整
     */
    public boolean isConfigured() {
        if (usesTencentSes()) {
            return sesClient != null && !sesFromAddress.isBlank() && sesVerificationTemplateId > 0;
        }
        return host != null && !host.isBlank();
    }

    /**
     * 发送注册验证码邮件
     */
    public void sendVerificationCode(String to, String code) {
        if (usesTencentSes()) {
            sendViaTencentSes(to, code);
        } else {
            sendViaSmtp(to, code);
        }
    }

    private boolean usesTencentSes() {
        return PROVIDER_TENCENT_SES.equals(provider);
    }

    /**
     * 腾讯云 SES API 模板发送（模板变量：code、minutes）
     */
    private void sendViaTencentSes(String to, String code) {
        if (!isConfigured()) {
            throw new BizException(503, "腾讯云 SES 尚未配置完整，请联系管理员");
        }
        try {
            SendEmailRequest request = new SendEmailRequest();
            request.setFromEmailAddress(sesFromAddress);
            request.setDestination(new String[]{to});
            request.setSubject(CODE_SUBJECT);
            Template template = new Template();
            template.setTemplateID(sesVerificationTemplateId);
            template.setTemplateData(objectMapper.writeValueAsString(
                    Map.of("code", code, "minutes", String.valueOf(CODE_VALIDITY_MINUTES))));
            request.setTemplate(template);
            sesClient.SendEmail(request);
        } catch (TencentCloudSDKException | JsonProcessingException e) {
            log.error("腾讯云 SES 发送验证码邮件失败: {}", to, e);
            throw new BizException(500, "验证码邮件发送失败，请稍后重试");
        }
    }

    /**
     * SMTP 直发内联 HTML
     */
    private void sendViaSmtp(String to, String code) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null || host == null || host.isBlank()) {
            throw new BizException(503, "邮件服务尚未配置，请联系管理员");
        }
        if (from == null || from.isBlank()) {
            throw new BizException(503, "邮件发件人未配置，请联系管理员");
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(CODE_SUBJECT);
            helper.setText("<p>你的验证码是：<strong>" + code + "</strong></p>"
                    + "<p>验证码 " + CODE_VALIDITY_MINUTES + " 分钟内有效，请勿泄露给他人。</p>"
                    + "<p>如果这不是你本人的操作，请忽略本邮件。</p>", true);
            sender.send(message);
        } catch (MessagingException | MailException e) {
            log.error("发送验证码邮件失败: {}", to, e);
            throw new BizException(500, "验证码邮件发送失败，请稍后重试");
        }
    }
}
