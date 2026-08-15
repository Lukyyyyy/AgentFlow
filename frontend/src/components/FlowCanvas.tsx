import { useCallback, useEffect } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Node,
  NodeProps,
  Connection,
  Handle,
  Position,
  addEdge,
  useNodesState,
  useEdgesState,
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
import { isProtectedWorkflowNodeType } from '../utils/workflowNode';

interface FlowCanvasProps {
  onNodeClick: (node: Node) => void;
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
  const inputCount = Array.isArray(data.inputParams) ? data.inputParams.length : 0;
  const outputCount = Array.isArray(data.outputParams) ? data.outputParams.length : 0;
  const modelLabel = data.model || data.provider || data.skillName || meta.caption;
  const tools = Array.isArray(data.tools) ? data.tools : [];
  const toolLabels = getToolLabels(tools);
  const isReActMode = (workflowType === 'llm' && data.agentStrategy === 'react') || workflowType === 'react_agent';
  const knowledgeBaseLabel = getKnowledgeBaseLabel(data);

  const isCondition = workflowType === 'condition';
  const conditions = Array.isArray(data.conditions) ? data.conditions : [];
  const handleCount = isCondition ? conditions.length + 1 : 0;

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
          <div className="workflow-node-subtitle">{modelLabel}</div>
        </div>
      </div>
      {isCondition && conditions.length > 0 && (
        <div className="workflow-node-conditions">
          {conditions.map((c, i) => (
            <div key={c.id} className="workflow-node-condition-item">
              <span className="condition-badge">{i + 1}</span>
              <span className="condition-label">{c.field} {c.operator} {c.value}</span>
            </div>
          ))}
          <div className="workflow-node-condition-item condition-default">
            <span className="condition-badge default">D</span>
            <span className="condition-label">否则(默认)</span>
          </div>
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
        <span>{inputCount} 入 / {outputCount} 出</span>
      </div>
      {workflowType === 'output' ? null : isCondition ? (
        <>
          {conditions.map((c, i) => (
            <Handle
              key={c.id}
              type="source"
              position={Position.Bottom}
              id={c.id}
              className="workflow-node-handle condition-handle"
              style={{ left: `${((i + 1) / (handleCount + 1)) * 100}%` }}
            />
          ))}
          <Handle
            type="source"
            position={Position.Bottom}
            id="default"
            className="workflow-node-handle condition-handle"
            style={{ left: `${(handleCount / (handleCount + 1)) * 100}%` }}
          />
        </>
      ) : (
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
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  markerEnd,
  style,
  selected,
}: EdgeProps) => {
  const deleteEdge = useWorkflowStore((state) => state.deleteEdge);
  const [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  });

  return (
    <>
      <BaseEdge
        id={id}
        path={edgePath}
        markerEnd={markerEnd}
        style={style}
        interactionWidth={24}
      />
      {selected && (
        <EdgeLabelRenderer>
          <button
            type="button"
            className="workflow-edge-delete nodrag nopan"
            style={{ transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)` }}
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
const FlowCanvas = ({ onNodeClick }: FlowCanvasProps) => {
  const {
    nodes: storeNodes,
    edges: storeEdges,
    setNodes: setStoreNodes,
    setEdges: setStoreEdges,
    deleteNode,
  } = useWorkflowStore();
  
  const [nodes, setNodes, onNodesChange] = useNodesState(storeNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(storeEdges);

  // 当 store 中的 nodes/edges 变化时，同步更新到本地状态
  useEffect(() => {
    console.log('Store nodes changed:', storeNodes);
    setNodes(storeNodes);
  }, [storeNodes, setNodes]);

  useEffect(() => {
    console.log('Store edges changed:', storeEdges);
    const edgesWithMarkers = storeEdges.map(edge => ({
      ...edge,
      type: 'workflow',
      style: WORKFLOW_EDGE_STYLE,
      markerEnd: WORKFLOW_EDGE_MARKER,
    }));
    setEdges(edgesWithMarkers);
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
    <div className="h-full w-full">
      <ReactFlow
        nodes={nodes}
        edges={edges}
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
  );
};

export default FlowCanvas;
