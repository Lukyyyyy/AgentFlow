import { createContext, useCallback, useContext, useEffect, useMemo } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Panel,
  Node,
  NodeProps,
  Edge,
  Connection,
  Handle,
  Position,
  addEdge,
  useNodesState,
  useEdgesState,
  useUpdateNodeInternals,
  useInternalNode,
  OnNodesChange,
  OnEdgesChange,
  OnConnect,
  MarkerType,
  BaseEdge,
  EdgeLabelRenderer,
  EdgeProps,
  getSmoothStepPath,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { useWorkflowStore } from '../store/workflowStore';
import { conditionOperators, isProtectedWorkflowNodeType } from '../utils/workflowNode';
import { getLoopEdgeIds } from '../utils/workflowLoop';

interface FlowCanvasProps {
  onNodeClick: (node: Node) => void;
  engineType: string;
}

type WorkflowNodeData = {
  label?: string;
  type?: string;
  provider?: string;
  model?: string;
  skillName?: string;
  inputParams?: unknown[];
  outputParams?: unknown[];
  maxSteps?: number;
  agentStrategy?: string;
  tools?: string[];
  knowledgeBaseId?: string;
  knowledgeBaseName?: string;
  conditions?: Array<{ id: string; field?: string; operator?: string; value?: string }>;
};

type WorkflowCardNode = Node<WorkflowNodeData, 'workflow'>;

type WorkflowCanvasEdge = Edge<{
  loopBack: boolean;
  loopExit: boolean;
  targetLabel: string;
  caption: string;
  lane: number;
}>;

const WorkflowEdgesContext = createContext<WorkflowCanvasEdge[]>([]);

const WORKFLOW_EDGE_STYLE = {
  stroke: '#6f86d8',
  strokeWidth: 2,
};

const WORKFLOW_EDGE_MARKER = {
  type: MarkerType.ArrowClosed,
  width: 20,
  height: 20,
};

const getDefaultNodeData = (type: string) => {
  switch (type) {
    case 'knowledge_retrieve':
      return {
        topK: 5,
        scoreThreshold: 0.2,
        outputParams: ['context', 'chunks', 'citations', 'output'].map((name) => ({ name, value: name })),
      };
    case 'image_generate':
      return {
        inputParams: [{ name: 'prompt', type: 'reference', value: '' }],
        outputParams: [{ name: 'image_url', value: 'imageUrl' }],
      };
    case 'video_generate':
      return {
        inputParams: [{ name: 'prompt', type: 'reference', value: '' }],
        outputParams: [{ name: 'video_url', value: 'videoUrl' }],
      };
    default:
      return {};
  }
};

const nodeMeta: Record<string, { icon: string; tone: string; caption: string }> = {
  input: { icon: 'IN', tone: 'node-tone-green', caption: '流程输入' },
  output: { icon: 'OUT', tone: 'node-tone-purple', caption: '最终输出' },
  llm: { icon: 'AI', tone: 'node-tone-blue', caption: '模型调用' },
  react_agent: { icon: 'AI', tone: 'node-tone-blue', caption: 'ReAct 智能体' },
  openai: { icon: 'AI', tone: 'node-tone-blue', caption: 'OpenAI' },
  deepseek: { icon: 'DS', tone: 'node-tone-blue', caption: 'DeepSeek' },
  qwen: { icon: 'QW', tone: 'node-tone-blue', caption: 'Qwen' },
  zhipu: { icon: 'ZP', tone: 'node-tone-blue', caption: 'ZhiPu' },
  ai_ping: { icon: 'AP', tone: 'node-tone-blue', caption: 'AIPing' },
  tts: { icon: 'TTS', tone: 'node-tone-amber', caption: '音频工具' },
  knowledge_retrieve: { icon: 'RAG', tone: 'node-tone-green', caption: '知识检索' },
  condition: { icon: 'IF', tone: 'node-tone-orange', caption: '条件分支' },
};

const getNodeMeta = (type?: string) => (
  nodeMeta[type || ''] || { icon: 'FN', tone: 'node-tone-slate', caption: '工作流节点' }
);

