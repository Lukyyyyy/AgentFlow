package com.agentflow.engine.executor.impl;

import com.agentflow.engine.model.WorkflowNode;
import com.agentflow.service.AgentPlanConfigResolver;
import com.agentflow.service.MinioService;
import com.agentflow.service.ResolvedAgentPlanConfig;
import com.agentflow.service.StepFunImageClient;
import com.agentflow.service.VolcengineAgentPlanClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageGenerateNodeExecutorTest {

    @Mock
    private AgentPlanConfigResolver configResolver;

    @Mock
    private VolcengineAgentPlanClient agentPlanClient;

    @Mock
    private StepFunImageClient stepFunImageClient;

    @Mock
    private MinioService minioService;

    private ImageGenerateNodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ImageGenerateNodeExecutor(configResolver, agentPlanClient, stepFunImageClient, minioService);
    }

    @Test
    void execute_StepProvider_UsesStepFunImageClient() throws Exception {
        WorkflowNode node = new WorkflowNode();
        Map<String, Object> data = new HashMap<>();
        data.put("size", "1024x1024");
        node.setData(data);

        ResolvedAgentPlanConfig config = mock(ResolvedAgentPlanConfig.class);
        when(config.provider()).thenReturn("step");
        when(config.model()).thenReturn("step-1x-medium");
        when(configResolver.resolveImageConfig(node)).thenReturn(config);
        doNothing().when(configResolver).validateApiConfig(config, "图片生成");
        when(stepFunImageClient.generateImage(config, "画一只猫", null, "1024x1024", 1, null, null, 0, 0, 0, true))
                .thenReturn(Map.of("imageUrls", List.of("https://step.example/image.png")));
        when(minioService.uploadFromUrl(eq("https://step.example/image.png"), anyString(), eq("image/png")))
                .thenReturn("https://minio.example/image.png");

        Map<String, Object> result = executor.execute(node, Map.of("prompt", "画一只猫"));

        assertEquals("step-1x-medium", result.get("model"));
        assertEquals("https://minio.example/image.png", result.get("imageUrl"));
        verify(stepFunImageClient).generateImage(config, "画一只猫", null, "1024x1024", 1, null, null, 0, 0, 0, true);
        verify(agentPlanClient, never()).generateImage(eq(config), anyString(), eq(null), anyString(), eq(1), eq(null), eq(null));
    }
}
