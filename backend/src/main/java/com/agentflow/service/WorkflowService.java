package com.agentflow.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.agentflow.dto.WorkflowRequest;
import com.agentflow.dto.WorkflowResponse;
import com.agentflow.common.ResourceNotFoundException;
import com.agentflow.entity.Workflow;
import com.agentflow.mapper.WorkflowMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作流服务
 */
@Service
public class WorkflowService extends ServiceImpl<WorkflowMapper, Workflow> {

    @Autowired
    private LLMGlobalConfigService llmGlobalConfigService;

    @Autowired
    private McpToolConfigService mcpToolConfigService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;
    
    /**
     * 创建工作流
     */
    public WorkflowResponse createWorkflow(Long ownerId, WorkflowRequest request) {
        validateReferences(ownerId, request.getFlowData());
        Workflow workflow = new Workflow();
        workflow.setOwnerId(ownerId);
        workflow.setName(request.getName());
        workflow.setDescription(request.getDescription());
        workflow.setFlowData(request.getFlowData());
        workflow.setEngineType(request.getEngineType());
        
        this.save(workflow);
        
        return toResponse(workflow);
    }
    
    /**
     * 更新工作流
     */
    public WorkflowResponse updateWorkflow(Long ownerId, Long id, WorkflowRequest request) {
        Workflow workflow = requireWorkflow(ownerId, id);
        validateReferences(ownerId, request.getFlowData());
        
        workflow.setName(request.getName());
        workflow.setDescription(request.getDescription());
        workflow.setFlowData(request.getFlowData());
        workflow.setEngineType(request.getEngineType());
        
        this.updateById(workflow);
        
        return toResponse(workflow);
    }
    
    /**
     * 删除工作流
     */
    public void deleteWorkflow(Long ownerId, Long id) {
        Workflow workflow = requireWorkflow(ownerId, id);
        this.removeById(workflow.getId());
    }
    
    /**
     * 获取工作流详情
     */
    public WorkflowResponse getWorkflowById(Long ownerId, Long id) {
        return toResponse(requireWorkflow(ownerId, id));
    }
    
    /**
     * 查询工作流列表
     */
    public List<WorkflowResponse> listWorkflows(Long ownerId) {
        LambdaQueryWrapper<Workflow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Workflow::getOwnerId, ownerId)
                .orderByDesc(Workflow::getUpdatedAt);
        
        List<Workflow> workflows = this.list(wrapper);
        return workflows.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Workflow requireWorkflow(Long ownerId, Long id) {
        Workflow workflow = this.getOne(new LambdaQueryWrapper<Workflow>()
                .eq(Workflow::getId, id)
                .eq(Workflow::getOwnerId, ownerId));
        if (workflow == null) {
            throw new ResourceNotFoundException("工作流不存在");
        }
        return workflow;
    }

    @SuppressWarnings("unchecked")
    private void validateReferences(Long ownerId, String flowData) {
        Object root = JSON.parse(flowData);
        validateValue(ownerId, root);
    }

    private void validateValue(Long ownerId, Object value) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> {
                String name = String.valueOf(key);
                if (("configId".equals(name) || "agentPlanConfigId".equals(name)) && isNumeric(child)) {
                    llmGlobalConfigService.requireConfig(ownerId, toLong(child));
                } else if ("mcpToolIds".equals(name)) {
                    mcpToolConfigService.requireOwnedConfigs(ownerId, child);
                } else if ("knowledgeBaseId".equals(name) && isNumeric(child)) {
                    knowledgeBaseService.requireOwnedBase(ownerId, toLong(child));
                } else {
                    validateValue(ownerId, child);
                }
            });
        } else if (value instanceof Iterable<?> iterable) {
            iterable.forEach(child -> validateValue(ownerId, child));
        }
    }

    private boolean isNumeric(Object value) {
        if (value instanceof Number) {
            return true;
        }
        return value instanceof String text && text.matches("\\d+");
    }

    private Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
    
    /**
     * 转换为响应 DTO
     */
    private WorkflowResponse toResponse(Workflow workflow) {
        WorkflowResponse response = new WorkflowResponse();
        BeanUtils.copyProperties(workflow, response);
        return response;
    }
}
