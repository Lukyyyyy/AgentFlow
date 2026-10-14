package com.agentflow.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.agentflow.entity.LLMGlobalConfig;
import com.agentflow.common.ResourceNotFoundException;
import com.agentflow.mapper.LLMGlobalConfigMapper;
import com.agentflow.engine.llm.ChatClientFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.List;
import java.util.Map;

/**
 * 全局模型配置服务，LLM 与 TTS 节点共用。
 */
@Service
public class LLMGlobalConfigService extends ServiceImpl<LLMGlobalConfigMapper, LLMGlobalConfig> {

    @Autowired
    private ChatClientFactory chatClientFactory;

    public String testConnection(LLMGlobalConfig config) {
        normalizeConfig(config);
        if (config.getApiUrl() == null || config.getApiKey() == null) {
            throw new IllegalArgumentException("请填写 API 地址和密钥");
        }
        URI uri;
        try {
            uri = URI.create(config.getApiUrl());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("请输入有效的 HTTP 或 HTTPS API 地址");
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("请输入有效的 HTTP 或 HTTPS API 地址");
        }

        String baseUrl = "volcengine_agent_plan".equals(config.getProvider())
                ? chatClientFactory.normalizeAgentPlanBaseUrl(config.getApiUrl()) + "/api/plan/v3"
                : chatClientFactory.normalizeBaseUrl(config.getApiUrl()) + "/v1";
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        RestClient client = RestClient.builder().requestFactory(requestFactory)
                .baseUrl(baseUrl).defaultHeaders(headers -> headers.setBearerAuth(config.getApiKey())).build();
        try {
            String response;
            String tested;
            if (config.getModel() != null) {
                response = client.post().uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("model", chatClientFactory.normalizeModel(config.getProvider(), config.getModel()), "messages", List.of(
                                Map.of("role", "user", "content", "Reply OK.")),
                                "max_tokens", 16, "stream", false))
                        .retrieve().body(String.class);
                JSONObject json = JSON.parseObject(response);
                if (json == null || json.getJSONArray("choices") == null || json.getJSONArray("choices").isEmpty()) {
                    throw new IllegalArgumentException("接口未返回有效的模型响应，请检查 API 地址和模型名称");
                }
                tested = "LLM 模型";
            } else if (config.getEmbeddingModel() != null) {
                response = client.post().uri("/embeddings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("model", config.getEmbeddingModel(), "input", "connection test"))
                        .retrieve().body(String.class);
                JSONObject json = JSON.parseObject(response);
                if (json == null || json.getJSONArray("data") == null || json.getJSONArray("data").isEmpty()
                        || json.getJSONArray("data").getJSONObject(0).getJSONArray("embedding") == null
                        || json.getJSONArray("data").getJSONObject(0).getJSONArray("embedding").isEmpty()) {
                    throw new IllegalArgumentException("接口未返回有效的向量响应，请检查 API 地址和模型名称");
                }
                tested = "向量模型";
            } else if (config.getTtsModel() != null) {
                if ("qwen".equals(config.getProvider())) {
                    response = client.post().uri(URI.create(normalizeDashScopeBaseApiUrl(config.getApiUrl())
                                    + "/services/aigc/multimodal-generation/generation"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("model", config.getTtsModel(), "input", Map.of(
                                    "text", "连接测试。", "voice", "Cherry", "language_type", "Auto")))
                            .retrieve().body(String.class);
                    JSONObject json = JSON.parseObject(response);
                    JSONObject output = json == null ? null : json.getJSONObject("output");
                    JSONObject audio = output == null ? null : output.getJSONObject("audio");
                    if (audio == null || !StringUtils.hasText(audio.getString("url"))) {
                        throw new IllegalArgumentException("TTS 接口未返回有效的音频地址，请检查模型名称和配置");
                    }
                } else if ("step".equals(config.getProvider())) {
                    byte[] audio = client.post().uri(URI.create(normalizeStepSpeechEndpoint(config.getApiUrl())))
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(Map.of("model", config.getTtsModel(), "input", "连接测试。",
                                    "voice", "cixingnansheng", "response_format", "wav",
                                    "speed", 1.0, "volume", 1.0, "sample_rate", 24000))
                            .retrieve().body(byte[].class);
                    if (audio == null || audio.length <= 44
                            || !"RIFF".equals(new String(audio, 0, 4, StandardCharsets.US_ASCII))
                            || !"WAVE".equals(new String(audio, 8, 4, StandardCharsets.US_ASCII))) {
                        throw new IllegalArgumentException("TTS 接口未返回有效的音频，请检查模型名称和配置");
                    }
                } else {
                    throw new IllegalArgumentException("TTS 暂不支持的供应商: " + config.getProvider());
                }
                tested = "TTS 模型";
            } else {
                // shortcut: 仅有图片或视频能力时验证模型列表接口，逐能力测试需接入各供应商协议。
                response = client.get().uri("/models").retrieve().body(String.class);
                JSONObject json = JSON.parseObject(response);
                if (json == null || json.getJSONArray("data") == null) {
                    throw new IllegalArgumentException("接口未返回有效的模型列表，请检查 API 地址");
                }
                return "连接成功，API 密钥验证通过（未验证图片或视频生成能力）";
            }
            return "连接成功，" + tested + "响应正常（其他能力未测试）";
        } catch (RestClientResponseException e) {
            String reason = switch (e.getStatusCode().value()) {
                case 401, 403 -> "密钥无效或没有访问权限";
                case 404 -> "接口或模型不存在，请检查 API 地址和模型名称";
                case 429 -> "请求受限或额度不足";
                default -> "服务返回 HTTP " + e.getStatusCode().value() + "，请检查配置或稍后重试";
            };
            throw new IllegalArgumentException(reason);
        } catch (ResourceAccessException e) {
            throw new IllegalArgumentException("无法连接或请求超时，请检查 API 地址和网络");
        } catch (JSONException e) {
            throw new IllegalArgumentException("接口返回格式无效，请检查 API 地址");
        }
    }

    public static String normalizeDashScopeBaseApiUrl(String apiUrl) {
        if (!StringUtils.hasText(apiUrl)) {
            return null;
        }
        String normalized = apiUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        normalized = normalized
                .replace("/services/aigc/multimodal-generation/generation", "")
                .replace("/compatible-mode/v1", "/api/v1")
                .replace("/compatible-mode", "/api/v1");
        if (!normalized.endsWith("/api/v1")) {
            normalized = normalized + "/api/v1";
        }
        return normalized;
    }

    public static String normalizeStepSpeechEndpoint(String apiUrl) {
        String normalized = StringUtils.hasText(apiUrl) ? apiUrl.trim() : "https://api.stepfun.com/v1";
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/audio/speech")) {
            return normalized;
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/audio/speech";
        }
        if (normalized.endsWith("/step_plan/v1")) {
            return normalized + "/audio/speech";
        }
        return normalized + "/v1/audio/speech";
    }

