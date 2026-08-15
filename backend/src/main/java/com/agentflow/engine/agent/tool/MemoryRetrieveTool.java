package com.agentflow.engine.agent.tool;

import com.agentflow.engine.agent.AgentTool;
import com.agentflow.engine.agent.AgentToolContext;
import com.agentflow.service.AgentMemoryService;
import com.agentflow.service.AgentPlanConfigResolver;
import com.agentflow.service.ResolvedAgentPlanConfig;
import com.agentflow.service.VolcengineAgentPlanClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MemoryRetrieveTool implements AgentTool {

    private final AgentPlanConfigResolver configResolver;
    private final VolcengineAgentPlanClient agentPlanClient;
    private final AgentMemoryService memoryService;

    public MemoryRetrieveTool(AgentPlanConfigResolver configResolver,
                              VolcengineAgentPlanClient agentPlanClient,
                              AgentMemoryService memoryService) {
        this.configResolver = configResolver;
        this.agentPlanClient = agentPlanClient;
        this.memoryService = memoryService;
    }

    @Override
    public String getName() {
        return "memory_retrieve";
    }

    @Override
    public String getDescription() {
        return "从 Agent 长期记忆中召回与当前任务相关的偏好、背景和历史结论。";
    }

    @Override
    public String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"scope\":{\"type\":\"string\"},\"topK\":{\"type\":\"number\"}},\"required\":[\"query\"]}";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments, AgentToolContext context) throws Exception {
        String query = String.valueOf(arguments.getOrDefault("query", ""));
        String scope = String.valueOf(arguments.getOrDefault("scope", "workflow"));
        int topK = arguments.get("topK") instanceof Number number ? number.intValue() : 5;
        ResolvedAgentPlanConfig config = configResolver.resolve(context.node(), "memory");
        List<Double> embedding = List.of();
        if (config.apiUrl() != null && config.apiKey() != null && config.model() != null) {
            embedding = agentPlanClient.createEmbedding(config, query);
        }
        return memoryService.retrieve(query, scope, List.of(), embedding, topK);
    }
}
