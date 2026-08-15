package com.agentflow.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 执行变量响应 DTO
 */
@Data
public class ExecutionVariableResponse {
    
    /**
     * 变量 ID
     */
    private Long id;
    
    /**
     * 执行记录 ID
     */
    private Long executionId;
    
    /**
     * 变量名
     */
    private String variableName;
    
    /**
     * 变量类型
     */
    private String variableType;
    
    /**
     * 变量值
     */
    private String variableValue;
    
    /**
     * 是否被修改
     */
    private Boolean isModified;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}