package com.agentflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.agentflow.entity.ExecutionVariable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 执行变量 Mapper 接口
 */
@Mapper
public interface ExecutionVariableMapper extends BaseMapper<ExecutionVariable> {
    
    /**
     * 根据执行记录 ID 查询变量列表
     */
    List<ExecutionVariable> selectByExecutionId(@Param("executionId") Long executionId);
    
    /**
     * 根据执行记录 ID 和变量名查询变量
     */
    ExecutionVariable selectByExecutionIdAndVariableName(
        @Param("executionId") Long executionId, 
        @Param("variableName") String variableName
    );
    
    /**
     * 根据执行记录 ID 删除变量
     */
    int deleteByExecutionId(@Param("executionId") Long executionId);
}