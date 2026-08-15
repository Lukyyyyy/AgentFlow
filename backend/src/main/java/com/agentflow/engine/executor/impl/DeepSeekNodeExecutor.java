package com.agentflow.engine.executor.impl;

import org.springframework.stereotype.Component;

/**
 * DeepSeek 节点执行器
 * 基于Spring AI实现，使用OpenAI兼容API
 */
@Component
public class DeepSeekNodeExecutor extends AbstractLLMNodeExecutor {
    
    @Override
    protected String getNodeType() {
        return "deepseek";
    }
}
