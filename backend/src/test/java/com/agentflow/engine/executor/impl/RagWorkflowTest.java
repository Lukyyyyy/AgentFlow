package com.agentflow.engine.executor.impl;

import com.agentflow.dto.ExecutionResponse;
import com.agentflow.engine.WorkflowConfigParser;
import com.agentflow.engine.WorkflowEngine;
import com.agentflow.engine.agent.AgentTool;
import com.agentflow.engine.agent.AgentToolRegistry;
import com.agentflow.engine.agent.tool.KnowledgeRetrieveTool;
import com.agentflow.engine.dag.DAGParser;
import com.agentflow.engine.executor.NodeExecutorFactory;
import com.agentflow.engine.llm.ChatClientFactory;
import com.agentflow.engine.llm.PromptTemplateService;
import com.agentflow.engine.model.WorkflowNode;
import com.agentflow.entity.Workflow;
import com.agentflow.mapper.ExecutionRecordMapper;
import com.agentflow.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagWorkflowTest {

    private final KnowledgeBaseService knowledge = mock(KnowledgeBaseService.class);
    private final ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final ChatClientFactory factory = mock(ChatClientFactory.class);

    @Test
    void shouldPassOriginalQuestionAndRagContextToLlm() {
        when(knowledge.searchRuntime(7L, "3", "用户问题", 5, 0.2)).thenReturn(new LinkedHashMap<>(Map.of(
                "context", "参考资料", "chunks", List.of(), "citations", List.of())));
        when(factory.createClientWithFunctions(anyString(), anyString(), anyString(), anyString(), anyDouble(), anyList()))
                .thenReturn(client);
        when(client.prompt().user(anyString()).call().chatResponse()).thenReturn(response("有依据的答案"));
        LlmNodeExecutor llm = new LlmNodeExecutor(mock(ReActAgentNodeExecutor.class));
        configure(llm);
        WorkflowEngine engine = new WorkflowEngine();
        ReflectionTestUtils.setField(engine, "dagParser", new DAGParser());
        ReflectionTestUtils.setField(engine, "workflowConfigParser", new WorkflowConfigParser());
        ReflectionTestUtils.setField(engine, "executionRecordMapper", mock(ExecutionRecordMapper.class));
        ReflectionTestUtils.setField(engine, "executorFactory", new NodeExecutorFactory(List.of(
                new InputNodeExecutor(), new KnowledgeRetrieveNodeExecutor(knowledge), llm)));
        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setOwnerId(7L);
        workflow.setFlowData("""
                {"nodes":[
                  {"id":"input","type":"input","data":{}},
                  {"id":"rag","type":"knowledge_retrieve","data":{"knowledgeBaseId":"3",
                    "inputParams":[{"name":"query","type":"reference","referenceNode":"input.user_input"}]}},
                  {"id":"llm","type":"llm","data":{"provider":"openai","apiUrl":"https://example.test",
                    "apiKey":"test-key","model":"test-model","prompt":"问题：{{question}}；资料：{{context}}",
                    "inputParams":[{"name":"question","type":"reference","referenceNode":"input.user_input"},
                                   {"name":"context","type":"reference","referenceNode":"rag.context"}]}}
                ],"edges":[{"source":"input","target":"rag"},{"source":"input","target":"llm"},
                            {"source":"rag","target":"llm"}]}
                """);

        ExecutionResponse result = engine.execute(workflow, "用户问题");

        assertEquals("SUCCESS", result.getStatus());
        verify(client.prompt()).user("问题：用户问题；资料：参考资料");
        verify(knowledge, times(1)).searchRuntime(7L, "3", "用户问题", 5, 0.2);
    }

    @Test
    void shouldLetReActRetrieveThenAnswerUsingObservation() throws Exception {
        ReActAgentNodeExecutor executor = reAct();
        when(knowledge.searchRuntime(7L, "3", "检索问题", 5, 0.2))
                .thenReturn(Map.of("context", "参考资料", "citations", List.of(11L)));
        when(client.prompt(any(Prompt.class)).call().chatResponse()).thenReturn(
                response("""
                        {"action":"tool_call","toolName":"knowledge_retrieve","toolInput":{"query":"检索问题"}}
                        """),
                response("""
                        {"action":"final_answer","finalAnswer":"有依据的答案"}
                        """));
        clearInvocations(client);

        Map<String, Object> result = executor.execute(agentNode(), Map.of("input", "用户问题"));

        assertEquals("有依据的答案", result.get("output"));
        verify(knowledge).searchRuntime(7L, "3", "检索问题", 5, 0.2);
        var prompts = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(client, times(2)).prompt(prompts.capture());
        assertTrue(prompts.getAllValues().get(1).getContents().contains("参考资料"));
    }

    @Test
    void shouldRejectRegisteredToolThatCurrentNodeDidNotEnable() throws Exception {
        ReActAgentNodeExecutor executor = reAct();
        AgentTool forbidden = mock(AgentTool.class);
        when(forbidden.getName()).thenReturn("forbidden");
        ReflectionTestUtils.setField(executor, "toolRegistry", new AgentToolRegistry(List.of(
                new KnowledgeRetrieveTool(knowledge), forbidden)));
        when(client.prompt(any(Prompt.class)).call().chatResponse()).thenReturn(response("""
                {"action":"tool_call","toolName":"forbidden","toolInput":{}}
                """));

        assertThrows(IllegalArgumentException.class, () -> executor.execute(agentNode(), Map.of()));

        verify(forbidden, never()).execute(anyMap(), any());
        verifyNoInteractions(knowledge);
    }

    private ReActAgentNodeExecutor reAct() {
        ReActAgentNodeExecutor executor = new ReActAgentNodeExecutor();
        configure(executor);
        ReflectionTestUtils.setField(executor, "toolRegistry", new AgentToolRegistry(List.of(new KnowledgeRetrieveTool(knowledge))));
        when(factory.createClient(anyString(), anyString(), anyString(), anyString(), anyDouble())).thenReturn(client);
        return executor;
    }

    private void configure(AbstractLLMNodeExecutor executor) {
        ReflectionTestUtils.setField(executor, "chatClientFactory", factory);
        ReflectionTestUtils.setField(executor, "promptTemplateService", new PromptTemplateService());
    }

    private WorkflowNode agentNode() {
        WorkflowNode node = new WorkflowNode();
        node.setId("agent");
        node.setType("llm");
        node.setOwnerId(7L);
        node.setData(Map.of("provider", "openai", "apiUrl", "https://example.test", "apiKey", "test-key",
                "model", "test-model", "prompt", "回答问题", "knowledgeBaseId", "3",
                "tools", List.of("knowledge_retrieve")));
        return node;
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
