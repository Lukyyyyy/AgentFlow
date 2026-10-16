package com.agentflow.engine.langgraph;

import com.agentflow.dto.ExecutionEvent;
import com.agentflow.engine.WorkflowConfigParser;
import com.agentflow.engine.executor.NodeExecutor;
import com.agentflow.engine.executor.NodeExecutorFactory;
import com.agentflow.engine.executor.impl.ConditionNodeExecutor;
import com.agentflow.engine.executor.impl.InputNodeExecutor;
import com.agentflow.engine.langgraph.adapter.NodeAdapter;
import com.agentflow.engine.langgraph.builder.GraphBuilder;
import com.agentflow.engine.langgraph.state.StateManager;
import com.agentflow.engine.model.WorkflowConfig;
import com.agentflow.dto.ExecutionResponse;
import com.agentflow.entity.ExecutionRecord;
import com.agentflow.entity.Workflow;
import com.agentflow.mapper.ExecutionRecordMapper;
import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class GraphBuilderTest {
    private final GraphBuilder builder = new GraphBuilder();
    private final StateManager states = new StateManager();
    private final List<String> visited = new ArrayList<>();
    private final List<ExecutionEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        NodeExecutor step = new NodeExecutor() {
            public String getSupportedNodeType() { return "step"; }
            public Map<String, Object> execute(com.agentflow.engine.model.WorkflowNode node, Map<String, Object> input) {
                visited.add(node.getId());
                if (Boolean.TRUE.equals(node.getData().get("fail"))) {
                    throw new IllegalStateException("模拟节点失败");
                }
                Map<String, Object> result = new HashMap<>(input);
                result.put("count", ((Number) input.getOrDefault("count", 0)).intValue() + 1);
                return result;
            }
        };
        NodeAdapter adapter = new NodeAdapter();
        ReflectionTestUtils.setField(adapter, "executorFactory", new NodeExecutorFactory(
                List.of(new InputNodeExecutor(), new ConditionNodeExecutor(), step)));
        ReflectionTestUtils.setField(builder, "nodeAdapter", adapter);
    }

    private WorkflowConfig config(String nodes, String edges, int maxSteps) {
        return new WorkflowConfigParser().parse("{\"nodes\":" + nodes + ",\"edges\":" + edges
                + ",\"maxSteps\":" + maxSteps + "}");
    }

    private Map<String, Object> run(WorkflowConfig config, Map<String, Object> input) throws Exception {
        Map<String, Object> initial = states.initializeState("test");
        initial.put("currentInput", input);
        return builder.buildGraph(config, events::add).invoke(initial).orElseThrow().data();
    }

    private WorkflowConfig branching(int maxSteps) {
        return config("""
                [{"id":"in","type":"input"},
                 {"id":"if","type":"condition","data":{"conditions":[
                   {"id":"again","field":"count","operator":"lt","value":3},
                   {"id":"second","field":"count","operator":"lt","value":10}]}},
                 {"id":"a","type":"step","data":{}},
                 {"id":"b","type":"step","data":{}},
                 {"id":"d","type":"step","data":{}}]
                """, """
                [{"source":"in","target":"if"},
                 {"source":"if","target":"a","sourceHandle":"again"},
                 {"source":"if","target":"b","sourceHandle":"second"},
                 {"source":"if","target":"d","sourceHandle":"default"}]
                """, maxSteps);
    }

    @Test
    void firstMatchingBranchOnlyExecutesItsOwnTerminal() throws Exception {
        Map<String, Object> result = run(branching(100), Map.of("count", 1));
        assertEquals(List.of("a"), visited);
        assertEquals(2, ((Map<?, ?>) result.get("currentInput")).get("count"));
        assertTrue(states.isSuccessful(result));
    }

    @Test
    void secondAndDefaultBranchesWork() throws Exception {
        run(branching(100), Map.of("count", 5));
        assertEquals(List.of("b"), visited);
        visited.clear();
        run(branching(100), Map.of("count", 20));
        assertEquals(List.of("d"), visited);
    }

    @Test
    void conditionCanRouteByJsonTextReturnedFromLlm() throws Exception {
        WorkflowConfig config = new WorkflowConfigParser().parse(
                JSON.toJSONString(branching(100)).replace("\"field\":\"count\"", "\"field\":\"output.count\""));
        run(config, Map.of("output", "{\"count\":1}"));
        assertEquals(List.of("a"), visited);
    }

    @Test
    void unconnectedSelectedBranchEndsWithoutRunningOtherBranches() throws Exception {
        WorkflowConfig config = branching(100);
        config.getEdges().removeIf(e -> "default".equals(e.getSourceHandle()));
        config.getNodes().removeIf(n -> "d".equals(n.getId()));
        Map<String, Object> result = run(config, Map.of("count", 20));
        assertTrue(visited.isEmpty());
        assertTrue(states.isSuccessful(result));
    }

    private WorkflowConfig loop(int maxSteps) {
        return config("""
                [{"id":"in","type":"input"},
                 {"id":"if","type":"condition","data":{"conditions":[
                   {"id":"again","field":"count","operator":"lt","value":3}]}},
                 {"id":"body","type":"step","data":{}},
                 {"id":"out","type":"step","data":{}}]
                """, """
                [{"source":"in","target":"if"},
                 {"source":"if","target":"body","sourceHandle":"again"},
                 {"source":"body","target":"if"},
                 {"source":"if","target":"out","sourceHandle":"default"}]
                """, maxSteps);
    }

    @Test
    void loopUsesUpdatedStateAndPreservesEveryIterationInOrder() throws Exception {
        WorkflowConfig config = loop(100);
        Map<String, Object> result = run(config, Map.of("count", 0));
        assertEquals(List.of("body", "body", "body", "out"), visited);
        assertTrue(states.isSuccessful(result));
        List<Map<String, Object>> history = states.extractNodeResults(result, config);
        assertEquals(List.of("in", "if", "body", "if", "body", "if", "body", "if", "out"),
                history.stream().map(r -> r.get("nodeId")).toList());
        assertTrue(((String) history.get(2).get("output")).contains("\"count\":1"));
        assertTrue(((String) history.get(6).get("output")).contains("\"count\":3"));
        assertEquals(9L, events.stream().filter(e -> "NODE_SUCCESS".equals(e.getEventType())).count());
    }

    @Test
    void exceedingBudgetFailsInsteadOfSilentlyReturningSuccess() throws Exception {
        Map<String, Object> result = run(loop(4), Map.of("count", 0));
        assertEquals(List.of("body"), visited);
        assertFalse(states.isSuccessful(result));
        assertTrue(states.getErrorMessage(result).contains("最大执行步数 4"));
        assertEquals(1L, events.stream().filter(e -> "NODE_ERROR".equals(e.getEventType())).count());
    }

    @Test
    void exactBudgetCanFinishSuccessfully() throws Exception {
        assertTrue(states.isSuccessful(run(loop(9), Map.of("count", 0))));
    }

    @Test
    void longerLoopDoesNotSilentlyStopAtLibraryDefaultLimit() throws Exception {
        WorkflowConfig config = new WorkflowConfigParser().parse(
                JSON.toJSONString(loop(100)).replace("\"value\":3", "\"value\":30"));
        Map<String, Object> result = run(config, Map.of("count", 0));
        assertTrue(states.isSuccessful(result));
        assertEquals(30, Collections.frequency(visited, "body"));
        assertEquals("out", visited.get(visited.size() - 1));
        assertEquals(63, states.extractNodeResults(result, config).size());
    }

    @Test
    void cycleWithoutExitFailsAtBudget() throws Exception {
        WorkflowConfig config = config("[{\"id\":\"in\",\"type\":\"input\"}]",
                "[{\"source\":\"in\",\"target\":\"in\"}]", 3);
        Map<String, Object> result = run(config, Map.of());
        assertFalse(states.isSuccessful(result));
        assertEquals(3, result.get("executionSteps"));
        assertTrue(states.getErrorMessage(result).contains("最大执行步数 3"));
    }

    @Test
    void enginePersistsLoopHistoryAndReturnsBudgetFailureDetails() {
        LangGraphWorkflowEngine engine = new LangGraphWorkflowEngine();
        ReflectionTestUtils.setField(engine, "graphBuilder", builder);
        ReflectionTestUtils.setField(engine, "stateManager", states);
        ReflectionTestUtils.setField(engine, "workflowConfigParser", new WorkflowConfigParser());
        ExecutionRecordMapper mapper = mock(ExecutionRecordMapper.class);
        List<ExecutionRecord> records = new ArrayList<>();
        when(mapper.insert(any(ExecutionRecord.class))).thenAnswer(invocation -> {
            ExecutionRecord record = invocation.getArgument(0);
            record.setId(42L);
            records.add(record);
            return 1;
        });
        ReflectionTestUtils.setField(engine, "executionRecordMapper", mapper);
        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setFlowData(JSON.toJSONString(loop(9)));
        ExecutionResponse success = engine.execute(workflow, "{\"count\":0}");
        assertEquals("SUCCESS", success.getStatus());
        assertEquals(9, success.getNodeResults().size());
        workflow.setFlowData(JSON.toJSONString(loop(4)));
        ExecutionResponse failure = engine.executeWithCallback(workflow, "{\"count\":0}", events::add);
        assertEquals("FAILED", failure.getStatus());
        assertEquals(42L, failure.getExecutionId());
        assertTrue(failure.getErrorMessage().contains("最大执行步数 4"));
        assertEquals("FAILED", failure.getNodeResults().get(4).getStatus());
        assertTrue(failure.getNodeResults().get(4).getError().contains("最大执行步数"));
        assertTrue(records.get(1).getNodeResults().contains("FAILED"));
        assertEquals("FAILED", events.get(events.size() - 1).getStatus());
    }

    @Test
    void failureInsideLoopStopsBeforeAnyFurtherNode() throws Exception {
        WorkflowConfig config = loop(100);
        config.getNodes().stream().filter(n -> "body".equals(n.getId())).findFirst().orElseThrow()
                .getData().put("fail", true);
        Map<String, Object> result = run(config, Map.of("count", 0));
        assertEquals(List.of("body"), visited);
        assertFalse(states.isSuccessful(result));
        assertEquals("FAILED", states.extractNodeResults(result, config).get(2).get("status"));
    }

    @Test
    void backEdgeToInputStillUsesExplicitInputAsEntry() throws Exception {
        WorkflowConfig config = loop(6);
        config.getEdges().stream().filter(e -> "body".equals(e.getSource())).findFirst().orElseThrow().setTarget("in");
        Map<String, Object> result = run(config, Map.of("count", 0));
        assertFalse(states.isSuccessful(result));
        assertTrue(states.getErrorMessage(result).contains("最大执行步数"));
    }

    @Test
    void invalidBranchPortAndDuplicateConnectionsAreRejected() {
        WorkflowConfig config = branching(100);
        config.getEdges().get(1).setSourceHandle("missing");
        assertThrows(IllegalArgumentException.class, () -> builder.buildGraph(config, null));
        config.getEdges().get(1).setSourceHandle("default");
        assertThrows(IllegalArgumentException.class, () -> builder.buildGraph(config, null));
    }

    @Test
    void invalidNodesAndAmbiguousOrdinaryFanoutAreRejected() {
        WorkflowConfig config = branching(100);
        config.getEdges().get(1).setTarget("missing");
        assertThrows(IllegalArgumentException.class, () -> builder.buildGraph(config, null));
        WorkflowConfig fanout = branching(100);
        fanout.getNodes().get(1).setType("step");
        assertThrows(IllegalArgumentException.class, () -> builder.buildGraph(fanout, null));
    }

    @Test
    void disconnectedNodesMultipleEntriesAndDuplicateIdsAreRejected() {
        WorkflowConfig config = branching(100);
        config.getEdges().remove(0);
        assertThrows(IllegalArgumentException.class, () -> builder.buildGraph(config, null));
        WorkflowConfig multiple = branching(100);
        multiple.getNodes().get(2).setType("input");
        assertThrows(IllegalArgumentException.class, () -> builder.buildGraph(multiple, null));
        WorkflowConfig duplicate = branching(100);
        duplicate.getNodes().get(2).setId("in");
        assertThrows(IllegalArgumentException.class, () -> builder.buildGraph(duplicate, null));
    }
}
