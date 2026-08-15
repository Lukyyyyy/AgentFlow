package com.agentflow.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 执行历史对比响应 DTO
 */
@Data
public class ExecutionComparisonResponse {
    
    /**
     * 当前执行 ID
     */
    private Long currentExecutionId;
    
    /**
     * 上次执行 ID
     */
    private Long previousExecutionId;
    
    /**
     * 工作流 ID
     */
    private Long flowId;
    
    /**
     * 当前执行快照列表
     */
    private List<NodeComparison> currentNodes;
    
    /**
     * 上次执行快照列表
     */
    private List<NodeComparison> previousNodes;
    
    /**
     * 差异摘要
     */
    private DiffSummary diffSummary;
    
    /**
     * 节点对比信息
     */
    @Data
    public static class NodeComparison {
        private String nodeId;
        private String nodeName;
        private String status;
        private Integer duration;
        private Map<String, Object> outputData;
        private String errorMessage;
        private Boolean hasChanged; // 与对比对象相比是否有变化
    }
    
    /**
     * 差异摘要
     */
    @Data
    public static class DiffSummary {
        private Integer totalNodes; // 总节点数
        private Integer successNodes; // 成功节点数
        private Integer failedNodes; // 失败节点数
        private Integer changedNodes; // 有变化的节点数
        private Integer newNodes; // 新增节点数
        private Integer removedNodes; // 删除节点数
        private Long totalDuration; // 总耗时
        private Long durationDiff; // 耗时差异
    }
}