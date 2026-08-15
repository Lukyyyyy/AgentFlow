package com.agentflow.engine.skill;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.function.FunctionCallback;

/**
 * 加载 Skill Reference 文件的 Function
 * 用于渐进式披露的第三阶段
 */
@Slf4j
public class LoadSkillReferenceFunction implements FunctionCallback {

    private static final String NAME = "load_skill_reference";
    private static final String DESCRIPTION = """
            加载指定技能的参考文档内容。
            当你需要查看技能的详细模板、示例或指南时，调用此函数。
            例如：脚本模板、语音风格指南、结构模式参考等。
            """;

    private final SkillRegistry skillRegistry;

    public LoadSkillReferenceFunction(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public String getInputTypeSchema() {
        return """
            {
              "type": "object",
              "properties": {
                "skill_name": {
                  "type": "string",
                  "description": "技能名称"
                },
                "reference_name": {
                  "type": "string",
                  "description": "参考文档名称（不含 .md 后缀）"
                }
              },
              "required": ["skill_name", "reference_name"]
            }
            """;
    }

    @Override
    public String call(String functionInput) {
        log.info("Loading skill reference: {}", functionInput);

        try {
            // 解析输入参数
            InputParams params = parseInput(functionInput);

            if (params.skillName == null || params.skillName.isBlank()) {
                return "错误：请提供技能名称";
            }

            if (params.referenceName == null || params.referenceName.isBlank()) {
                // 返回可用的 reference 列表
                return skillRegistry.getSkill(params.skillName)
                        .map(skill -> {
                            if (skill.getReferences() == null || skill.getReferences().isEmpty()) {
                                return "该技能没有可用的参考文档";
                            }
                            return "可用的参考文档：\n" +
                                    skill.getReferences().stream()
                                            .map(ref -> "- " + ref)
                                            .reduce((a, b) -> a + "\n" + b)
                                            .orElse("");
                        })
                        .orElse("错误：未找到技能 '" + params.skillName + "'");
            }

            // 加载指定的 reference 文件
            String content = skillRegistry.loadReference(params.skillName, params.referenceName);
            log.info("Successfully loaded reference: {} for skill: {}", params.referenceName, params.skillName);
            return content;

        } catch (Exception e) {
            log.error("Failed to load skill reference", e);
            return "加载参考文档失败：" + e.getMessage();
        }
    }

    /**
     * 解析输入参数
     * 支持直接传入字符串或 JSON 格式
     */
    private InputParams parseInput(String input) {
        if (input == null || input.isBlank()) {
            return new InputParams(null, null);
        }

        input = input.trim();

        // 如果是 JSON 格式
        if (input.startsWith("{")) {
            try {
                String skillName = extractJsonValue(input, "skill_name");
                String referenceName = extractJsonValue(input, "reference_name");
                return new InputParams(skillName, referenceName);
            } catch (Exception e) {
                log.debug("Failed to parse JSON input", e);
            }
        }

        // 尝试解析为 "skill_name:reference_name" 格式
        if (input.contains(":")) {
            int colonIndex = input.indexOf(':');
            return new InputParams(
                    input.substring(0, colonIndex).trim(),
                    input.substring(colonIndex + 1).trim()
            );
        }

        // 否则作为 skill_name
        return new InputParams(input, null);
    }

    /**
     * 从 JSON 字符串中提取指定字段的值
     */
    private String extractJsonValue(String json, String fieldName) {
        String searchKey = "\"" + fieldName + "\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex < 0) {
            return null;
        }

        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex < 0) {
            return null;
        }

        int startQuote = json.indexOf("\"", colonIndex + 1);
        if (startQuote < 0) {
            return null;
        }

        int endQuote = json.indexOf("\"", startQuote + 1);
        if (endQuote < 0) {
            return null;
        }

        return json.substring(startQuote + 1, endQuote);
    }

    /**
     * 输入参数
     */
    private record InputParams(String skillName, String referenceName) {}

    /**
     * 函数输入参数定义
     */
    public record Input(
            @JsonProperty(required = true)
            @JsonPropertyDescription("技能名称")
            String skill_name,

            @JsonProperty(required = true)
            @JsonPropertyDescription("参考文档名称（不含 .md 后缀）")
            String reference_name
    ) {}
}
