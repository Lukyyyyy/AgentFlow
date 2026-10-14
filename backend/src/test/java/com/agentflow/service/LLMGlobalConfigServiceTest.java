package com.agentflow.service;

import com.agentflow.entity.LLMGlobalConfig;
import com.agentflow.engine.llm.ChatClientFactory;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LLMGlobalConfigServiceTest {

    private final LLMGlobalConfigService service = new LLMGlobalConfigService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "chatClientFactory", new ChatClientFactory());
    }

    @ParameterizedTest
    @CsvSource({
            "qwen, /compatible-mode/v1/, /compatible-mode/v1/chat/completions, chat",
            "qwen, /compatible-mode/v1/chat/completions, /compatible-mode/v1/chat/completions, chat",
            "qwen, /compatible-mode/v1, /compatible-mode/v1/embeddings, embedding",
            "ark, /api/plan/v3, /api/plan/v3/chat/completions, chat",
            "skyclaw, /agent/v1, /agent/v1/chat/completions, chat",
            "qwen, /api/v1, /api/v1/services/aigc/multimodal-generation/generation, qwen-tts",
            "qwen, /compatible-mode/v1/, /api/v1/services/aigc/multimodal-generation/generation, qwen-tts",
            "qwen, /api/v1/services/aigc/multimodal-generation/generation, /api/v1/services/aigc/multimodal-generation/generation, qwen-tts",
            "step, /v1, /v1/audio/speech, step-tts",
            "step, /step_plan/v1/audio/speech, /step_plan/v1/audio/speech, step-tts",
            "step, /v1, /v1/models, models"
    })
    void shouldTestUnsavedConfiguration(String provider, String basePath, String expectedPath, String capability) throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            method.set(exchange.getRequestMethod());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String response = switch (capability) {
                case "chat" -> "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}";
                case "embedding" -> "{\"data\":[{\"embedding\":[0.1,0.2]}]}";
                case "qwen-tts" -> "{\"output\":{\"audio\":{\"url\":\"https://example.com/audio.wav\"}}}";
                default -> "{\"data\":[]}";
            };
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            if ("step-tts".equals(capability)) {
                bytes = new byte[48];
                System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
                System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, bytes, 8, 4);
            }
            exchange.getResponseHeaders().set("Content-Type", "step-tts".equals(capability) ? "audio/wav" : "text/plain");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            LLMGlobalConfig config = validBaseConfig();
            config.setProvider(provider);
            config.setApiUrl("http://localhost:" + server.getAddress().getPort() + basePath);
            config.setApiKey(" test-key ");
            if ("chat".equals(capability)) {
                config.setModel("skyclaw".equals(provider) ? "skyclaw-v1.0" : "test-chat");
                config.setEmbeddingModel("other-embedding");
            } else if ("embedding".equals(capability)) {
                config.setEmbeddingModel("text-embedding-v4");
            } else if (capability.endsWith("-tts")) {
                config.setTtsModel("qwen-tts".equals(capability) ? "qwen3-tts-flash" : "stepaudio-2.5-tts");
            } else {
                config.setImageModel("test-image");
            }

            assertTrue(service.testConnection(config).startsWith("连接成功"));
            assertEquals(expectedPath, path.get());
            assertEquals("Bearer test-key", authorization.get());
            assertEquals("models".equals(capability) ? "GET" : "POST", method.get());
            if (!"models".equals(capability)) {
                JSONObject request = JSON.parseObject(body.get());
                String expectedModel = switch (capability) {
                    case "chat" -> "skyclaw".equals(provider) ? "skywork-ai/skyclaw-v1" : "test-chat";
                    case "qwen-tts" -> "qwen3-tts-flash";
                    case "step-tts" -> "stepaudio-2.5-tts";
                    default -> "text-embedding-v4";
                };
                assertEquals(expectedModel, request.getString("model"));
                if ("qwen-tts".equals(capability)) {
                    assertEquals("连接测试。", request.getJSONObject("input").getString("text"));
                    assertEquals("Cherry", request.getJSONObject("input").getString("voice"));
                    assertEquals("Auto", request.getJSONObject("input").getString("language_type"));
                } else if ("step-tts".equals(capability)) {
                    assertEquals("连接测试。", request.getString("input"));
                    assertEquals("cixingnansheng", request.getString("voice"));
                    assertEquals("wav", request.getString("response_format"));
                }
            }
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"qwen", "step"})
    void shouldRejectTtsErrorResponseEvenWhenHttpStatusIsSuccessful(String provider) throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            byte[] bytes = "{\"code\":\"InvalidApiKey\",\"message\":\"secret-api-key\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            LLMGlobalConfig config = validBaseConfig();
            config.setProvider(provider);
            config.setApiUrl("http://localhost:" + server.getAddress().getPort() + ("qwen".equals(provider) ? "/api/v1" : "/v1"));
            config.setTtsModel("qwen".equals(provider) ? "qwen3-tts-flash" : "stepaudio-2.5-tts");
            String expected = "qwen".equals(provider)
                    ? "TTS 接口未返回有效的音频地址，请检查模型名称和配置"
                    : "TTS 接口未返回有效的音频，请检查模型名称和配置";
            assertEquals(expected, assertThrows(IllegalArgumentException.class,
                    () -> service.testConnection(config)).getMessage());
            assertTrue(!path.get().endsWith("/models"));
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "401, 密钥无效或没有访问权限",
            "403, 密钥无效或没有访问权限",
            "404, 接口或模型不存在，请检查 API 地址和模型名称",
            "429, 请求受限或额度不足",
            "503, 服务返回 HTTP 503，请检查配置或稍后重试",
            "200, 接口返回格式无效，请检查 API 地址"
    })
    void shouldReportErrorsWithoutLeakingProviderResponse(int status, String expectedMessage) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/models", exchange -> {
            byte[] bytes = "secret-api-key".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            LLMGlobalConfig config = validBaseConfig();
            config.setApiUrl("http://localhost:" + server.getAddress().getPort() + "/v1");
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> service.testConnection(config));
            assertEquals(expectedMessage, error.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRejectInvalidUrlAndBlankKeyBeforeSendingRequest() {
        LLMGlobalConfig config = validBaseConfig();
        config.setApiUrl("file:///tmp/secret");
        assertThrows(IllegalArgumentException.class, () -> service.testConnection(config));
        config.setApiUrl("https://user:password@example.com/v1");
        assertThrows(IllegalArgumentException.class, () -> service.testConnection(config));
        config.setApiUrl("https://example.com/v1");
        config.setApiKey(" ");
        assertEquals("请填写 API 地址和密钥", assertThrows(IllegalArgumentException.class,
                () -> service.testConnection(config)).getMessage());
    }

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
