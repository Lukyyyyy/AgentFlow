package com.agentflow.engine.executor.impl;

import com.agentflow.engine.model.WorkflowNode;
import com.agentflow.entity.McpToolConfig;
import com.agentflow.service.McpToolConfigService;
import com.agentflow.service.SearchInfinityMcpClient;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class WebSearchNodeExecutor extends AbstractAgentPlanNodeExecutor {

    private final SearchInfinityMcpClient searchInfinityMcpClient;
    private final McpToolConfigService mcpToolConfigService;

    public WebSearchNodeExecutor(SearchInfinityMcpClient searchInfinityMcpClient,
                                 McpToolConfigService mcpToolConfigService) {
        this.searchInfinityMcpClient = searchInfinityMcpClient;
        this.mcpToolConfigService = mcpToolConfigService;
    }

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) throws Exception {
        String query = textValue(node, input, "query", "input");
        if (query == null) {
            throw new IllegalArgumentException("联网搜索节点缺少 query");
        }

        int limit = intData(node, "limit", 5);
        String freshness = stringData(node, "freshness", null);
        String language = stringData(node, "language", "zh");
        McpToolConfig selectedMcp = mcpToolConfigService.resolveFirstEnabledWebSearch(node.getData().get("mcpToolIds"));
        if (selectedMcp == null) {
            throw new IllegalArgumentException("联网搜索节点未配置，请先在 MCP 工具中添加 Agent Plan 联网搜索，并在当前节点选择它");
        }
        Map<String, Object> searchResult = searchInfinityMcpClient.webSearch(
                selectedMcp,
                query,
                limit,
                normalizeTimeRange(freshness),
                null,
                normalizeSearchType(language)
        );

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("query", query);
        output.put("summary", searchResult.get("summary"));
        output.put("results", searchResult.get("results"));
        output.put("citations", searchResult.get("citations"));
        output.put("output", searchResult.get("summary"));
        output.put("metadata", searchResult.get("raw"));
        return output;
    }

    private String normalizeSearchType(String language) {
        return "image".equalsIgnoreCase(language) ? "image" : "web";
    }

    private String normalizeTimeRange(String freshness) {
        if (!StringUtils.hasText(freshness)) {
            return null;
        }
        return switch (freshness) {
            case "day", "OneDay" -> "OneDay";
            case "week", "OneWeek" -> "OneWeek";
            case "month", "OneMonth" -> "OneMonth";
            case "year", "OneYear" -> "OneYear";
            default -> freshness;
        };
    }

    @Override
    public String getSupportedNodeType() {
        return "web_search";
    }
}
