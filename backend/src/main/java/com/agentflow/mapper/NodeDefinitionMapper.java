package com.agentflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.agentflow.entity.NodeDefinition;
import org.apache.ibatis.annotations.Mapper;

/**
 * 节点定义 Mapper 接口
 */
@Mapper
public interface NodeDefinitionMapper extends BaseMapper<NodeDefinition> {
}
