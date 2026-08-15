package com.agentflow.engine.executor.impl;

import org.springframework.stereotype.Component;

/**
 * 阶跃星辰(Step) 节点执行器
 * 基于 OpenAI 兼容接口实现
 */
@Component
public class StepNodeExecutor extends AbstractLLMNodeExecutor {

    @Override
    protected String getNodeType() {
        return "step";
    }
}
