package com.agentflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.agentflow.entity.ExecutionSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 执行快照 Mapper 接口
 */
@Mapper
public interface ExecutionSnapshotMapper extends BaseMapper<ExecutionSnapshot> {
    
    /**
     * 根据执行记录 ID 查询快照列表
     */
    List<ExecutionSnapshot> selectByExecutionId(@Param("executionId") Long executionId);
    
    /**
     * 根据执行记录 ID 和节点 ID 查询快照
     */
    ExecutionSnapshot selectByExecutionIdAndNodeId(
        @Param("executionId") Long executionId, 
        @Param("nodeId") String nodeId
    );
    
    /**
     * 根据执行记录 ID 删除快照
     */
    int deleteByExecutionId(@Param("executionId") Long executionId);
}