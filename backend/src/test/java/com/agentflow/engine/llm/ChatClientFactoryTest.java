package com.agentflow.engine.llm;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ChatClientFactoryTest {

    private final ChatClientFactory factory = new ChatClientFactory();

    @Test
    void createClient_acceptsSkyClawProviderAliasAsOpenAiCompatible() {
        ChatClient client = assertDoesNotThrow(() ->
                factory.createClient(
                        "skyclaw-v1.0",
                        "https://api.apifree.ai/agent/v1/chat/completions",
                        "test-api-key",
                        "skywork-ai/skyclaw-v1",
                        0.7
                )
        );

        assertNotNull(client);
    }

    @Test
    void normalizeModel_mapsSkyClawProductNamesToApifreeModelIds() throws Exception {
        Method normalizeModel = ChatClientFactory.class.getDeclaredMethod("normalizeModel", String.class, String.class);
        normalizeModel.setAccessible(true);

        assertEquals("skywork-ai/skyclaw-v1",
                normalizeModel.invoke(factory, "apifree", "skyclaw-v1.0"));
        assertEquals("skywork-ai/skyclaw-v1-lite",
                normalizeModel.invoke(factory, "apifree", "skyclaw-v1.0-lite"));
    }

    @Test
    void apifreeClient_parsesOpenAiJsonReturnedAsTextPlain() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] response = """
                    {
                      "id": "chatcmpl-test",
                      "object": "chat.completion",
                      "created": 1779881763,
                      "model": "skywork-ai/skyclaw-v1",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "content": "pong"
                          },
                          "finish_reason": "stop"
                        }
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain;charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            ChatClient client = factory.createClient(
                    "apifree",
                    "http://localhost:" + server.getAddress().getPort(),
                    "test-api-key",
                    "skywork-ai/skyclaw-v1",
                    0.7
            );

            String content = client.prompt().user("ping").call().chatResponse()
                    .getResult().getOutput().getContent();

            assertEquals("pong", content);
        } finally {
            server.stop(0);
        }
    }
}
