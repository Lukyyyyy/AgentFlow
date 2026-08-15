package com.agentflow.engine.agent.tool;

import com.agentflow.engine.agent.AgentToolContext;
import com.agentflow.engine.model.WorkflowNode;
import com.agentflow.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeRetrieveToolTest {

    @Test
    void shouldUseNodeKnowledgeSettingsOverModelArguments() {
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        KnowledgeRetrieveTool tool = new KnowledgeRetrieveTool(knowledgeBaseService);
        WorkflowNode node = new WorkflowNode();
        Map<String, Object> nodeData = new LinkedHashMap<>();
        nodeData.put("knowledgeBaseId", "3");
        nodeData.put("knowledgeTopK", 5);
        nodeData.put("knowledgeScoreThreshold", 0.2);
        node.setData(nodeData);
        when(knowledgeBaseService.searchRuntime("3", "联网搜索怎么做", 5, 0.2)).thenReturn(Map.of("chunks", Map.of()));

        tool.execute(
                Map.of("query", "联网搜索怎么做", "topK", 1, "scoreThreshold", 0.7),
                new AgentToolContext(node, Map.of("input", "联网搜索怎么做"))
        );

        verify(knowledgeBaseService).searchRuntime("3", "联网搜索怎么做", 5, 0.2);
    }
}
