package com.agentflow.engine.executor.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.agentflow.dto.ExecutionEvent;
import com.agentflow.engine.agent.AgentTool;
import com.agentflow.engine.agent.AgentToolContext;
import com.agentflow.engine.agent.AgentToolRegistry;
import com.agentflow.engine.llm.LLMNodeConfig;
import com.agentflow.engine.model.WorkflowNode;
import com.agentflow.engine.skill.Skill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * First-phase ReAct runtime implemented inside a workflow node.
 */
@Slf4j
@Component
public class ReActAgentNodeExecutor extends AbstractLLMNodeExecutor {

    private static final int DEFAULT_MAX_STEPS = 5;
    private static final int MAX_ALLOWED_STEPS = 20;

    @Autowired
    private AgentToolRegistry toolRegistry;

    @Override
    protected String getNodeType() {
        return "react_agent";
    }

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) throws Exception {
        return execute(node, input, null);
    }

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input,
                                       Consumer<ExecutionEvent> progressCallback) throws Exception {
        LLMNodeConfig config = extractConfig(node);
        validateConfig(config);

        String goalPrompt = promptTemplateService.processTemplate(
                config.getPromptTemplate(),
                config.getInputParams(),
                input
        );
        int maxSteps = resolveMaxSteps(node.getData());
        List<AgentTool> availableTools = toolRegistry.getTools(resolveToolNames(node.getData()));
        if (availableTools.isEmpty()) {
            throw new IllegalArgumentException("ReAct Agent 至少需要启用一个工具");
        }

        String systemPrompt = buildSystemPrompt(config, availableTools, node.getData());
        ChatClient chatClient = chatClientFactory.createClient(
                config.getProvider(),
                config.getApiUrl(),
                config.getApiKey(),
                config.getModel(),
                config.getTemperature()
        );

        List<Map<String, Object>> trace = new ArrayList<>();
        TokenUsage tokenUsage = new TokenUsage();
        AgentToolContext toolContext = new AgentToolContext(node, input);

        for (int step = 1; step <= maxSteps; step++) {
            String userPrompt = buildStepPrompt(goalPrompt, input, trace, step, maxSteps);
            ChatResponse response = chatClient.prompt(new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            ))).call().chatResponse();

            tokenUsage.add(response);
            String content = response.getResult().getOutput().getContent();
            AgentDecision decision = parseDecision(content);

            Map<String, Object> traceItem = new LinkedHashMap<>();
            traceItem.put("step", step);
            traceItem.put("action", decision.action());
            traceItem.put("reasoningSummary", decision.reasoningSummary());

            if ("final_answer".equals(decision.action())) {
                traceItem.put("finalAnswer", decision.finalAnswer());
                trace.add(traceItem);
                emitProgress(node, progressCallback, "ReAct 完成", traceItem);
                return buildOutput(decision.finalAnswer(), trace, tokenUsage, false, null);
            }

            if (!"tool_call".equals(decision.action())) {
                traceItem.put("finalAnswer", content);
                trace.add(traceItem);
                emitProgress(node, progressCallback, "ReAct 返回非标准动作，按最终答案处理", traceItem);
                return buildOutput(content, trace, tokenUsage, false, null);
            }

            String toolName = decision.toolName();
            AgentTool tool = toolRegistry.getRequiredTool(toolName);
            Map<String, Object> toolInput = decision.toolInput();
            Map<String, Object> observation;
            try {
                observation = tool.execute(toolInput, toolContext);
            } catch (Exception toolError) {
                log.warn("ReAct 工具调用失败: node={}, tool={}, error={}", node.getId(), toolName, toolError.getMessage());
                observation = new LinkedHashMap<>();
                observation.put("error", toolError.getMessage());
                observation.put("recoverable", true);
            }

            traceItem.put("toolName", toolName);
            traceItem.put("toolInput", toolInput);
            traceItem.put("observation", observation);
            trace.add(traceItem);
            emitProgress(node, progressCallback, "ReAct 工具调用: " + toolName, traceItem);
        }

        String message = "达到最大 ReAct 步数限制: " + maxSteps;
        return buildOutput(message, trace, tokenUsage, true, message);
    }

    private String buildSystemPrompt(LLMNodeConfig config, List<AgentTool> tools, Map<String, Object> nodeData) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个运行在 AgentFlow 工作流节点里的 ReAct Agent。\n");
        sb.append("你必须通过 JSON 决策进行工具调用或给出最终答案，不要输出 Markdown 代码块。\n");
        sb.append("不要暴露完整隐藏推理链，只在 reasoningSummary 字段给一句简短行动理由。\n\n");

        if (hasText(nodeData.get("knowledgeBaseId"))) {
            sb.append("当前节点已配置知识库。回答项目资料、实现计划、配置字段或节点说明相关问题时，必须先调用 knowledge_retrieve 检索知识库，再基于 observation 回答。\n\n");
        }

        if (config.getSkillName() != null && !config.getSkillName().isBlank()) {
            Optional<Skill> skill = skillRegistry.getSkill(config.getSkillName());
            skill.ifPresent(value -> {
                sb.append("当前节点关联 Skill: ").append(value.getName()).append("\n");
                sb.append(value.getSummary()).append("\n\n");
            });
        }

        sb.append("可用工具:\n");
        for (AgentTool tool : tools) {
            sb.append("- name: ").append(tool.getName()).append("\n");
            sb.append("  description: ").append(tool.getDescription()).append("\n");
            sb.append("  inputSchema: ").append(tool.getInputSchema()).append("\n");
        }

        sb.append("""

                每次只返回下面两种 JSON 之一：
                {"action":"tool_call","reasoningSummary":"为什么需要这个工具","toolName":"工具名","toolInput":{}}
                {"action":"final_answer","reasoningSummary":"为什么可以结束","finalAnswer":"最终答案"}
                """);
        return sb.toString();
    }

    private String buildStepPrompt(String goalPrompt, Map<String, Object> input,
                                   List<Map<String, Object>> trace, int step, int maxSteps) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("goal", goalPrompt);
        payload.put("currentInput", input);
        payload.put("previousSteps", trace);
        payload.put("step", step);
        payload.put("maxSteps", maxSteps);
        return JSON.toJSONString(payload);
    }

    private AgentDecision parseDecision(String content) {
        String cleaned = stripCodeFence(content);
        try {
            JSONObject json = JSON.parseObject(cleaned);
            String action = normalizeAction(json.getString("action"));
            String reasoningSummary = defaultString(json.getString("reasoningSummary"));
            String toolName = firstString(json, "toolName", "tool_name");
            Map<String, Object> toolInput = toMap(firstValue(json, "toolInput", "tool_input", "arguments"));
            String finalAnswer = defaultString(firstString(json, "finalAnswer", "final_answer", "answer", "content"));
            return new AgentDecision(action, reasoningSummary, toolName, toolInput, finalAnswer);
        } catch (JSONException | IllegalArgumentException e) {
            log.warn("ReAct 响应不是合法 JSON，按最终答案处理: {}", e.getMessage());
            return new AgentDecision("final_answer", "模型直接返回了最终内容", null, Map.of(), content);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof JSONObject jsonObject) {
            return new LinkedHashMap<>(jsonObject);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
            return result;
        }
        return JSON.parseObject(JSON.toJSONString(value), Map.class);
    }

    private String stripCodeFence(String content) {
        if (content == null) {
            return "";
        }

        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        int firstNewline = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewline >= 0 && lastFence > firstNewline) {
            return trimmed.substring(firstNewline + 1, lastFence).trim();
        }
        return trimmed;
    }

    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return "final_answer";
        }
        String normalized = action.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "tool", "call_tool", "function_call" -> "tool_call";
            case "final", "answer" -> "final_answer";
            default -> normalized;
        };
    }

    private Object firstValue(JSONObject json, String... names) {
        for (String name : names) {
            if (json.containsKey(name)) {
                return json.get(name);
            }
        }
        return null;
    }

    private String firstString(JSONObject json, String... names) {
        Object value = firstValue(json, names);
        return value == null ? null : value.toString();
    }

    private Map<String, Object> buildOutput(String finalAnswer, List<Map<String, Object>> trace,
                                            TokenUsage tokenUsage, boolean maxStepsReached,
                                            String stopReason) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("output", finalAnswer);
        output.put("finalAnswer", finalAnswer);
        output.put("toolTrace", trace);
        output.put("steps", trace.size());
        output.put("maxStepsReached", maxStepsReached);
        if (stopReason != null) {
            output.put("stopReason", stopReason);
        }
        output.put("inputTokens", tokenUsage.inputTokens);
        output.put("outputTokens", tokenUsage.outputTokens);
        output.put("totalTokens", tokenUsage.totalTokens);
        output.put("tokens", tokenUsage.totalTokens);
        return output;
    }

    private void emitProgress(WorkflowNode node, Consumer<ExecutionEvent> progressCallback,
                              String message, Map<String, Object> data) {
        if (progressCallback != null) {
            progressCallback.accept(ExecutionEvent.nodeProgress(node.getId(), node.getType(), message, data));
        }
    }

    private int resolveMaxSteps(Map<String, Object> data) {
        Object value = data == null ? null : data.get("maxSteps");
        if (value == null) {
            return DEFAULT_MAX_STEPS;
        }

        int maxSteps = value instanceof Number number
                ? number.intValue()
                : Integer.parseInt(value.toString());
        return Math.max(1, Math.min(MAX_ALLOWED_STEPS, maxSteps));
    }

    private List<String> resolveToolNames(Map<String, Object> data) {
        Object value = data == null ? null : data.get("tools");
        List<String> toolNames;
        if (value == null) {
            toolNames = new ArrayList<>();
        } else if (value instanceof List<?> list) {
            toolNames = list.stream()
                    .map(Object::toString)
                    .filter(toolName -> !toolName.isBlank())
                    .collect(Collectors.toList());
        } else {
            toolNames = List.of(value.toString().split(",")).stream()
                    .map(String::trim)
                    .filter(toolName -> !toolName.isBlank())
                    .collect(Collectors.toList());
        }

        if (hasText(data == null ? null : data.get("knowledgeBaseId")) && !toolNames.contains("knowledge_retrieve")) {
            toolNames = new ArrayList<>(toolNames);
            toolNames.add("knowledge_retrieve");
        }
        if (toolNames.contains("web_search") && !toolNames.contains("web_fetch")) {
            List<String> expanded = new ArrayList<>(toolNames);
            expanded.add("web_fetch");
            return expanded;
        }
        return toolNames;
    }

    private boolean hasText(Object value) {
        return value != null && !String.valueOf(value).trim().isEmpty();
    }

    private void validateConfig(LLMNodeConfig config) {
        if (isBlank(config.getProvider())) {
            throw new IllegalArgumentException("ReAct Agent 缺少有效的提供商配置");
        }
        if (isBlank(config.getApiUrl()) || isBlank(config.getApiKey()) || isBlank(config.getModel())) {
            throw new IllegalArgumentException("ReAct Agent 缺少有效的模型配置，请检查全局配置或节点配置");
        }
        if (isBlank(config.getPromptTemplate())) {
            throw new IllegalArgumentException("ReAct Agent 缺少任务提示词");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private record AgentDecision(
            String action,
            String reasoningSummary,
            String toolName,
            Map<String, Object> toolInput,
            String finalAnswer
    ) {
    }

    private static class TokenUsage {
        private int inputTokens;
        private int outputTokens;
        private int totalTokens;

        void add(ChatResponse response) {
            if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
                return;
            }

            var usage = response.getMetadata().getUsage();
            if (usage.getPromptTokens() != null) {
                inputTokens += usage.getPromptTokens().intValue();
            }
            if (usage.getGenerationTokens() != null) {
                outputTokens += usage.getGenerationTokens().intValue();
            }
            if (usage.getTotalTokens() != null) {
                totalTokens += usage.getTotalTokens().intValue();
            } else {
                totalTokens = inputTokens + outputTokens;
            }
        }
    }
}
