package com.agentflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 执行快照实体类
 * 用于断点续执行功能，保存每个节点的执行状态和中间输出
 */
@Data
@TableName("execution_snapshot")
public class ExecutionSnapshot {
    
    /**
     * 快照主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 执行记录 ID
     */
    private Long executionId;
    
    /**
     * 工作流 ID
     */
    private Long flowId;
    
    /**
     * 节点 ID
     */
    private String nodeId;
    
    /**
     * 节点类型
     */
    private String nodeType;
    
    /**
     * 节点名称
     */
    private String nodeName;
    
    /**
     * 节点状态(PENDING/RUNNING/SUCCESS/FAILED/SKIPPED)
     */
    private String status;
    
    /**
     * 节点输入数据 - JSON 格式
     */
    private String inputData;
    
    /**
     * 节点输出数据 - JSON 格式
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String outputData;
    
    /**
     * 错误信息
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorMessage;
    
    /**
     * 开始执行时间
     */
    private LocalDateTime startedAt;
    
    /**
     * 完成时间
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime completedAt;
    
    /**
     * 执行耗时(毫秒)
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer duration;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 执行顺序
     */
    private Integer executionOrder;
    
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
