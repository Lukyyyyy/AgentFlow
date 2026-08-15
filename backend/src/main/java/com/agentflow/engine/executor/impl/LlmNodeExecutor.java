package com.agentflow.engine.executor.impl;

import com.agentflow.dto.ExecutionEvent;
import com.agentflow.engine.model.WorkflowNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 通用 LLM 节点执行器
 */
@Component
public class LlmNodeExecutor extends AbstractLLMNodeExecutor {

    private final ReActAgentNodeExecutor reActAgentNodeExecutor;

    public LlmNodeExecutor(ReActAgentNodeExecutor reActAgentNodeExecutor) {
        this.reActAgentNodeExecutor = reActAgentNodeExecutor;
    }

    @Override
    protected String getNodeType() {
        return "llm";
    }

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) throws Exception {
        return execute(node, input, null);
    }

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input,
                                       Consumer<ExecutionEvent> progressCallback) throws Exception {
        if (isReActEnabled(node.getData())) {
            return reActAgentNodeExecutor.execute(node, input, progressCallback);
        }
        return super.execute(node, input, progressCallback);
    }

    private boolean isReActEnabled(Map<String, Object> data) {
        if (data == null) {
            return false;
        }
        Object strategy = data.get("agentStrategy");
        if (strategy != null && "react".equals(strategy.toString().trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        Object tools = data.get("tools");
        if (tools instanceof List<?> list) {
            return !list.isEmpty();
        }
        return tools instanceof String text && !text.isBlank();
    }
}
