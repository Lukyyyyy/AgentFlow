package com.agentflow.dto;

import lombok.Data;
import java.util.Map;

/**
 * 断点续执行请求 DTO
 */
@Data
public class ResumeExecutionRequest {
    
    /**
     * 从哪个节点开始执行（可选，不指定则从失败节点开始）
     */
    private String startNodeId;
    
    /**
     * 是否使用快照的变量值
     */
    private Boolean useSnapshotVariables = true;
    
    /**
     * 需要修改的变量（可选）
     */
    private Map<String, Object> modifiedVariables;
    
    /**
     * 是否跳过已成功的节点
     */
    private Boolean skipSuccessNodes = true;
}