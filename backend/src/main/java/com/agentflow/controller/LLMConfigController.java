package com.agentflow.controller;

import com.agentflow.common.Result;
import com.agentflow.common.ResourceNotFoundException;
import com.agentflow.entity.LLMGlobalConfig;
import com.agentflow.service.LLMGlobalConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 全局模型配置管理控制器。
 */
@Tag(name = "全局模型配置接口")
@RestController
@RequestMapping("/api/llm-config")
public class LLMConfigController {

    @Autowired
    private LLMGlobalConfigService llmGlobalConfigService;

    @Operation(summary = "获取指定提供商的配置列表")
    @GetMapping("/{provider}")
    public Result<List<LLMGlobalConfig>> listByProvider(@RequestAttribute Long userId, @PathVariable String provider) {
        List<LLMGlobalConfig> configs = llmGlobalConfigService.listByProvider(userId, provider);
        return Result.success(configs);
    }

    @Operation(summary = "获取所有配置列表")
    @GetMapping
    public Result<List<LLMGlobalConfig>> listAll(@RequestAttribute Long userId) {
        List<LLMGlobalConfig> configs = llmGlobalConfigService.listAll(userId);
        return Result.success(configs);
    }

    @Operation(summary = "获取配置详情")
    @GetMapping("/detail/{id}")
    public Result<LLMGlobalConfig> getById(@RequestAttribute Long userId, @PathVariable Long id) {
        LLMGlobalConfig config = llmGlobalConfigService.requireConfig(userId, id);
        return Result.success(config);
    }

    @Operation(summary = "获取指定提供商的默认配置")
    @GetMapping("/default/{provider}")
    public Result<LLMGlobalConfig> getDefaultConfig(@RequestAttribute Long userId, @PathVariable String provider) {
        LLMGlobalConfig config = llmGlobalConfigService.getDefaultConfig(userId, provider);
        return Result.success(config);
    }

    @Operation(summary = "保存配置（新增或更新）")
    @PostMapping
    public Result<LLMGlobalConfig> saveConfig(@RequestAttribute Long userId, @RequestBody LLMGlobalConfig config) {
        try {
            LLMGlobalConfig saved = llmGlobalConfigService.saveConfig(userId, config);
            return Result.success(saved);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return Result.error("保存配置失败: " + e.getMessage());
        } catch (DuplicateKeyException e) {
            return Result.error("保存配置失败: 同一供应商下的配置别名不能重复");
        } catch (Exception e) {
            return Result.error("保存配置失败，请稍后重试");
        }
    }

    @Operation(summary = "删除配置")
    @DeleteMapping("/{id}")
    public Result<Void> deleteConfig(@RequestAttribute Long userId, @PathVariable Long id) {
        llmGlobalConfigService.deleteConfig(userId, id);
        return Result.success();
    }

    @Operation(summary = "设置默认配置")
    @PostMapping("/{id}/default")
    public Result<Void> setDefaultConfig(@RequestAttribute Long userId, @PathVariable Long id) {
        try {
            llmGlobalConfigService.setDefaultConfig(userId, id);
            return Result.success();
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return Result.error("设置默认配置失败: " + e.getMessage());
        }
    }
}
