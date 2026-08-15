import { Node } from '@xyflow/react';

const REACT_FLOW_NODE_TYPE = 'workflow';
const PROTECTED_WORKFLOW_NODE_TYPES = new Set(['input', 'output']);

export const isProtectedWorkflowNodeType = (nodeType: string) =>
  PROTECTED_WORKFLOW_NODE_TYPES.has(nodeType);

const getFallbackLabel = (nodeType: string, nodeId: string) => {
  switch (nodeType) {
    case 'input':
      return '输入';
    case 'output':
      return '输出';
    case 'tts':
      return '语音合成';
    case 'llm':
      return '大模型';
    case 'react_agent':
      return 'ReAct Agent';
    default:
      return nodeType || nodeId;
  }
};

export const getWorkflowNodeType = (node: Pick<Node, 'type' | 'data'>) => {
  const dataType = typeof node.data?.type === 'string' ? node.data.type : '';
  if (dataType) {
    return dataType;
  }

  return node.type && node.type !== REACT_FLOW_NODE_TYPE ? node.type : '';
};

export const isProtectedWorkflowNode = (node: Pick<Node, 'type' | 'data'>) =>
  isProtectedWorkflowNodeType(getWorkflowNodeType(node));

export const normalizeWorkflowNode = (node: Node): Node => {
  const workflowNodeType = getWorkflowNodeType(node);

  return {
    ...node,
    type: REACT_FLOW_NODE_TYPE,
    deletable: isProtectedWorkflowNodeType(workflowNodeType) ? false : node.deletable,
    data: {
      ...node.data,
      type: workflowNodeType,
      label: typeof node.data?.label === 'string' && node.data.label.trim()
        ? node.data.label
        : getFallbackLabel(workflowNodeType, node.id),
    },
  };
};

export const normalizeWorkflowNodes = (nodes: Node[]) => nodes.map(normalizeWorkflowNode);

export const serializeWorkflowNodes = (nodes: Node[]) =>
  nodes.map((node) => ({
    id: node.id,
    type: getWorkflowNodeType(node) || node.type,
    position: node.position,
    data: {
      ...node.data,
      type: getWorkflowNodeType(node) || node.type,
    },
  }));

export const createDefaultWorkflowNodes = (): Node[] => [
  {
    id: 'input-default',
    type: REACT_FLOW_NODE_TYPE,
    deletable: false,
    position: { x: 120, y: 240 },
    data: {
      label: '输入节点',
      type: 'input',
    },
  },
  {
    id: 'output-default',
    type: REACT_FLOW_NODE_TYPE,
    deletable: false,
    position: { x: 560, y: 240 },
    data: {
      label: '输出节点',
      type: 'output',
      outputParams: [],
      responseContent: '',
    },
  },
];
