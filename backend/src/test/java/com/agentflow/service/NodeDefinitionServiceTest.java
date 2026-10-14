package com.agentflow.service;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NodeDefinitionServiceTest {

    @Test
    void shouldExposeRagWithoutRequiringSeededDatabaseRows() {
        NodeDefinitionService service = spy(new NodeDefinitionService());
        doReturn(List.of()).when(service).list();

        var nodes = service.listAllNodeDefinitions();
        var rag = nodes.stream().filter(node -> "knowledge_retrieve".equals(node.getNodeType())).findFirst().orElseThrow();

        assertEquals("KNOWLEDGE", rag.getCategory());
        assertTrue(JSON.parseObject(rag.getOutputSchema()).getJSONObject("properties").containsKey("context"));
        assertFalse(JSON.parseObject(rag.getConfigSchema()).getJSONObject("properties").containsKey("configId"));
        assertTrue(nodes.stream().noneMatch(node -> "knowledge_upsert".equals(node.getNodeType())));
    }
}
