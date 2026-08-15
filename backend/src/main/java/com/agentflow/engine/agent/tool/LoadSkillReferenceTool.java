package com.agentflow.engine.agent.tool;

import com.agentflow.engine.agent.AgentTool;
import com.agentflow.engine.agent.AgentToolContext;
import com.agentflow.engine.skill.SkillRegistry;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads one reference document from a registered Skill.
 */
@Component
public class LoadSkillReferenceTool implements AgentTool {

    private final SkillRegistry skillRegistry;

    public LoadSkillReferenceTool(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    @Override
    public String getName() {
        return "load_skill_reference";
    }

    @Override
    public String getDescription() {
        return "加载指定 Skill 的参考文档内容。需要模板、示例或更细资料时调用。";
    }

    @Override
    public String getInputSchema() {
        return """
                {"type":"object","properties":{"skill_name":{"type":"string"},"reference_name":{"type":"string","description":"参考文档名称，不含 .md 后缀"}},"required":["skill_name","reference_name"]}
                """;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments, AgentToolContext context) throws Exception {
        String skillName = stringArg(arguments, "skill_name");
        String referenceName = stringArg(arguments, "reference_name");
        Map<String, Object> result = new LinkedHashMap<>();

        if (skillName == null || skillName.isBlank()) {
            result.put("error", "缺少 skill_name");
            return result;
        }
        if (referenceName == null || referenceName.isBlank()) {
            result.put("error", "缺少 reference_name");
            return result;
        }

        result.put("skillName", skillName);
        result.put("referenceName", referenceName);
        result.put("content", skillRegistry.loadReference(skillName, referenceName));
        return result;
    }

    private String stringArg(Map<String, Object> arguments, String name) {
        Object value = arguments == null ? null : arguments.get(name);
        return value == null ? null : value.toString();
    }
}
