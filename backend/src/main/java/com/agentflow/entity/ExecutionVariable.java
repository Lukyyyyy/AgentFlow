package com.agentflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 执行变量实体类
 * 用于断点续执行功能，保存执行过程中的变量
 */
@Data
@TableName("execution_variable")
public class ExecutionVariable {
    
    /**
     * 变量主键 ID
     */
    @TableId(type = IdType.AUTO)
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
     * 是否被修改(0-否,1-是)
     */
    private Integer isModified;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}