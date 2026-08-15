package com.agentflow.controller;

import com.agentflow.common.Result;
import com.agentflow.entity.NodeDefinition;
import com.agentflow.service.NodeDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 节点定义控制器
 */
@Tag(name = "节点定义接口")
@RestController
@RequestMapping("/api/node-types")
public class NodeDefinitionController {
    
    @Autowired
    private NodeDefinitionService nodeDefinitionService;
    
    @Operation(summary = "查询所有节点类型")
    @GetMapping
    public Result<List<NodeDefinition>> listNodeTypes() {
        List<NodeDefinition> list = nodeDefinitionService.listAllNodeDefinitions();
        return Result.success(list);
    }
}