const getToolLabels = (tools: string[]) => {
  const labels: string[] = [];
  if (tools.includes('web_search') || tools.includes('web_fetch')) {
    labels.push('联网搜索');
  }
  if (tools.includes('memory_write')) {
    labels.push('写入记忆');
  }
  return labels;
};

const getKnowledgeBaseLabel = (data: WorkflowNodeData) => {
  if (!data.knowledgeBaseId) {
    return '';
  }
  return data.knowledgeBaseName || '已启用';
};

const WorkflowNodeCard = ({ id, data, selected }: NodeProps<WorkflowCardNode>) => {
  const deleteNode = useWorkflowStore((state) => state.deleteNode);
  const workflowType = data.type || '';
  const isProtected = isProtectedWorkflowNodeType(workflowType);
  const meta = getNodeMeta(workflowType);
  const inputCount = useWorkflowStore((state) => state.edges.filter((edge) => edge.target === id).length);
  const outputCount = useWorkflowStore((state) => state.edges.filter((edge) => edge.source === id).length);
  const modelLabel = data.model || data.provider || data.skillName || meta.caption;
  const tools = Array.isArray(data.tools) ? data.tools : [];
  const toolLabels = getToolLabels(tools);
  const isReActMode = (workflowType === 'llm' && data.agentStrategy === 'react') || workflowType === 'react_agent';
  const knowledgeBaseLabel = getKnowledgeBaseLabel(data);

  const isCondition = workflowType === 'condition';
  const visualEdges = useContext(WorkflowEdgesContext);
  const outgoing = visualEdges.filter((edge) => edge.source === id);
  const isLoopCondition = isCondition && outgoing.some((edge) => edge.data?.loopBack);
  const conditions = Array.isArray(data.conditions) ? data.conditions : [];
  const handleCount = isCondition ? conditions.length + 1 : 0;
  const updateNodeInternals = useUpdateNodeInternals();
  useEffect(() => {
    updateNodeInternals(id);
  }, [id, handleCount, outputCount, updateNodeInternals]);

  return (
    <div className={`workflow-node ${selected ? 'is-selected' : ''}`}>
      {!isProtected && (
        <button
          type="button"
          className="workflow-node-delete nodrag nopan"
          aria-label={`删除节点 ${data.label || workflowType || id}`}
          title="删除节点"
          onPointerDown={(event) => event.stopPropagation()}
          onClick={(event) => {
            event.stopPropagation();
            deleteNode(id);
          }}
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M4 7h16M9 7V4h6v3m3 0-1 13H7L6 7m4 4v5m4-5v5" />
          </svg>
        </button>
      )}
      {workflowType !== 'input' && (
        <Handle
          type="target"
          position={Position.Top}
          className="workflow-node-handle"
        />
      )}
      <div className="workflow-node-header">
        <div className={`workflow-node-icon ${meta.tone}`}>{meta.icon}</div>
        <div className="workflow-node-title-wrap">
          <div className="workflow-node-title">{data.label || workflowType || '未命名节点'}</div>
          <div className={`workflow-node-subtitle ${isLoopCondition ? 'workflow-loop-caption' : ''}`}>
            {isLoopCondition ? '↻ 循环判断' : modelLabel}
          </div>
        </div>
      </div>
      {isCondition && (
        <div className="workflow-node-conditions">
          {[...conditions, { id: 'default', field: '', operator: '', value: '' }].map((condition, index) => {
            const connections = outgoing.filter((edge) => edge.sourceHandle === condition.id);
            const operator = conditionOperators.find((option) => option.value === condition.operator)?.label || condition.operator || '';
            const label = condition.id === 'default' ? '否则（默认）'
              : `条件 ${index + 1}：${condition.field || '待配置'} ${operator} ${condition.value || ''}`;
            return (
              <div key={condition.id} className="workflow-condition-branch">
                <div className="workflow-node-condition-item">
                  <span className={`condition-badge ${condition.id === 'default' ? 'default' : ''}`}>
                    {condition.id === 'default' ? 'D' : index + 1}
                  </span>
                  <span className="condition-label" title={label}>{label}</span>
                </div>
                {connections.length ? connections.map((edge) => (
                  <div key={edge.id} className={`workflow-branch-target ${edge.data?.loopBack ? 'is-loop' : edge.data?.loopExit ? 'is-exit' : ''}`} title={edge.data?.caption}>
                    {edge.data?.loopBack ? '↻ 循环返回 · ' : edge.data?.loopExit ? '→ 退出循环 · ' : '→ '}{edge.data?.targetLabel}
                  </div>
                )) : <div className="workflow-branch-target">未连接</div>}
                {connections.length > 1 && <div className="workflow-branch-warning">此出口有多条连线，请拆分条件</div>}
                <Handle
                  type="source"
                  position={Position.Right}
                  id={condition.id}
                  className={`workflow-node-handle condition-handle ${connections.some((edge) => edge.data?.loopBack) ? 'is-loop' : connections.some((edge) => edge.data?.loopExit) ? 'is-exit' : ''}`}
                  title={`${label}：拖动连接到目标节点`}
                  aria-label={`${label}出口`}
                />
              </div>
            );
          })}
          {outgoing.some((edge) => edge.data?.caption.includes('未绑定条件')) && (
            <div className="workflow-branch-warning">有连线未绑定条件出口，请重新连接</div>
          )}
        </div>
      )}
      {(isReActMode || knowledgeBaseLabel) && (
        <div className="workflow-node-conditions">
          {isReActMode && (
            <div className="workflow-node-condition-item">
              <span className="condition-badge">A</span>
              <span className="condition-label">Agent策略 ReAct</span>
            </div>
          )}
          {isReActMode && toolLabels.length > 0 && (
            <div className="workflow-node-condition-item">
              <span className="condition-badge default">{toolLabels.length}</span>
              <span className="condition-label">工具 {toolLabels.join(', ')}</span>
            </div>
          )}
          {knowledgeBaseLabel && (
            <div className="workflow-node-condition-item">
              <span className="condition-badge knowledge">K</span>
              <span className="condition-label">知识库 {knowledgeBaseLabel}</span>
            </div>
          )}
        </div>
      )}
      <div className="workflow-node-footer">
        <span>{workflowType || 'node'}</span>
        <span title="当前节点的入向连线数 / 出向连线数">{inputCount} 入 / {outputCount} 出</span>
      </div>
      {workflowType !== 'output' && !isCondition && (
        <Handle
          type="source"
          position={Position.Bottom}
          className="workflow-node-handle"
        />
      )}
    </div>
  );
};

