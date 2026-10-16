package com.agentflow.engine.langgraph.builder;

import com.agentflow.dto.ExecutionEvent;
import com.agentflow.engine.langgraph.adapter.NodeAdapter;
import com.agentflow.engine.model.WorkflowConfig;
import com.agentflow.engine.model.WorkflowEdge;
import com.agentflow.engine.model.WorkflowNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** 将工作流转换为支持条件路由与回边的状态图。 */
@Component
public class GraphBuilder {
    @Autowired
    private NodeAdapter nodeAdapter;

    public CompiledGraph<AgentState> buildGraph(
            WorkflowConfig config, Consumer<ExecutionEvent> eventCallback) throws Exception {
        if (config.getNodes() == null || config.getNodes().isEmpty()) {
            throw new IllegalArgumentException("工作流至少需要一个节点");
        }
        if (config.getMaxSteps() < 1 || config.getMaxSteps() > 10000) {
            throw new IllegalArgumentException("最大执行步数必须是 1 到 10000 的整数");
        }
        Map<String, WorkflowNode> nodes = new LinkedHashMap<>();
        Map<String, List<WorkflowEdge>> outgoing = new HashMap<>();
        Set<String> incoming = new HashSet<>();
        for (WorkflowNode node : config.getNodes()) {
            String id = node.getId();
            if (id == null || id.isBlank() || StateGraph.START.equals(id) || StateGraph.END.equals(id)
                    || nodes.putIfAbsent(id, node) != null) {
                throw new IllegalArgumentException("节点 ID 为空、重复或使用保留值: " + id);
            }
        }
        for (WorkflowEdge edge : config.getEdges() == null ? List.<WorkflowEdge>of() : config.getEdges()) {
            if (!nodes.containsKey(edge.getSource()) || !nodes.containsKey(edge.getTarget())) {
                throw new IllegalArgumentException("连线引用了不存在的节点: " + edge.getId());
            }
            outgoing.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(edge);
            incoming.add(edge.getTarget());
        }

        // 循环入口可能有回边，优先以明确的输入节点作为入口。
        List<WorkflowNode> entries = nodes.values().stream().filter(n -> "input".equals(n.getType())).toList();
        if (entries.isEmpty()) {
            entries = nodes.values().stream().filter(n -> !incoming.contains(n.getId())).toList();
        }
        if (entries.size() != 1) {
            throw new IllegalArgumentException("LangGraph 需要唯一入口，请保留一个输入节点");
        }
        String entryId = entries.get(0).getId();
        Set<String> reachable = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(entryId);
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (reachable.add(id)) {
                outgoing.getOrDefault(id, List.of()).forEach(edge -> pending.addLast(edge.getTarget()));
            }
        }
        if (reachable.size() != nodes.size()) {
            throw new IllegalArgumentException("存在无法从入口到达的节点");
        }

        StateGraph<AgentState> graph = new StateGraph<>(AgentState::new);
        for (WorkflowNode node : nodes.values()) {
            graph.addNode(node.getId(), nodeAdapter.adaptNode(node, eventCallback, config.getMaxSteps()));
        }
        graph.addEdge(StateGraph.START, entryId);
        for (WorkflowNode node : nodes.values()) {
            List<WorkflowEdge> edges = outgoing.getOrDefault(node.getId(), List.of());
            Map<String, String> branches = new HashMap<>();
            boolean condition = "condition".equals(node.getType());
            if (condition) {
                branches.put("default", StateGraph.END);
                Object configured = node.getData() == null ? null : node.getData().get("conditions");
                if (configured != null && !(configured instanceof List<?>)) {
                    throw new IllegalArgumentException("条件列表格式错误: " + node.getId());
                }
                for (Object item : configured == null ? List.of() : (List<?>) configured) {
                    if (!(item instanceof Map<?, ?> rule) || !(rule.get("id") instanceof String id)
                            || id.isBlank() || branches.putIfAbsent(id, StateGraph.END) != null) {
                        throw new IllegalArgumentException("条件 ID 为空、重复或使用 default 保留值: " + node.getId());
                    }
                }
                Set<String> connected = new HashSet<>();
                for (WorkflowEdge edge : edges) {
                    String handle = edge.getSourceHandle();
                    if (!branches.containsKey(handle) || !connected.add(handle)) {
                        throw new IllegalArgumentException("条件出口无效或重复连线: " + node.getId() + " / " + handle);
                    }
                    branches.put(handle, edge.getTarget());
                }
            } else if (edges.size() > 1) {
                throw new IllegalArgumentException("普通节点只能连接一个后继，请使用条件分支节点: " + node.getId());
            }
            String next = edges.isEmpty() ? StateGraph.END : edges.get(0).getTarget();
            Map<String, String> targets = new HashMap<>();
            targets.put(StateGraph.END, StateGraph.END);
            edges.forEach(edge -> targets.put(edge.getTarget(), edge.getTarget()));
            graph.addConditionalEdges(node.getId(), state -> {
                if ("FAILED".equals(state.data().get("status"))) {
                    return CompletableFuture.completedFuture(StateGraph.END);
                }
                if (!condition) {
                    return CompletableFuture.completedFuture(next);
                }
                Map<?, ?> output = (Map<?, ?>) state.data().get("currentInput");
                String branch = (String) output.get("__selectedBranch__");
                if (!branches.containsKey(branch)) {
                    throw new IllegalStateException("条件返回未知分支: " + branch);
                }
                return CompletableFuture.completedFuture(branches.get(branch));
            }, targets);
        }
        CompiledGraph<AgentState> compiled = graph.compile();
        // 库达到迭代上限会静默结束；让节点适配器先报出明确的步数超限错误。
        compiled.setMaxIterations(config.getMaxSteps() + 4);
        return compiled;
    }
}
