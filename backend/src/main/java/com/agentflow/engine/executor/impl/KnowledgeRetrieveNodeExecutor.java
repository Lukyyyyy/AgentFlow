package com.agentflow.engine.executor.impl;

import com.agentflow.engine.model.WorkflowNode;
import com.agentflow.service.KnowledgeBaseService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Component
public class KnowledgeRetrieveNodeExecutor extends AbstractAgentPlanNodeExecutor {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeRetrieveNodeExecutor(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) throws Exception {
        String query = node.getData().get("inputParams") instanceof List<?> params && !params.isEmpty()
                ? configuredTextInput(node, input, "query")
                : textValue(node, input, "query", "input");
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("检索知识库节点缺少 query");
        }

        String knowledgeBaseId = stringData(node, "knowledgeBaseId", null);
        if (knowledgeBaseId == null) {
            throw new IllegalArgumentException("请选择要检索的知识库");
        }

        Map<String, Object> output = knowledgeBaseService.searchRuntime(
                node.getOwnerId(),
                knowledgeBaseId,
                query,
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
