package com.agentflow.engine.executor.impl;

import com.agentflow.engine.model.WorkflowNode;
import com.agentflow.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeRetrieveNodeExecutorTest {

    private final KnowledgeBaseService service = mock(KnowledgeBaseService.class);
    private final KnowledgeRetrieveNodeExecutor executor = new KnowledgeRetrieveNodeExecutor(service);

    @Test
    void shouldResolveUpstreamQuestionAndKeepSources() throws Exception {
        WorkflowNode node = node(Map.of(
                "inputParams", List.of(Map.of("name", "query", "type", "reference", "referenceNode", "input-1.user_input")),
                "query", "过时的固定问题", "topK", 3, "scoreThreshold", 0.0));
        Map<String, Object> retrieved = new LinkedHashMap<>(Map.of(
                "context", "参考资料", "chunks", List.of(Map.of("chunkId", 11L, "title", "说明")),
                "citations", List.of(11L)));
        when(service.searchRuntime(7L, "3", "用户问题", 3, 0.0)).thenReturn(retrieved);

        Map<String, Object> result = executor.execute(node, Map.of("input", "用户问题"));

        assertEquals("参考资料", result.get("output"));
        assertEquals(List.of(11L), result.get("citations"));
        assertSame(retrieved.get("chunks"), result.get("chunks"));
    }

    @Test
    void shouldKeepLegacyFixedQueryAndReturnEmptyContext() throws Exception {
        WorkflowNode node = node(Map.of("query", "固定问题"));
        when(service.searchRuntime(7L, "3", "固定问题", 5, 0.2))
                .thenReturn(new LinkedHashMap<>(Map.of("context", "", "chunks", List.of(), "citations", List.of())));

        Map<String, Object> result = executor.execute(node, Map.of("input", "用户问题"));

        assertEquals("", result.get("output"));
        assertEquals(List.of(), result.get("chunks"));
    }

    @Test
    void shouldRejectUnresolvedReferenceInsteadOfUsingUnrelatedInput() {
        WorkflowNode node = node(Map.of("inputParams", List.of(
                Map.of("name", "query", "type", "reference", "referenceNode", "upstream.missing"))));

        assertThrows(IllegalArgumentException.class, () -> executor.execute(node, Map.of("input", "其他问题")));
        verifyNoInteractions(service);
    }

    @Test
    void shouldRequireQuestionAndSelectedKnowledgeBase() {
        assertThrows(IllegalArgumentException.class, () -> executor.execute(node(Map.of("query", "  ")), Map.of()));
        WorkflowNode node = node(Map.of("query", "问题"));
        node.getData().remove("knowledgeBaseId");
        assertThrows(IllegalArgumentException.class, () -> executor.execute(node, Map.of()));
        verifyNoInteractions(service);
    }

    @Test
    void shouldPropagateRetrievalFailure() {
        when(service.searchRuntime(7L, "3", "问题", 5, 0.2)).thenThrow(new IllegalStateException("检索失败"));
        assertThrows(IllegalStateException.class, () -> executor.execute(node(Map.of("query", "问题")), Map.of()));
    }

    private WorkflowNode node(Map<String, Object> data) {
        WorkflowNode node = new WorkflowNode();
        node.setOwnerId(7L);
        node.setData(new LinkedHashMap<>(data));
        node.getData().put("knowledgeBaseId", "3");
        return node;
    }
}
