package com.agentflow.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VolcengineAgentPlanClientTest {

    @ParameterizedTest
    @CsvSource({
            "qwen,/compatible-mode,/compatible-mode/v1/embeddings",
            "qwen,/compatible-mode/v1/,/compatible-mode/v1/embeddings",
            "openai,'',/v1/embeddings",
            "volcengine_agent_plan,/api/plan/v3,/api/plan/v3/embeddings"
    })
    void shouldCallEmbeddingEndpointForSelectedProvider(String provider, String basePath, String expectedPath) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<JSONObject> body = new AtomicReference<>();
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            body.set(JSON.parseObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] response = "{\"data\":[{\"embedding\":[0.1,0.2]}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            ResolvedAgentPlanConfig config = new ResolvedAgentPlanConfig(9L, provider,
                    "http://localhost:" + server.getAddress().getPort() + basePath, "test-key",
                    "text-embedding-v4", "text-embedding-v4", null, null, false);

            assertEquals(List.of(0.1, 0.2), new VolcengineAgentPlanClient().createEmbedding(config, "资料"));
            assertEquals(expectedPath, path.get());
            assertEquals("text-embedding-v4", body.get().getString("model"));
            assertEquals("资料", body.get().getString("input"));
        } finally {
            server.stop(0);
        }
    }
}
