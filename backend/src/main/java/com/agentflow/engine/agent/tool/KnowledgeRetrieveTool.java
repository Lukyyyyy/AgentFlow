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
        return "从当前节点配置的知识库检索与问题相关的资料，返回参考片段和来源。";
    }

    @Override
    public String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"],\"additionalProperties\":false}";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments, AgentToolContext context) {
        String query = arguments.get("query") instanceof String text ? text.trim() : "";
        String knowledgeBaseId = firstText(
                context.node().getData().get("knowledgeBaseId"),
                "default"
        );
        int topK = intArg(context.node().getData().get("knowledgeTopK"), 5);
        double scoreThreshold = doubleArg(
                context.node().getData().get("knowledgeScoreThreshold"),
                0.2
        );
        return knowledgeBaseService.searchRuntime(
                context.node().getOwnerId(), knowledgeBaseId, query, topK, scoreThreshold);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private int intArg(Object value, int defaultValue) {
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

    private double doubleArg(Object value, double defaultValue) {
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
