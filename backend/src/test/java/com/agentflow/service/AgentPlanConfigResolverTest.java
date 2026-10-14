package com.agentflow.service;

import com.agentflow.engine.model.WorkflowNode;
import com.agentflow.entity.LLMGlobalConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPlanConfigResolverTest {

    @Test
    void resolveKnowledgeConfig_UsesSelectedQwenEmbeddingModel() {
        LLMGlobalConfigService service = mock(LLMGlobalConfigService.class);
        LLMGlobalConfig config = new LLMGlobalConfig();
        config.setId(12L);
        config.setProvider("qwen");
        config.setModel("chat-model");
        config.setEmbeddingModel("text-embedding-v4");
        config.setApiUrl("https://example.test/compatible-mode");
        config.setApiKey("test-key");
        when(service.requireConfig(7L, 12L)).thenReturn(config);

        ResolvedAgentPlanConfig resolved = new AgentPlanConfigResolver(service).resolveKnowledgeConfig(7L, 12L, null);

        assertEquals("qwen", resolved.provider());
        assertEquals("text-embedding-v4", resolved.model());
        assertEquals(config.getApiUrl(), resolved.apiUrl());
        assertEquals("test-key", resolved.apiKey());
    }

    @Test
    void resolveKnowledgeConfig_DoesNotInventAnUnconfiguredModel() {
        AgentPlanConfigResolver resolver = new AgentPlanConfigResolver(mock(LLMGlobalConfigService.class));
        ResolvedAgentPlanConfig resolved = resolver.resolveKnowledgeConfig(7L, null, null);

        assertNull(resolved.model());
        assertNull(resolved.embeddingModel());
        assertNull(resolved.apiUrl());
    }

    @Test
    void resolveKnowledgeConfig_PreservesExistingKnowledgeBaseModel() {
        AgentPlanConfigResolver resolver = new AgentPlanConfigResolver(mock(LLMGlobalConfigService.class));
        assertEquals("existing-model", resolver.resolveKnowledgeConfig(7L, null, "existing-model").model());
    }

    @Test
    void resolveImageConfig_UsesSelectedStepConfig_WhenAgentPlanDefaultExists() {
        LLMGlobalConfigService configService = mock(LLMGlobalConfigService.class);
        AgentPlanConfigResolver resolver = new AgentPlanConfigResolver(configService);

        LLMGlobalConfig stepConfig = new LLMGlobalConfig();
        stepConfig.setId(12L);
        stepConfig.setProvider("step");
        stepConfig.setApiUrl("https://api.stepfun.com/v1");
        stepConfig.setApiKey("step-key");
        stepConfig.setModel("step-3.5-flash");
        stepConfig.setImageModel("step-1x-medium");

        LLMGlobalConfig agentPlanDefault = new LLMGlobalConfig();
        agentPlanDefault.setId(33L);
        agentPlanDefault.setProvider("volcengine_agent_plan");
        agentPlanDefault.setApiUrl("https://ark.cn-beijing.volces.com/api/plan/v3");
        agentPlanDefault.setApiKey("ark-key");
        agentPlanDefault.setModel("doubao-model");
        agentPlanDefault.setImageModel("seedream-model");

        when(configService.getOwnedById(7L, 12L)).thenReturn(stepConfig);
        when(configService.getDefaultConfig(7L, "volcengine_agent_plan")).thenReturn(agentPlanDefault);

        WorkflowNode node = new WorkflowNode();
        node.setOwnerId(7L);
        Map<String, Object> data = new HashMap<>();
        data.put("configId", 12L);
        node.setData(data);

        ResolvedAgentPlanConfig resolved = resolver.resolveImageConfig(node);

        assertEquals(12L, resolved.configId());
        assertEquals("step", resolved.provider());
        assertEquals("https://api.stepfun.com/v1", resolved.apiUrl());
        assertEquals("step-key", resolved.apiKey());
        assertEquals("step-1x-medium", resolved.model());
    }
}
