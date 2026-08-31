package com.agentflow.service;

import com.agentflow.common.ResourceNotFoundException;
import com.agentflow.entity.Workflow;
import com.agentflow.mapper.WorkflowMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowServiceIsolationTest {

    @Test
    void requireWorkflowQueriesByResourceAndOwner() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Workflow.class);
        WorkflowMapper mapper = mock(WorkflowMapper.class);
        WorkflowService service = new WorkflowService();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.selectOne(any(Wrapper.class), eq(true))).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.requireWorkflow(7L, 99L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Workflow>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectOne(captor.capture(), eq(true));
        Wrapper<Workflow> query = captor.getValue();
        assertTrue(query.getSqlSegment().contains("owner_id"));
        assertTrue(query.getSqlSegment().contains("id"));
        AbstractWrapper<?, ?, ?> abstractQuery = (AbstractWrapper<?, ?, ?>) query;
        assertEquals(Set.of(7L, 99L), Set.copyOf(abstractQuery.getParamNameValuePairs().values()));
    }
}
