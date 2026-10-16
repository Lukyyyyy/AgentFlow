package com.agentflow.engine.model;

import lombok.Data;
import java.util.List;

/**
 * 工作流配置模型
 */
@Data
public class WorkflowConfig {
    
    /**
     * 节点列表
     */
    private List<WorkflowNode> nodes;
    
    /**
     * 连线列表
     */
    private List<WorkflowEdge> edges;

    /** LangGraph 单次运行允许执行的节点总次数（包含循环中的重复执行）。 */
    private int maxSteps = 100;
}