const nodeTypes = {
  workflow: WorkflowNodeCard,
};

const WorkflowEdgeLine = ({
  id,
  source,
  target,
  data,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  markerEnd,
  style,
  selected,
}: EdgeProps<WorkflowCanvasEdge>) => {
  const deleteEdge = useWorkflowStore((state) => state.deleteEdge);
  const sourceNode = useInternalNode(source);
  const targetNode = useInternalNode(target);
  let [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  });
  if (data?.loopBack) {
    const right = (targetNode?.internals.positionAbsolute.x ?? targetX) + (targetNode?.measured.width ?? 236);
    const laneX = Math.max(sourceX, right) + 72 + data.lane * 28;
    const startY = sourcePosition === Position.Bottom ? sourceY + 28 : sourceY;
    const topY = Math.min(targetY, sourceNode?.internals.positionAbsolute.y ?? sourceY) - 36;
    edgePath = `M ${sourceX} ${sourceY} L ${sourceX} ${startY} L ${laneX} ${startY} L ${laneX} ${topY} L ${targetX} ${topY} L ${targetX} ${targetY}`;
    labelX = laneX;
    labelY = (startY + topY) / 2;
  }

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        markerEnd={markerEnd}
        style={style}
        interactionWidth={24}
      />
      {(data?.caption || selected) && (
        <EdgeLabelRenderer>
          {data?.caption && (
            <div
              className={`workflow-edge-caption ${data.loopBack ? 'is-loop' : data.loopExit ? 'is-exit' : ''}`}
              style={{ transform: `translate(${data.loopBack ? '12px' : '-50%'}, -50%) translate(${labelX}px, ${labelY}px)` }}
              title={data.caption}
            >
              {data.caption}
            </div>
          )}
          {selected && (
          <button
            type="button"
            className="workflow-edge-delete nodrag nopan"
            style={{ transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY - (data?.caption ? 28 : 0)}px)` }}
            aria-label="取消节点连接"
            title="取消连接"
            onPointerDown={(event) => event.stopPropagation()}
            onClick={(event) => {
              event.stopPropagation();
              deleteEdge(id);
            }}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="m7 7 10 10M17 7 7 17" />
            </svg>
          </button>
          )}
        </EdgeLabelRenderer>
      )}
    </>
  );
};

const edgeTypes = {
  workflow: WorkflowEdgeLine,
};

/**
 * 中间画布组件
 */
const FlowCanvas = ({ onNodeClick, engineType }: FlowCanvasProps) => {
  const {
    nodes: storeNodes,
    edges: storeEdges,
    setNodes: setStoreNodes,
    setEdges: setStoreEdges,
    deleteNode,
  } = useWorkflowStore();
  
  const [nodes, setNodes, onNodesChange] = useNodesState(storeNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(storeEdges);
  const loopEdgeIds = useMemo(() => getLoopEdgeIds(edges), [edges]);
  const visualEdges = useMemo<WorkflowCanvasEdge[]>(() => {
    const nodeById = new Map(storeNodes.map((node) => [node.id, node]));
    const loopConditions = new Set(edges.filter((edge) => loopEdgeIds.has(edge.id)
      && nodeById.get(edge.source)?.data.type === 'condition').map((edge) => edge.source));
    let lane = 0;
    return edges.map((edge) => {
      const source = nodeById.get(edge.source);
      const target = nodeById.get(edge.target);
      const inLoop = loopEdgeIds.has(edge.id);
      const isCondition = source?.data.type === 'condition';
      const loopBack = inLoop && (isCondition || edge.source === edge.target);
      const loopExit = loopConditions.has(edge.source) && !inLoop;
      const conditions = (Array.isArray(source?.data.conditions) ? source.data.conditions : []) as WorkflowNodeData['conditions'];
      const branchIndex = conditions?.findIndex((condition) => condition.id === edge.sourceHandle) ?? -1;
      const branchLabel = isCondition ? edge.sourceHandle === 'default' ? '否则' : branchIndex >= 0 ? `条件 ${branchIndex + 1}` : '未绑定条件' : '';
      const targetLabel = String(target?.data.label || edge.target);
      const caption = loopBack ? `↻ 循环返回 · ${branchLabel ? `${branchLabel} → ` : ''}${targetLabel}`
        : loopExit ? `退出循环 · ${branchLabel} → ${targetLabel}` : branchLabel;
      const color = inLoop ? '#7c3aed' : loopExit ? '#16845b' : WORKFLOW_EDGE_STYLE.stroke;
      return {
        ...edge,
        type: 'workflow',
        style: { ...WORKFLOW_EDGE_STYLE, stroke: color, strokeWidth: inLoop ? 2.5 : 2, strokeDasharray: loopBack ? '7 5' : undefined },
        markerEnd: { ...WORKFLOW_EDGE_MARKER, color },
        data: { ...edge.data, loopBack, loopExit, targetLabel, caption, lane: loopBack ? lane++ : 0 },
      };
    });
  }, [edges, storeNodes, loopEdgeIds]);

  // 当 store 中的 nodes/edges 变化时，同步更新到本地状态
  useEffect(() => {
    console.log('Store nodes changed:', storeNodes);
    setNodes(storeNodes);
  }, [storeNodes, setNodes]);

  useEffect(() => {
    console.log('Store edges changed:', storeEdges);
    setEdges(storeEdges);
  }, [storeEdges, setEdges]);

  // 同步到 store
  const handleNodesChange: OnNodesChange = useCallback((changes) => {
    onNodesChange(changes);
    // 使用 setTimeout 确保状态更新后再同步
    setTimeout(() => {
      setNodes((currentNodes) => {
        setStoreNodes(currentNodes);
        return currentNodes;
      });
    }, 0);
  }, [onNodesChange, setNodes, setStoreNodes]);

  const handleEdgesChange: OnEdgesChange = useCallback((changes) => {
    onEdgesChange(changes);
    setTimeout(() => {
      setEdges((currentEdges) => {
        setStoreEdges(currentEdges);
        return currentEdges;
      });
    }, 0);
  }, [onEdgesChange, setEdges, setStoreEdges]);

  const handleConnect: OnConnect = useCallback((connection: Connection) => {
    console.log('Connection created:', connection);
    setEdges((eds) => {
      const newEdge = {
        ...connection,
        type: 'workflow',
        style: WORKFLOW_EDGE_STYLE,
        markerEnd: WORKFLOW_EDGE_MARKER,
      };
      console.log('New edge:', newEdge);
      const updatedEdges = addEdge(newEdge, eds);
      setStoreEdges(updatedEdges);
      return updatedEdges;
    });
  }, [setEdges, setStoreEdges]);

  // 处理拖拽放置
  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();

      const type = event.dataTransfer.getData('application/reactflow-type');
      const label = event.dataTransfer.getData('application/reactflow-label');

      if (!type) return;

      const reactFlowBounds = (event.target as HTMLElement).getBoundingClientRect();
      const position = {
        x: event.clientX - reactFlowBounds.left - 75,
        y: event.clientY - reactFlowBounds.top - 25,
      };

      const newNode: Node = {
        id: `${type}-${Date.now()}`,
        type: 'workflow',
        deletable: !isProtectedWorkflowNodeType(type),
        position,
        data: { label: label || type, type, ...getDefaultNodeData(type) },
      };

      setNodes((nds) => {
        const updatedNodes = nds.concat(newNode);
        setStoreNodes(updatedNodes);
        return updatedNodes;
      });
    },
    [setNodes, setStoreNodes]
  );

  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, []);

  const handleNodeClick = useCallback(
    (_: React.MouseEvent, node: Node) => {
      onNodeClick(node);
    },
    [onNodeClick]
  );

  const handleNodesDelete = useCallback((deletedNodes: Node[]) => {
    deletedNodes.forEach((node) => deleteNode(node.id));
  }, [deleteNode]);

  return (
    <WorkflowEdgesContext.Provider value={visualEdges}>
    <div className="h-full w-full">
      <ReactFlow
        nodes={nodes}
        edges={visualEdges}
        onNodesChange={handleNodesChange}
        onEdgesChange={handleEdgesChange}
        onConnect={handleConnect}
        onDrop={onDrop}
        onDragOver={onDragOver}
        onNodeClick={handleNodeClick}
        onNodesDelete={handleNodesDelete}
        deleteKeyCode={['Backspace', 'Delete']}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        defaultEdgeOptions={{
          type: 'workflow',
          style: WORKFLOW_EDGE_STYLE,
          markerEnd: WORKFLOW_EDGE_MARKER,
        }}
        fitView
        fitViewOptions={{ padding: 0.28 }}
        snapToGrid
        snapGrid={[16, 16]}
      >
        {loopEdgeIds.size > 0 && (
          <Panel position="top-left" className="workflow-loop-legend">
            <strong>↻ 循环路径</strong>
            <span className="workflow-loop-caption">紫色实线：循环体 · 虚线：循环返回</span>
            <span className="workflow-exit-caption">绿色连线：退出循环</span>
            {engineType === 'dag' && <span className="workflow-branch-warning">运行循环请切换至 LangGraph 引擎</span>}
          </Panel>
        )}
        <Background color="#d4dce8" gap={20} size={1.25} />
        <Controls className="workflow-controls" />
        <MiniMap
          pannable
          zoomable
          className="workflow-minimap"
          maskColor="rgba(238, 242, 247, 0.76)"
          nodeColor={(node) => getNodeMeta(String(node.data?.type || '')).tone.includes('green') ? '#20a36a' : '#5d70d6'}
        />
      </ReactFlow>
    </div>
    </WorkflowEdgesContext.Provider>
  );
};

export default FlowCanvas;
