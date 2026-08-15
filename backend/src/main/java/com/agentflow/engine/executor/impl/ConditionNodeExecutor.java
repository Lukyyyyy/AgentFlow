package com.agentflow.engine.executor.impl;

import com.agentflow.engine.executor.NodeExecutor;
import com.agentflow.engine.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 条件分支节点执行器
 * 基于上一步输出的 JSON 字段做条件判断，走不同的下游分支。
 *
 * 节点配置 (node.data):
 * {
 *   "conditions": [
 *     { "id": "condition_0", "field": "status", "operator": "eq", "value": "success" },
 *     { "id": "condition_1", "field": "score", "operator": "gte", "value": "80" }
 *   ]
 * }
 *
 * 输出中包含 __selectedBranch__ 字段标识选中的分支 ID，
 * 对应条件节点出边的 sourceHandle。
 * 如果没有条件匹配，__selectedBranch__ 为 "default"。
 */
@Slf4j
@Component
public class ConditionNodeExecutor implements NodeExecutor {

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) throws Exception {
        Map<String, Object> nodeData = node.getData();
        if (nodeData == null) {
            nodeData = new HashMap<>();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) nodeData.get("conditions");

        String selectedBranch = "default";

        if (conditions != null && !conditions.isEmpty()) {
            for (Map<String, Object> condition : conditions) {
                String conditionId = (String) condition.get("id");
                String field = (String) condition.get("field");
                String operator = (String) condition.get("operator");
                Object expectedValue = condition.get("value");

                Object actualValue = resolveField(input, field);

                if (evaluateCondition(actualValue, operator, expectedValue)) {
                    selectedBranch = conditionId;
                    log.info("条件分支节点 [{}]: 条件 [{}] 匹配 - field={}, operator={}, expected={}, actual={}",
                            node.getId(), conditionId, field, operator, expectedValue, actualValue);
                    break;
                }
            }
        }

        if ("default".equals(selectedBranch)) {
            log.info("条件分支节点 [{}]: 无条件匹配，走默认分支", node.getId());
        }

        Map<String, Object> output = new HashMap<>(input);
        output.put("__selectedBranch__", selectedBranch);
        output.put("__conditionNodeId__", node.getId());
        return output;
    }

    @Override
    public String getSupportedNodeType() {
        return "condition";
    }

    /**
     * 从 input map 中解析字段值，支持嵌套字段（用 . 分隔）。
     * 如果顶层找不到字段，会尝试解析 "input" 键中的 JSON 字符串。
     */
    private Object resolveField(Map<String, Object> input, String field) {
        if (field == null || field.isEmpty()) {
            return null;
        }

        // 先从顶层尝试解析
        Object result = resolveFieldFromMap(input, field);
        if (result != null) {
            return result;
        }

        // 如果顶层找不到，尝试解析 "input" 键中的 JSON 字符串
        Object inputValue = input.get("input");
        if (inputValue instanceof String) {
            String inputStr = (String) inputValue;
            try {
                JSONObject parsed = JSON.parseObject(inputStr);
                if (parsed != null) {
                    result = resolveFieldFromMap(parsed, field);
                    if (result != null) {
                        return result;
                    }
                }
            } catch (Exception e) {
                // 不是有效 JSON，忽略
            }
        }

        return null;
    }

    private Object resolveFieldFromMap(Map<String, Object> map, String field) {
        String[] parts = field.split("\\.");
        Object current = map;

        for (String part : parts) {
            if (current instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> currentMap = (Map<String, Object>) current;
                current = currentMap.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * 条件评估
     */
    private boolean evaluateCondition(Object actualValue, String operator, Object expectedValue) {
        if (operator == null) {
            return false;
        }

        switch (operator) {
            case "eq":
                return isEqual(actualValue, expectedValue);
            case "neq":
                return !isEqual(actualValue, expectedValue);
            case "gt":
                return compareNumbers(actualValue, expectedValue) > 0;
            case "gte":
                return compareNumbers(actualValue, expectedValue) >= 0;
            case "lt":
                return compareNumbers(actualValue, expectedValue) < 0;
            case "lte":
                return compareNumbers(actualValue, expectedValue) <= 0;
            case "contains":
                return stringContains(actualValue, expectedValue);
            case "notContains":
                return !stringContains(actualValue, expectedValue);
            case "startsWith":
                return stringStartsWith(actualValue, expectedValue);
            case "endsWith":
                return stringEndsWith(actualValue, expectedValue);
            case "isEmpty":
                return isEmptyValue(actualValue);
            case "isNotEmpty":
                return !isEmptyValue(actualValue);
            default:
                log.warn("不支持的条件操作符: {}", operator);
                return false;
        }
    }

    private boolean isEqual(Object actual, Object expected) {
        if (actual == null && expected == null) {
            return true;
        }
        if (actual == null || expected == null) {
            return false;
        }
        // 尝试数值比较
        if (isNumeric(actual) && isNumeric(expected)) {
            return compareNumbers(actual, expected) == 0;
        }
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    private int compareNumbers(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == null ? -1 : 1;
        }
        try {
            double actualNum = toDouble(actual);
            double expectedNum = toDouble(expected);
            return Double.compare(actualNum, expectedNum);
        } catch (NumberFormatException e) {
            // 回退到字符串比较
            return String.valueOf(actual).compareTo(String.valueOf(expected));
        }
    }

    private boolean stringContains(Object actual, Object expected) {
        if (actual == null) return false;
        return String.valueOf(actual).contains(String.valueOf(expected));
    }

    private boolean stringStartsWith(Object actual, Object expected) {
        if (actual == null) return false;
        return String.valueOf(actual).startsWith(String.valueOf(expected));
    }

    private boolean stringEndsWith(Object actual, Object expected) {
        if (actual == null) return false;
        return String.valueOf(actual).endsWith(String.valueOf(expected));
    }

    private boolean isEmptyValue(Object value) {
        if (value == null) return true;
        if (value instanceof String) return ((String) value).isEmpty();
        if (value instanceof Map) return ((Map<?, ?>) value).isEmpty();
        if (value instanceof List) return ((List<?>) value).isEmpty();
        return false;
    }

    private boolean isNumeric(Object value) {
        if (value instanceof Number) return true;
        try {
            Double.parseDouble(String.valueOf(value));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
