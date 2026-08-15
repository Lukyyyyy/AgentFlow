package com.agentflow.service;

import com.agentflow.entity.LLMGlobalConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LLMGlobalConfigServiceTest {

    private final LLMGlobalConfigService service = new LLMGlobalConfigService();

    @Test
    void shouldAllowTtsOnlyConfigWithoutLlmModel() {
        LLMGlobalConfig config = validBaseConfig();
        config.setTtsModel(" qwen3-tts-flash ");

        ReflectionTestUtils.invokeMethod(service, "normalizeConfig", config);

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "validateConfig", config));
        assertEquals("qwen3-tts-flash", config.getTtsModel());
    }

    @Test
    void shouldRejectConfigWithoutAnyModelCapability() {
        LLMGlobalConfig config = validBaseConfig();

        ReflectionTestUtils.invokeMethod(service, "normalizeConfig", config);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validateConfig", config)
        );
        assertEquals("请至少配置一种模型能力", error.getMessage());
    }

    private LLMGlobalConfig validBaseConfig() {
        LLMGlobalConfig config = new LLMGlobalConfig();
        config.setProvider("qwen");
        config.setConfigName("qwen-tts");
        config.setApiUrl("https://dashscope.aliyuncs.com/api/v1");
        config.setApiKey("test-key");
        return config;
    }
}
