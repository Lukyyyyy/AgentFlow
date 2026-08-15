package com.agentflow.engine.executor.impl;

import com.agentflow.engine.model.WorkflowNode;
import com.agentflow.service.AgentPlanConfigResolver;
import com.agentflow.service.KnowledgeBaseService;
import com.agentflow.service.ResolvedAgentPlanConfig;
import com.agentflow.service.VolcengineAgentPlanClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class KnowledgeRetrieveNodeExecutor extends AbstractAgentPlanNodeExecutor {

    private final AgentPlanConfigResolver configResolver;
    private final VolcengineAgentPlanClient agentPlanClient;
    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeRetrieveNodeExecutor(AgentPlanConfigResolver configResolver,
                                         VolcengineAgentPlanClient agentPlanClient,
                                         KnowledgeBaseService knowledgeBaseService) {
        this.configResolver = configResolver;
        this.agentPlanClient = agentPlanClient;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) throws Exception {
        String query = textValue(node, input, "query", "input");
        if (query == null) {
            throw new IllegalArgumentException("检索知识库节点缺少 query");
        }

        ResolvedAgentPlanConfig config = configResolver.resolve(node, "knowledge");
        List<Double> embedding = List.of();
        if (config.apiUrl() != null && config.apiKey() != null && config.model() != null) {
            embedding = agentPlanClient.createEmbedding(config, query);
        }

        Map<String, Object> output = knowledgeBaseService.retrieve(
                stringData(node, "knowledgeBaseId", "default"),
                query,
                embedding,
                intData(node, "topK", 5),
                doubleData(node, "scoreThreshold", 0.2)
        );
        output.put("output", output.get("context"));
        return output;
    }

    @Override
    public String getSupportedNodeType() {
        return "knowledge_retrieve";
    }
}
