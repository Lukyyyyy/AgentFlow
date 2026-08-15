package com.agentflow.engine.executor.impl;

import com.agentflow.engine.llm.ChatClientFactory;
import com.agentflow.engine.model.WorkflowNode;
import com.agentflow.service.AgentPlanConfigResolver;
import com.agentflow.service.ResolvedAgentPlanConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class VisionAnalyzeNodeExecutor extends AbstractAgentPlanNodeExecutor {

    private final AgentPlanConfigResolver configResolver;
    private final ChatClientFactory chatClientFactory;

    public VisionAnalyzeNodeExecutor(AgentPlanConfigResolver configResolver, ChatClientFactory chatClientFactory) {
        this.configResolver = configResolver;
        this.chatClientFactory = chatClientFactory;
    }

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) {
        String imageUrl = textValue(node, input, "imageUrl", "imageUrl");
        String videoUrl = textValue(node, input, "videoUrl", "videoUrl");
        String criteria = stringData(node, "criteria", "判断素材是否满足输入要求，给出问题列表和 0-100 分。");
        if (imageUrl == null && videoUrl == null) {
            throw new IllegalArgumentException("视觉质检节点缺少 imageUrl 或 videoUrl");
        }

        ResolvedAgentPlanConfig config = configResolver.resolve(node, "vision");
        configResolver.validateApiConfig(config, "视觉质检");

        ChatClient chatClient = chatClientFactory.createClient(
                config.provider(),
                config.apiUrl(),
                config.apiKey(),
                config.model(),
                0.2
        );
        String prompt = """
                请对下面的视觉素材做质检。
                质检要求：%s
                图片 URL：%s
                视频 URL：%s

                请用中文返回简洁结论，包含：描述、分数、问题列表、是否通过。
                """.formatted(criteria, imageUrl == null ? "" : imageUrl, videoUrl == null ? "" : videoUrl);
        String description = chatClient.prompt(prompt).call().content();
        int score = description != null && description.contains("不通过") ? 60 : 85;
        boolean pass = score >= 80 && (description == null || !description.contains("不通过"));

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("description", description);
        output.put("score", score);
        output.put("issues", pass ? List.of() : List.of(description));
        output.put("pass", pass);
        output.put("output", description);
        return output;
    }

    @Override
    public String getSupportedNodeType() {
        return "vision_analyze";
    }
}
