package com.agentflow.engine.executor.impl;

import com.agentflow.engine.model.WorkflowNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TTSNodeExecutorTest {

    @Test
    void extractInputText_AllowsArbitraryParameterName() {
        WorkflowNode node = new WorkflowNode();
        node.setData(Map.of("inputParams", List.of(Map.of(
                "name", "tts-input",
                "type", "reference",
                "referenceNode", "llm.llm-out"
        ))));

        String text = ReflectionTestUtils.invokeMethod(
                new TTSNodeExecutor(), "extractInputText", node, Map.of("llm-out", "待合成文本"));

        assertEquals("待合成文本", text);
    }
}
