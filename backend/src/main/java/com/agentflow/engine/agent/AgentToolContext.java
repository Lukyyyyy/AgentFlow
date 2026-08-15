package com.agentflow.engine.agent;

import com.agentflow.engine.model.WorkflowNode;

import java.util.Map;

/**
 * Execution context exposed to ReAct runtime tools.
 */
public record AgentToolContext(
        WorkflowNode node,
        Map<String, Object> currentInput
) {
}
