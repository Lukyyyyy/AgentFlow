package com.agentflow.engine.executor.impl;

import com.agentflow.engine.model.WorkflowNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WebFetchNodeExecutor extends AbstractAgentPlanNodeExecutor {

    @Override
    public Map<String, Object> execute(WorkflowNode node, Map<String, Object> input) throws Exception {
        String urlsText = textValue(node, input, "urls", "url");
        if (urlsText == null) {
            throw new IllegalArgumentException("网页读取节点缺少 urls");
        }

        List<Map<String, Object>> pages = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        for (String urlText : urlsText.split("[,，\\n\\s]+")) {
            if (urlText.isBlank()) {
                continue;
            }
            URL url = URI.create(urlText.trim()).toURL();
            String text = new String(url.openStream().readAllBytes(), StandardCharsets.UTF_8);
            String truncated = text.length() > 8000 ? text.substring(0, 8000) : text;
            Map<String, Object> page = new LinkedHashMap<>();
            page.put("url", urlText.trim());
            page.put("content", truncated);
            pages.add(page);
            content.append("URL: ").append(urlText.trim()).append("\n").append(truncated).append("\n\n");
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("pages", pages);
        output.put("content", content.toString().trim());
        output.put("citations", pages.stream().map(page -> page.get("url")).toList());
        output.put("output", content.toString().trim());
        return output;
    }

    @Override
    public String getSupportedNodeType() {
        return "web_fetch";
    }
}
