package com.agentflow.engine.agent.tool;

import com.agentflow.engine.agent.AgentTool;
import com.agentflow.engine.agent.AgentToolContext;
import com.agentflow.engine.skill.SkillRegistry;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads the main content of a registered Skill.
 */
@Component
public class LoadSkillDetailTool implements AgentTool {

    private final SkillRegistry skillRegistry;

    public LoadSkillDetailTool(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    @Override
    public String getName() {
        return "load_skill_detail";
    }

    @Override
    public String getDescription() {
        return "加载指定 Skill 的完整指南内容。需要深入使用某个技能时调用。";
    }

    @Override
    public String getInputSchema() {
        return """
                {"type":"object","properties":{"skill_name":{"type":"string","description":"要加载的技能名称"}},"required":["skill_name"]}
                """;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments, AgentToolContext context) {
        String skillName = stringArg(arguments, "skill_name");
        Map<String, Object> result = new LinkedHashMap<>();

        if (skillName == null || skillName.isBlank()) {
            result.put("error", "缺少 skill_name");
            return result;
        }

        return skillRegistry.getSkill(skillName)
                .map(skill -> {
                    result.put("skillName", skillName);
                    result.put("content", skill.getFullContent());
                    return result;
                })
                .orElseGet(() -> {
                    result.put("error", "未找到 Skill: " + skillName);
                    return result;
                });
    }

    private String stringArg(Map<String, Object> arguments, String name) {
        Object value = arguments == null ? null : arguments.get(name);
        return value == null ? null : value.toString();
    }
}
