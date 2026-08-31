package com.agentflow.controller;

import com.agentflow.common.Result;
import com.agentflow.dto.AgentPlanWebSearchMcpRequest;
import com.agentflow.dto.McpToolConfigRequest;
import com.agentflow.dto.McpToolTestRequest;
import com.agentflow.service.McpToolConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "MCP 工具管理接口")
@RestController
@RequestMapping("/api/mcp-tools")
public class McpToolConfigController {

    private final McpToolConfigService mcpToolConfigService;

    public McpToolConfigController(McpToolConfigService mcpToolConfigService) {
        this.mcpToolConfigService = mcpToolConfigService;
    }

    @Operation(summary = "查询 MCP 工具列表")
    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestAttribute Long userId) {
        return Result.success(mcpToolConfigService.listConfigs(userId));
    }

    @Operation(summary = "新增 MCP 工具")
    @PostMapping
    public Result<Map<String, Object>> create(@RequestAttribute Long userId,
                                              @Valid @RequestBody McpToolConfigRequest request) {
        return Result.success(mcpToolConfigService.createConfig(userId, request));
    }

    @Operation(summary = "新增 Agent Plan 联网搜索 MCP 工具")
    @PostMapping("/agent-plan-web-search")
    public Result<Map<String, Object>> createAgentPlanWebSearch(@RequestAttribute Long userId,
            @Valid @RequestBody AgentPlanWebSearchMcpRequest request) {
        return Result.success(mcpToolConfigService.createAgentPlanWebSearch(userId, request));
    }

    @Operation(summary = "更新 MCP 工具")
    @PutMapping("/{id}")
    public Result<Map<String, Object>> update(@RequestAttribute Long userId, @PathVariable Long id,
                                              @Valid @RequestBody McpToolConfigRequest request) {
        return Result.success(mcpToolConfigService.updateConfig(userId, id, request));
    }

    @Operation(summary = "删除 MCP 工具")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@RequestAttribute Long userId, @PathVariable Long id) {
        mcpToolConfigService.deleteConfig(userId, id);
        return Result.success();
    }

    @Operation(summary = "测试 MCP 工具")
    @PostMapping("/{id}/test")
    public Result<Map<String, Object>> test(@RequestAttribute Long userId, @PathVariable Long id,
                                            @RequestBody(required = false) McpToolTestRequest request) throws Exception {
        return Result.success(mcpToolConfigService.testConfig(userId, id, request == null ? null : request.getQuery()));
    }
}
