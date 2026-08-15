package com.agentflow.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 执行快照响应 DTO
 */
@Data
public class ExecutionSnapshotResponse {
    
    /**
     * 快照 ID
     */
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
     * 节点状态
     */
    private String status;
    
    /**
     * 输入数据
     */
    private Map<String, Object> inputData;
    
    /**
     * 输出数据
     */
    private Map<String, Object> outputData;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 开始执行时间
     */
    private LocalDateTime startedAt;
    
    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
    
    /**
     * 执行耗时(毫秒)
     */
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
    private LocalDateTime createdAt;
}