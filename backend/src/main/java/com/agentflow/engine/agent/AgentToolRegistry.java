package com.agentflow.engine.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for tools that are safe to expose to ReAct agent nodes.
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public AgentToolRegistry(List<AgentTool> toolList) {
        for (AgentTool tool : toolList) {
            tools.put(tool.getName(), tool);
        }
    }

    public List<AgentTool> getAllTools() {
        return List.copyOf(tools.values());
    }

    public List<AgentTool> getTools(List<String> selectedToolNames) {
        if (selectedToolNames == null || selectedToolNames.isEmpty()) {
            return getAllTools();
        }

        List<AgentTool> selectedTools = new ArrayList<>();
        for (String toolName : selectedToolNames) {
            AgentTool tool = tools.get(toolName);
            if (tool != null) {
                selectedTools.add(tool);
            }
        }
        return selectedTools;
    }

    public AgentTool getRequiredTool(String toolName) {
        AgentTool tool = tools.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("不支持的 Agent 工具: " + toolName);
        }
        return tool;
    }

    public Map<String, AgentTool> asMap() {
        return Collections.unmodifiableMap(tools);
    }
}
