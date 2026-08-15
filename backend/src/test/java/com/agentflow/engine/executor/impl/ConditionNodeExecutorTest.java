package com.agentflow.engine.executor.impl;

import com.agentflow.engine.model.WorkflowNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 条件分支节点执行器单元测试
 */
class ConditionNodeExecutorTest {

    private ConditionNodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ConditionNodeExecutor();
    }

    @Test
    void testGetSupportedNodeType() {
        assertEquals("condition", executor.getSupportedNodeType());
    }

    @Test
    void testEqualConditionMatches() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "status", "eq", "success")
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("status", "success");
        input.put("data", "hello");

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("condition_0", output.get("__selectedBranch__"));
        assertEquals("success", output.get("status"));
        assertEquals("hello", output.get("data"));
    }

    @Test
    void testEqualConditionNotMatches() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "status", "eq", "success")
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("status", "failed");

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("default", output.get("__selectedBranch__"));
    }

    @Test
    void testNotEqualCondition() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "status", "neq", "error")
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("status", "success");

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("condition_0", output.get("__selectedBranch__"));
    }

    @Test
    void testGreaterThanCondition() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "score", "gt", "80")
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("score", 95);

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("condition_0", output.get("__selectedBranch__"));
    }

    @Test
    void testGreaterThanConditionNotMatches() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "score", "gt", "80")
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("score", 60);

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("default", output.get("__selectedBranch__"));
    }

    @Test
    void testGreaterThanOrEqualCondition() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "score", "gte", "80")
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("score", 80);

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("condition_0", output.get("__selectedBranch__"));
    }

    @Test
    void testLessThanCondition() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "score", "lt", "60")
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("score", 45);

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("condition_0", output.get("__selectedBranch__"));
    }

    @Test
    void testLessThanOrEqualCondition() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "score", "lte", "60")
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("score", 60);

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("condition_0", output.get("__selectedBranch__"));
    }

    @Test
    void testContainsCondition() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "message", "contains", "error")
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("message", "An error occurred");

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("condition_0", output.get("__selectedBranch__"));
    }

    @Test
    void testNotContainsCondition() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "message", "notContains", "error")
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("message", "All good");

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("condition_0", output.get("__selectedBranch__"));
    }

    @Test
    void testStartsWithCondition() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "output", "startsWith", "OK")
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("output", "OK: done");

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("condition_0", output.get("__selectedBranch__"));
    }

    @Test
    void testEndsWithCondition() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "output", "endsWith", ".json")
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("output", "file.json");

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("condition_0", output.get("__selectedBranch__"));
    }

    @Test
    void testIsEmptyCondition() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "data", "isEmpty", null)
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("data", "");

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("condition_0", output.get("__selectedBranch__"));
    }

    @Test
    void testIsNotEmptyCondition() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "data", "isNotEmpty", null)
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("data", "hello");

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("condition_0", output.get("__selectedBranch__"));
    }

    @Test
    void testMultipleConditionsFirstMatches() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "score", "gte", "90"),
                createCondition("condition_1", "score", "gte", "60")
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("score", 95);

        Map<String, Object> output = executor.execute(node, input);

        // 第一个条件匹配，应该返回 condition_0
        assertEquals("condition_0", output.get("__selectedBranch__"));
    }

    @Test
    void testMultipleConditionsSecondMatches() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "score", "gte", "90"),
                createCondition("condition_1", "score", "gte", "60")
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("score", 75);

        Map<String, Object> output = executor.execute(node, input);

        // 第二个条件匹配
        assertEquals("condition_1", output.get("__selectedBranch__"));
    }

    @Test
    void testMultipleConditionsNoneMatches() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "score", "gte", "90"),
                createCondition("condition_1", "score", "gte", "60")
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("score", 30);

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("default", output.get("__selectedBranch__"));
    }

    @Test
    void testNestedFieldAccess() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "result.status", "eq", "ok")
        ));

        Map<String, Object> input = new HashMap<>();
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        input.put("result", result);

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("condition_0", output.get("__selectedBranch__"));
    }

    @Test
    void testNullFieldDefaultBranch() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "nonexistent", "eq", "value")
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("other", "data");

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("default", output.get("__selectedBranch__"));
    }

    @Test
    void testNullFieldIsEmpty() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "missing", "isEmpty", null)
        ));

        Map<String, Object> input = new HashMap<>();

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("condition_0", output.get("__selectedBranch__"));
    }

    @Test
    void testNoConditionsDefaultBranch() throws Exception {
        WorkflowNode node = createConditionNode(List.of());

        Map<String, Object> input = new HashMap<>();
        input.put("data", "test");

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("default", output.get("__selectedBranch__"));
    }

    @Test
    void testNullNodeDataDefaultBranch() throws Exception {
        WorkflowNode node = new WorkflowNode();
        node.setId("cond-1");
        node.setType("condition");
        node.setData(null);

        Map<String, Object> input = new HashMap<>();
        input.put("data", "test");

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("default", output.get("__selectedBranch__"));
    }

    @Test
    void testNumericEqualityWithIntegerAndString() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "count", "eq", "5")
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("count", 5);

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("condition_0", output.get("__selectedBranch__"));
    }

    @Test
    void testOutputContainsConditionNodeId() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "status", "eq", "ok")
        ));
        node.setId("my-condition-node");

        Map<String, Object> input = new HashMap<>();
        input.put("status", "ok");

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("my-condition-node", output.get("__conditionNodeId__"));
    }

    @Test
    void testInputPassThrough() throws Exception {
        WorkflowNode node = createConditionNode(List.of(
                createCondition("condition_0", "status", "eq", "ok")
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("status", "ok");
        input.put("data", "preserved");
        input.put("count", 42);

        Map<String, Object> output = executor.execute(node, input);

        assertEquals("ok", output.get("status"));
        assertEquals("preserved", output.get("data"));
        assertEquals(42, output.get("count"));
    }

    // --- Helper methods ---

    private WorkflowNode createConditionNode(List<Map<String, Object>> conditions) {
        WorkflowNode node = new WorkflowNode();
        node.setId("cond-1");
        node.setType("condition");

        Map<String, Object> data = new HashMap<>();
        data.put("conditions", conditions);
        node.setData(data);

        return node;
    }

    private Map<String, Object> createCondition(String id, String field, String operator, Object value) {
        Map<String, Object> condition = new HashMap<>();
        condition.put("id", id);
        condition.put("field", field);
        condition.put("operator", operator);
        if (value != null) {
            condition.put("value", value);
        }
        return condition;
    }
}
