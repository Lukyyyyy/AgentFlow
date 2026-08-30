package com.agentflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MailServiceTest {

    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> mailSenderProvider = mock(ObjectProvider.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldConfigureSmtpWhenHostPresent() {
        MailService service = new MailService(mailSenderProvider, objectMapper,
                "smtp.qcloudmail.com", "AgentFlow <noreply@example.com>", "smtp",
                "", "", "ap-hongkong", "", 0);
        assertTrue(service.isConfigured());
    }

    @Test
    void shouldNotConfigureSmtpWhenHostBlank() {
        MailService service = new MailService(mailSenderProvider, objectMapper,
                "", "", "smtp", "", "", "ap-hongkong", "", 0);
        assertFalse(service.isConfigured());
    }

    @Test
    void shouldConfigureTencentSesWhenAllParamsPresent() {
        MailService service = new MailService(mailSenderProvider, objectMapper,
                "", "", "tencent-ses",
                "AKIDtest", "secret-key", "ap-hongkong",
                "AI工作流 <agentflow-noreply@mail.lukybetter.com>", 213714);
        assertTrue(service.isConfigured());
    }

    @Test
    void shouldNotConfigureTencentSesWhenTemplateIdMissing() {
        MailService service = new MailService(mailSenderProvider, objectMapper,
                "smtp.qcloudmail.com", "", "tencent-ses",
                "AKIDtest", "secret-key", "ap-hongkong",
                "AI工作流 <agentflow-noreply@mail.lukybetter.com>", 0);
        assertFalse(service.isConfigured());
    }

    @Test
    void shouldNotConfigureTencentSesWhenSecretMissing() {
        MailService service = new MailService(mailSenderProvider, objectMapper,
                "smtp.qcloudmail.com", "", "Tencent-Ses",
                "", "", "ap-hongkong",
                "AI工作流 <agentflow-noreply@mail.lukybetter.com>", 213714);
        assertFalse(service.isConfigured());
    }
}
