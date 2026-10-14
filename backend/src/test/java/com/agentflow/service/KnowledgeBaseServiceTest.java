package com.agentflow.service;

import com.agentflow.entity.KnowledgeBase;
import com.agentflow.entity.KnowledgeChunk;
import com.agentflow.common.ResourceNotFoundException;
import com.agentflow.dto.KnowledgeBaseRequest;
import com.agentflow.entity.LLMGlobalConfig;
import com.agentflow.mapper.KnowledgeBaseMapper;
import com.agentflow.mapper.KnowledgeChunkMapper;
import com.agentflow.mapper.KnowledgeDocumentMapper;
import com.agentflow.mapper.KnowledgeIndexTaskMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class KnowledgeBaseServiceTest {

    @Test
    void shouldSaveSelectedEmbeddingModelAndLeaveUnconfiguredModelEmpty() {
        LLMGlobalConfigService configs = mock(LLMGlobalConfigService.class);
        LLMGlobalConfig qwen = new LLMGlobalConfig();
        qwen.setEmbeddingModel("text-embedding-v4");
        when(configs.requireConfig(7L, 9L)).thenReturn(qwen);
        KnowledgeBaseService service = new KnowledgeBaseService(mock(KnowledgeBaseMapper.class),
                mock(KnowledgeDocumentMapper.class), mock(KnowledgeChunkMapper.class),
                mock(KnowledgeIndexTaskMapper.class), new AgentPlanConfigResolver(configs),
                mock(VolcengineAgentPlanClient.class));
        KnowledgeBaseRequest request = new KnowledgeBaseRequest();
        request.setName("项目资料");
        request.setConfigId(9L);

        assertEquals("text-embedding-v4", service.createKnowledgeBase(7L, request).get("embeddingModel"));
        request.setConfigId(null);
        assertEquals(null, service.createKnowledgeBase(7L, request).get("embeddingModel"));
    }

    @Test
    void shouldUseKnowledgeBaseEmbeddingConfigurationForRuntimeSearch() throws Exception {
        KnowledgeBaseMapper baseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        AgentPlanConfigResolver resolver = mock(AgentPlanConfigResolver.class);
        VolcengineAgentPlanClient client = mock(VolcengineAgentPlanClient.class);
        KnowledgeBase base = new KnowledgeBase();
        base.setId(3L);
        base.setConfigId(9L);
        base.setEmbeddingModel("knowledge-embedding");
        when(baseMapper.selectOne(any())).thenReturn(base);
        when(chunkMapper.selectList(any())).thenReturn(List.of());
        ResolvedAgentPlanConfig config = new ResolvedAgentPlanConfig(
                9L, "volcengine_agent_plan", "https://example.test", "test-key", "knowledge-embedding",
                "knowledge-embedding", null, null, false);
        when(resolver.resolveKnowledgeConfig(7L, 9L, "knowledge-embedding")).thenReturn(config);
        when(client.createEmbedding(config, "问题")).thenReturn(List.of(1.0, 0.0));
        KnowledgeBaseService service = new KnowledgeBaseService(baseMapper, mock(KnowledgeDocumentMapper.class),
                chunkMapper, mock(KnowledgeIndexTaskMapper.class), resolver, client);

        service.searchRuntime(7L, "3", "问题", 5, 0.2);

        verify(client).createEmbedding(config, "问题");
    }

    @Test
    void shouldRejectInvalidRuntimeSearchBeforeReadingChunks() {
        KnowledgeChunkMapper chunks = mock(KnowledgeChunkMapper.class);
        KnowledgeBaseService service = createService(chunks);

        assertThrows(IllegalArgumentException.class, () -> service.searchRuntime(7L, "1", " ", 5, 0.2));
        assertThrows(IllegalArgumentException.class, () -> service.searchRuntime(null, "1", "问题", 5, 0.2));
        for (int topK : List.of(0, 21)) {
            assertThrows(IllegalArgumentException.class, () -> service.searchRuntime(7L, "1", "问题", topK, 0.2));
        }
        for (double threshold : List.of(-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException.class, () -> service.searchRuntime(7L, "1", "问题", 5, threshold));
        }
        verifyNoInteractions(chunks);
    }

    @Test
    void shouldRejectInaccessibleKnowledgeBaseBeforeReadingChunks() {
        KnowledgeChunkMapper chunks = mock(KnowledgeChunkMapper.class);
        KnowledgeBaseService service = new KnowledgeBaseService(mock(KnowledgeBaseMapper.class),
                mock(KnowledgeDocumentMapper.class), chunks, mock(KnowledgeIndexTaskMapper.class),
                mock(AgentPlanConfigResolver.class), mock(VolcengineAgentPlanClient.class));

        assertThrows(ResourceNotFoundException.class, () -> service.searchRuntime(7L, "3", "问题", 5, 0.2));
        verifyNoInteractions(chunks);
    }

    @Test
    void shouldScoreChineseTextMatchesWhenEmbeddingIsUnavailable() {
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        KnowledgeBaseService service = createService(chunkMapper);
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                chunk(1L, "联网搜索能力", "Agent Plan 支持联网搜索、网页读取和知识库检索，可以获取实时权威信息。"),
                chunk(2L, "语音能力", "TTS 节点负责把文本转换成语音，适合播报和配音。")
        ));

        Map<String, Object> result = service.retrieve(7L, "1", "联网搜索应该怎么做?", List.of(), 5, 0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chunks = (List<Map<String, Object>>) result.get("chunks");
        assertEquals(1, chunks.size());
        assertEquals(1L, chunks.getFirst().get("chunkId"));
        assertTrue((double) chunks.getFirst().get("score") > 0);
    }

    @Test
    void shouldNotReturnZeroScoreChunksWhenThresholdAllowsFallbackSearch() {
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        KnowledgeBaseService service = createService(chunkMapper);
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                chunk(1L, "语音能力", "TTS 节点负责把文本转换成语音，适合播报和配音。")
        ));

        Map<String, Object> result = service.retrieve(7L, "1", "联网搜索应该怎么做?", List.of(), 5, 0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chunks = (List<Map<String, Object>>) result.get("chunks");
        assertTrue(chunks.isEmpty());
        assertFalse(String.valueOf(result.get("context")).contains("TTS"));
    }

    private KnowledgeBaseService createService(KnowledgeChunkMapper chunkMapper) {
        KnowledgeBaseMapper baseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeBase base = new KnowledgeBase();
        base.setId(1L);
        base.setName("测试知识库");
        when(baseMapper.selectOne(any())).thenReturn(base);
        return new KnowledgeBaseService(
                baseMapper,
                mock(KnowledgeDocumentMapper.class),
                chunkMapper,
                mock(KnowledgeIndexTaskMapper.class),
                mock(AgentPlanConfigResolver.class),
                mock(VolcengineAgentPlanClient.class)
        );
    }

    private KnowledgeChunk chunk(Long id, String title, String content) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setKnowledgeBaseId(1L);
        chunk.setDocumentId(10L + id);
        chunk.setTitle(title);
        chunk.setContent(content);
        chunk.setEmbedding("[]");
        chunk.setStatus("READY");
        return chunk;
    }
}
