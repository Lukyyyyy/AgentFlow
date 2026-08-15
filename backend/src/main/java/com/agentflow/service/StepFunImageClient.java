package com.agentflow.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StepFunImageClient {

    private static final int MAX_PROMPT_LENGTH = 512;
    private static final int MAX_NEGATIVE_PROMPT_LENGTH = 512;
    private static final String STEP_IMAGE_EDIT_2 = "step-image-edit-2";
    private static final String STEP_2X_LARGE = "step-2x-large";

    public Map<String, Object> generateImage(ResolvedAgentPlanConfig config, String prompt,
                                             String referenceImageUrl, String size, int count,
                                             String negativePrompt, String style,
                                             int steps, double cfgScale, int seed, boolean textMode) throws IOException {
        JSONObject request = new JSONObject();
        request.put("model", config.model());
        request.put("prompt", normalizePrompt(prompt, style));
        request.put("response_format", "url");
        request.put("n", Math.max(1, Math.min(count, 1)));
        request.put("size", normalizeSize(config.model(), size));
        request.put("steps", normalizeSteps(config.model(), steps));
        request.put("cfg_scale", normalizeCfgScale(config.model(), cfgScale));
        if (seed > 0) {
            request.put("seed", seed);
        }
        if (isStepImageEdit2(config.model())) {
            request.put("text_mode", textMode);
            if (StringUtils.hasText(negativePrompt)) {
                request.put("negative_prompt", truncate(negativePrompt.trim(), MAX_NEGATIVE_PROMPT_LENGTH));
            }
        }

        JSONObject response = postJson(config, request);
        List<String> urls = new ArrayList<>();
        JSONArray data = response.getJSONArray("data");
        if (data != null) {
            for (Object item : data) {
                if (item instanceof JSONObject object) {
                    String url = object.getString("url");
                    if (StringUtils.hasText(url)) {
                        urls.add(url);
                    }
                }
            }
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("imageUrls", urls);
        output.put("metadata", response);
        return output;
    }

    private JSONObject postJson(ResolvedAgentPlanConfig config, JSONObject body) throws IOException {
        HttpURLConnection conn = openConnection(config);
        byte[] bytes = body.toJSONString().getBytes(StandardCharsets.UTF_8);
        conn.setDoOutput(true);
        conn.getOutputStream().write(bytes);
        return readResponse(conn);
    }

    private HttpURLConnection openConnection(ResolvedAgentPlanConfig config) throws IOException {
        URL url = URI.create(normalizeImageEndpoint(config.apiUrl(), config.model())).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(120_000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + config.apiKey());
        return conn;
    }

    private JSONObject readResponse(HttpURLConnection conn) throws IOException {
        int status = conn.getResponseCode();
        byte[] bytes = (status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream()).readAllBytes();
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (status < 200 || status >= 300) {
            throw new IOException("StepFun 图片生成调用失败, HTTP " + status + ": " + text);
        }
        return JSON.parseObject(text);
    }

    String normalizeImageEndpoint(String apiUrl, String model) {
        String base = StringUtils.hasText(apiUrl) ? apiUrl.trim() : "https://api.stepfun.com/v1";
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/images/generations")) {
            return base;
        }
        if (isStepImageEdit2(model)) {
            String origin = extractOrigin(base);
            return origin + "/step_plan/v1/images/generations";
        }
        if (!base.contains("/v1") && !base.contains("/step_plan/v1")) {
            base = base + "/v1";
        }
        return base + "/images/generations";
    }

    String normalizeSize(String model, String size) {
        String value = StringUtils.hasText(size) ? size.trim() : "";
        if (!StringUtils.hasText(value) || "2K".equalsIgnoreCase(value) || "1K".equalsIgnoreCase(value)) {
            return "1024x1024";
        }
        if (isStepImageEdit2(model)) {
            return switch (value) {
                case "1280x800" -> "768x1360";
                case "800x1280" -> "1360x768";
                case "1024x1024", "768x1360", "896x1184", "1360x768", "1184x896" -> value;
                default -> "1024x1024";
            };
        }
        return value;
    }

    private String normalizePrompt(String prompt, String style) {
        String normalized = prompt.trim();
        if (StringUtils.hasText(style)) {
            normalized = normalized + "。风格：" + style.trim();
        }
        return truncate(normalized, MAX_PROMPT_LENGTH);
    }

    int normalizeSteps(String model, int steps) {
        int defaultValue = isStepImageEdit2(model) ? 8 : 50;
        int value = steps > 0 ? steps : defaultValue;
        return Math.max(1, Math.min(value, 50));
    }

    double normalizeCfgScale(String model, double cfgScale) {
        double defaultValue = isStepImageEdit2(model) ? 1.0 : STEP_2X_LARGE.equals(model) ? 6.0 : 7.5;
        double value = cfgScale > 0 ? cfgScale : defaultValue;
        return Math.max(1.0, Math.min(value, 10.0));
    }

    private boolean isStepImageEdit2(String model) {
        return STEP_IMAGE_EDIT_2.equals(model);
    }

    private String extractOrigin(String apiUrl) {
        String normalized = apiUrl;
        int protocolIndex = normalized.indexOf("://");
        if (protocolIndex < 0) {
            return "https://api.stepfun.com";
        }
        int pathIndex = normalized.indexOf('/', protocolIndex + 3);
        return pathIndex > 0 ? normalized.substring(0, pathIndex) : normalized;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