    /**
     * 获取某提供商的所有配置
     */
    public List<LLMGlobalConfig> listByProvider(Long ownerId, String provider) {
        LambdaQueryWrapper<LLMGlobalConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LLMGlobalConfig::getOwnerId, ownerId)
               .eq(LLMGlobalConfig::getProvider, provider)
               .orderByDesc(LLMGlobalConfig::getIsDefault)
               .orderByDesc(LLMGlobalConfig::getUpdatedAt);
        return this.list(wrapper);
    }

    /**
     * 获取某提供商的默认配置
     */
    public List<LLMGlobalConfig> listAll(Long ownerId) {
        return this.list(new LambdaQueryWrapper<LLMGlobalConfig>()
                .eq(LLMGlobalConfig::getOwnerId, ownerId)
                .orderByDesc(LLMGlobalConfig::getUpdatedAt));
    }

    public LLMGlobalConfig getDefaultConfig(Long ownerId, String provider) {
        LambdaQueryWrapper<LLMGlobalConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LLMGlobalConfig::getOwnerId, ownerId)
               .eq(LLMGlobalConfig::getProvider, provider)
               .eq(LLMGlobalConfig::getIsDefault, 1);
        return this.getOne(wrapper);
    }

    /**
     * 设置默认配置
     * 先清除该提供商的其他默认配置，再设置指定配置为默认
     */
    @Transactional
    public void setDefaultConfig(Long ownerId, Long id) {
        LLMGlobalConfig config = requireConfig(ownerId, id);

        // 清除该提供商的其他默认配置
        LambdaUpdateWrapper<LLMGlobalConfig> clearWrapper = new LambdaUpdateWrapper<>();
        clearWrapper.eq(LLMGlobalConfig::getOwnerId, ownerId)
                    .eq(LLMGlobalConfig::getProvider, config.getProvider())
                    .set(LLMGlobalConfig::getIsDefault, 0);
        this.update(clearWrapper);

        // 设置指定配置为默认
        config.setIsDefault(1);
        this.updateById(config);
    }

    /**
     * 保存配置（新增或更新）
     * 如果是第一个配置，自动设置为默认
     */
    @Transactional
    public LLMGlobalConfig saveConfig(Long ownerId, LLMGlobalConfig config) {
        if (config.getId() != null) {
            requireConfig(ownerId, config.getId());
        }
        config.setOwnerId(ownerId);
        normalizeConfig(config);
        validateConfig(config);
        purgeDeletedDuplicate(config);
        ensureUniqueProviderAndConfigName(config);

        // 检查是否是该提供商的第一个配置
        long count = this.countByProvider(ownerId, config.getProvider());

        if (config.getId() == null) {
            // 新增配置
            if (count == 0) {
                config.setIsDefault(1);
            } else if (config.getIsDefault() == null) {
                config.setIsDefault(0);
            }
            this.save(config);
        } else {
            // 更新配置
            if (config.getIsDefault() != null && config.getIsDefault() == 1) {
                // 如果要设置为默认，先清除其他默认配置
                LambdaUpdateWrapper<LLMGlobalConfig> clearWrapper = new LambdaUpdateWrapper<>();
                clearWrapper.eq(LLMGlobalConfig::getOwnerId, ownerId)
                           .eq(LLMGlobalConfig::getProvider, config.getProvider())
                           .ne(LLMGlobalConfig::getId, config.getId())
                           .set(LLMGlobalConfig::getIsDefault, 0);
                this.update(clearWrapper);
            }
            this.updateById(config);
        }

        return config;
    }

    private void purgeDeletedDuplicate(LLMGlobalConfig config) {
        LLMGlobalConfig existing = baseMapper.findAnyByProviderAndConfigName(
                config.getOwnerId(),
                config.getProvider(),
                config.getConfigName()
        );

        if (existing == null) {
            return;
        }

        boolean sameRecord = config.getId() != null && config.getId().equals(existing.getId());
        if (!sameRecord && existing.getDeleted() != null && existing.getDeleted() == 1) {
            baseMapper.hardDeleteById(existing.getId());
        }
    }

    private void normalizeConfig(LLMGlobalConfig config) {
        config.setProvider(canonicalizeProvider(trimToNull(config.getProvider())));
        config.setConfigName(trimToNull(config.getConfigName()));
        config.setApiUrl(trimToNull(config.getApiUrl()));
        config.setApiKey(trimToNull(config.getApiKey()));
        config.setModel(trimToNull(config.getModel()));
        config.setTtsModel(trimToNull(config.getTtsModel()));
        config.setEmbeddingModel(trimToNull(config.getEmbeddingModel()));
        config.setImageModel(trimToNull(config.getImageModel()));
        config.setVideoModel(trimToNull(config.getVideoModel()));
        config.setMemoryEnabled(config.getMemoryEnabled() != null && config.getMemoryEnabled() == 1 ? 1 : 0);
    }

    private void validateConfig(LLMGlobalConfig config) {
        if (config.getProvider() == null) {
            throw new IllegalArgumentException("供应商不能为空");
        }
        if (config.getConfigName() == null) {
            throw new IllegalArgumentException("配置别名不能为空");
        }
        if (config.getApiUrl() == null) {
            throw new IllegalArgumentException("API 地址不能为空");
        }
        if (config.getApiKey() == null) {
            throw new IllegalArgumentException("API 密钥不能为空");
        }
        if (config.getModel() == null
                && config.getTtsModel() == null
                && config.getEmbeddingModel() == null
                && config.getImageModel() == null
                && config.getVideoModel() == null) {
            throw new IllegalArgumentException("请至少配置一种模型能力");
        }
    }

    private void ensureUniqueProviderAndConfigName(LLMGlobalConfig config) {
        LambdaQueryWrapper<LLMGlobalConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LLMGlobalConfig::getOwnerId, config.getOwnerId())
               .eq(LLMGlobalConfig::getProvider, config.getProvider())
               .eq(LLMGlobalConfig::getConfigName, config.getConfigName());

        if (config.getId() != null) {
            wrapper.ne(LLMGlobalConfig::getId, config.getId());
        }

        if (this.count(wrapper) > 0) {
            throw new IllegalArgumentException("同一供应商下的配置别名不能重复");
        }
    }

    /**
     * 删除配置
     * 如果删除的是默认配置，自动将下一个配置设为默认
     */
    @Transactional
    public void deleteConfig(Long ownerId, Long id) {
        LLMGlobalConfig config = requireConfig(ownerId, id);

        String provider = config.getProvider();
        boolean wasDefault = config.getIsDefault() == 1;

        baseMapper.hardDeleteById(id);

        // 如果删除的是默认配置，将下一个配置设为默认
        if (wasDefault) {
            List<LLMGlobalConfig> remaining = this.listByProvider(ownerId, provider);
            if (!remaining.isEmpty()) {
                LLMGlobalConfig newDefault = remaining.get(0);
                newDefault.setIsDefault(1);
                this.updateById(newDefault);
            }
        }
    }

    /**
     * 统计某提供商的配置数量
     */
    private long countByProvider(Long ownerId, String provider) {
        LambdaQueryWrapper<LLMGlobalConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LLMGlobalConfig::getOwnerId, ownerId)
               .eq(LLMGlobalConfig::getProvider, provider);
        return this.count(wrapper);
    }

    public LLMGlobalConfig getOwnedById(Long ownerId, Long id) {
        if (ownerId == null || id == null) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<LLMGlobalConfig>()
                .eq(LLMGlobalConfig::getOwnerId, ownerId)
                .eq(LLMGlobalConfig::getId, id));
    }

    public LLMGlobalConfig requireConfig(Long ownerId, Long id) {
        LLMGlobalConfig config = getOwnedById(ownerId, id);
        if (config == null) {
            throw new ResourceNotFoundException("配置不存在");
        }
        return config;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String canonicalizeProvider(String provider) {
        if (provider == null) {
            return null;
        }

        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "open ai" -> "openai";
            case "deep seek" -> "deepseek";
            case "通义千问" -> "qwen";
            case "stepfun", "阶跃星辰" -> "step";
            case "ai ping" -> "ai_ping";
            case "智谱" -> "zhipu";
            case "api free", "apifree.ai", "skyclaw", "skyclaw-v1", "skyclaw-v1.0",
                 "skyclaw-v1-lite", "skyclaw-v1.0-lite", "skywork-ai/skyclaw-v1",
                 "skywork-ai/skyclaw-v1-lite" -> "apifree";
            case "volcengine", "ark", "agent_plan", "agent plan", "火山方舟" -> "volcengine_agent_plan";
            default -> provider;
        };
    }
}
