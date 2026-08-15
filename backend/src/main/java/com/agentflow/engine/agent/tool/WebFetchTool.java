package com.agentflow.engine.agent.tool;

import com.agentflow.engine.agent.AgentTool;
import com.agentflow.engine.agent.AgentToolContext;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WebFetchTool implements AgentTool {

    private static final int MAX_PAGE_CHARS = 8000;

    @Override
    public String getName() {
        return "web_fetch";
    }

    @Override
    public String getDescription() {
        return "读取指定 URL 的网页内容，用于搜索结果后的原文核验和信息抽取。";
    }

    @Override
    public String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"urls\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"url\":{\"type\":\"string\"}},\"required\":[]}";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments, AgentToolContext context) throws Exception {
        List<String> urls = resolveUrls(arguments);
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("网页读取工具缺少 url 或 urls 参数");
        }

        List<Map<String, Object>> pages = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        for (String url : urls) {
            URLConnection connection = URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "AgentFlow/1.0");
            String text = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String truncated = text.length() > MAX_PAGE_CHARS ? text.substring(0, MAX_PAGE_CHARS) : text;

            Map<String, Object> page = new LinkedHashMap<>();
            page.put("url", url);
            page.put("content", truncated);
            pages.add(page);

            content.append("URL: ").append(url).append("\n").append(truncated).append("\n\n");
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("pages", pages);
        output.put("content", content.toString().trim());
        output.put("citations", pages.stream().map(page -> page.get("url")).toList());
        return output;
    }

    private List<String> resolveUrls(Map<String, Object> arguments) {
        List<String> urls = new ArrayList<>();
        Object urlsValue = arguments.get("urls");
        if (urlsValue instanceof List<?> list) {
            for (Object item : list) {
                addUrl(urls, item);
            }
        } else {
            addUrl(urls, urlsValue);
        }
        addUrl(urls, arguments.get("url"));
        return urls.stream().distinct().toList();
    }

    private void addUrl(List<String> urls, Object value) {
        if (value == null) {
            return;
        }
        for (String url : value.toString().split("[,，\\n\\s]+")) {
            String trimmed = url.trim();
            if (!trimmed.isBlank()) {
                urls.add(trimmed);
            }
        }
    }
}
