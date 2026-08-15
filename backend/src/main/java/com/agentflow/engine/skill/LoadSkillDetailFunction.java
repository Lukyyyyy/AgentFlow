package com.agentflow.engine.skill;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.function.FunctionCallback;

/**
 * 加载 Skill 详细内容的 Function
 * 用于渐进式披露的第二阶段
 */
@Slf4j
public class LoadSkillDetailFunction implements FunctionCallback {

    private static final String NAME = "load_skill_detail";
    private static final String DESCRIPTION = """
            加载指定技能的完整指南内容。
            当你认为当前任务需要使用某个技能的详细指导时，调用此函数获取完整的 SKILL.md 内容。
            返回内容包括：技能的详细说明、操作步骤、最佳实践和可用的参考文档列表。
            """;

    private final SkillRegistry skillRegistry;

    public LoadSkillDetailFunction(SkillRegistry skillRegistry) {
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
                  "description": "要加载的技能名称"
                }
              },
              "required": ["skill_name"]
            }
            """;
    }

    @Override
    public String call(String functionInput) {
        log.info("Loading skill detail: {}", functionInput);

        try {
            // 解析输入参数
            String skillName = parseSkillName(functionInput);

            if (skillName == null || skillName.isBlank()) {
                return "错误：请提供技能名称";
            }

            return skillRegistry.getSkill(skillName)
                    .map(skill -> {
                        log.info("Successfully loaded skill detail: {}", skillName);
                        return skill.getFullContent();
                    })
                    .orElse("错误：未找到技能 '" + skillName + "'。可用的技能列表可以通过 GET /api/skills 获取。");

        } catch (Exception e) {
            log.error("Failed to load skill detail", e);
            return "加载技能详情失败：" + e.getMessage();
        }
    }

    /**
     * 解析技能名称
     * 支持直接传入字符串或 JSON 格式
     */
    private String parseSkillName(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        input = input.trim();

        // 如果是 JSON 格式，提取 skill_name 字段
        if (input.startsWith("{")) {
            try {
                // 简单解析 JSON 中的 skill_name 字段
                int nameIndex = input.indexOf("\"skill_name\"");
                if (nameIndex >= 0) {
                    int colonIndex = input.indexOf(":", nameIndex);
                    int startQuote = input.indexOf("\"", colonIndex + 1);
                    int endQuote = input.indexOf("\"", startQuote + 1);
                    if (startQuote >= 0 && endQuote > startQuote) {
                        return input.substring(startQuote + 1, endQuote);
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to parse JSON input, using as raw string", e);
            }
        }

        // 否则直接作为技能名称
        return input;
    }

    /**
     * 函数输入参数定义
     */
    public record Input(
            @JsonProperty(required = true)
            @JsonPropertyDescription("要加载的技能名称")
            String skill_name
    ) {}
}
