package com.agentflow.engine.executor.impl;

import com.agentflow.engine.model.WorkflowNode;
import com.agentflow.service.AgentMemoryService;
import com.agentflow.service.AgentPlanConfigResolver;
import com.agentflow.service.ResolvedAgentPlanConfig;
import com.agentflow.service.VolcengineAgentPlanClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class MemoryRetrieveNodeExecutor extends AbstractAgentPlanNodeExecutor {

    private final AgentPlanConfigResolver configResolver;
    private final VolcengineAgentPlanClient agentPlanClient;
    private final AgentMemoryService memoryService;

    public MemoryRetrieveNodeExecutor(AgentPlanConfigResolver configResolver,
                                      VolcengineAgentPlanClient agentPlanClient,
                                      AgentMemoryService memoryService) {
        this.configResolver = configResolver;
        this.agentPlanClient = agentPlanClient;
        this.memoryService = memoryService;
    }

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) throws Exception {
        String query = textValue(node, input, "query", "input");
        if (query == null) {
            throw new IllegalArgumentException("召回记忆节点缺少 query");
        }

        ResolvedAgentPlanConfig config = configResolver.resolve(node, "memory");
        List<Double> embedding = List.of();
        if (config.apiUrl() != null && config.apiKey() != null && config.model() != null) {
            embedding = agentPlanClient.createEmbedding(config, query);
        }

        Map<String, Object> output = memoryService.retrieve(
                query,
                stringData(node, "scope", "workflow"),
                memoryService.splitTags(stringData(node, "tags", "")),
                embedding,
                intData(node, "topK", 5)
        );
        output.put("output", output.get("context"));
        return output;
    }

    @Override
    public String getSupportedNodeType() {
        return "memory_retrieve";
    }
}
