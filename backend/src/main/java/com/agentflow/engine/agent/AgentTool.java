package com.agentflow.engine.agent;

import java.util.Map;

/**
 * Runtime tool that can be called by a ReAct agent node.
 */
public interface AgentTool {

    String getName();

    String getDescription();

    String getInputSchema();

    Map<String, Object> execute(Map<String, Object> arguments, AgentToolContext context) throws Exception;
}
