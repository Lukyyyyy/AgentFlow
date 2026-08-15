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
public class MemoryWriteTool implements AgentTool {

    private final AgentPlanConfigResolver configResolver;
    private final VolcengineAgentPlanClient agentPlanClient;
    private final AgentMemoryService memoryService;

    public MemoryWriteTool(AgentPlanConfigResolver configResolver,
                           VolcengineAgentPlanClient agentPlanClient,
                           AgentMemoryService memoryService) {
        this.configResolver = configResolver;
        this.agentPlanClient = agentPlanClient;
        this.memoryService = memoryService;
    }

    @Override
    public String getName() {
        return "memory_write";
    }

    @Override
    public String getDescription() {
        return "显式写入 Agent 长期记忆，适合保存用户偏好、项目背景和稳定结论。";
    }

    @Override
    public String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"content\":{\"type\":\"string\"},\"memoryType\":{\"type\":\"string\"},\"scope\":{\"type\":\"string\"},\"tags\":{\"type\":\"string\"}},\"required\":[\"content\"]}";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments, AgentToolContext context) throws Exception {
        String content = String.valueOf(arguments.getOrDefault("content", ""));
        String scope = String.valueOf(arguments.getOrDefault("scope", "workflow"));
        String memoryType = String.valueOf(arguments.getOrDefault("memoryType", "fact"));
        String tagsText = String.valueOf(arguments.getOrDefault("tags", ""));
        ResolvedAgentPlanConfig config = configResolver.resolve(context.node(), "memory");
        List<Double> embedding = List.of();
        if (config.apiUrl() != null && config.apiKey() != null && config.model() != null) {
            embedding = agentPlanClient.createEmbedding(config, content);
        }
        return memoryService.write(content, memoryType, scope, memoryService.splitTags(tagsText), context.node().getId(), embedding, config.model());
    }
}
