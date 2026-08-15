package com.agentflow.engine.agent.tool;

import com.agentflow.engine.agent.AgentTool;
import com.agentflow.engine.agent.AgentToolContext;
import com.agentflow.service.KnowledgeBaseService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class KnowledgeRetrieveTool implements AgentTool {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeRetrieveTool(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public String getName() {
        return "knowledge_retrieve";
    }

    @Override
    public String getDescription() {
        return "从当前节点配置的知识库中检索与问题相关的项目资料、实现计划、字段和节点说明。";
    }

    @Override
    public String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments, AgentToolContext context) {
        String query = String.valueOf(arguments.getOrDefault("query", ""));
        String knowledgeBaseId = firstText(
                arguments.get("knowledgeBaseId"),
                context.node().getData().get("knowledgeBaseId"),
                "default"
        );
        int topK = intArg(context.node().getData().get("knowledgeTopK"), arguments.get("topK"), 5);
        double scoreThreshold = doubleArg(
                context.node().getData().get("knowledgeScoreThreshold"),
                arguments.get("scoreThreshold"),
                0.2
        );
        return knowledgeBaseService.searchRuntime(knowledgeBaseId, query, topK, scoreThreshold);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private int intArg(Object primary, Object fallback, int defaultValue) {
        Object value = primary != null ? primary : fallback;
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null && !String.valueOf(value).trim().isEmpty()) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private double doubleArg(Object primary, Object fallback, double defaultValue) {
        Object value = primary != null ? primary : fallback;
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null && !String.valueOf(value).trim().isEmpty()) {
            try {
                return Double.parseDouble(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
}
