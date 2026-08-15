import { create } from 'zustand';
import { Node, Edge } from '@xyflow/react';
import {
  createDefaultWorkflowNodes,
  isProtectedWorkflowNode,
  normalizeWorkflowNodes,
} from '../utils/workflowNode';

interface WorkflowState {
  nodes: Node[];
  edges: Edge[];
  selectedNode: Node | null;
  currentWorkflowId: number | null;
  setNodes: (nodes: Node[]) => void;
  setEdges: (edges: Edge[]) => void;
  setSelectedNode: (node: Node | null) => void;
  setCurrentWorkflowId: (id: number | null) => void;
  addNode: (node: Node) => void;
  updateNode: (id: string, data: Record<string, unknown>) => void;
  deleteNode: (id: string) => void;
  deleteEdge: (id: string) => void;
  clear: () => void;
}

const defaultNodes: Node[] = createDefaultWorkflowNodes();

export const useWorkflowStore = create<WorkflowState>((set) => ({
  nodes: defaultNodes,
  edges: [],
  selectedNode: null,
  currentWorkflowId: null,
  
  setNodes: (nodes) => set({ nodes: normalizeWorkflowNodes(nodes) }),
  
  setEdges: (edges) => set({ edges }),
  
  setSelectedNode: (node) => set({ selectedNode: node }),
  
  setCurrentWorkflowId: (id) => set({ currentWorkflowId: id }),
  
  addNode: (node) => set((state) => ({
    nodes: [...state.nodes, normalizeWorkflowNodes([node])[0]]
  })),
  
  updateNode: (id, data) => set((state) => ({
    nodes: state.nodes.map((node) =>
      node.id === id ? { ...node, data: { ...node.data, ...data } } : node
    )
  })),
  
  deleteNode: (id) => set((state) => {
    const node = state.nodes.find((item) => item.id === id);
    if (!node || isProtectedWorkflowNode(node)) {
      return state;
    }

    return {
      nodes: state.nodes.filter((item) => item.id !== id),
      edges: state.edges.filter((edge) => edge.source !== id && edge.target !== id),
      selectedNode: state.selectedNode?.id === id ? null : state.selectedNode
    };
  }),

  deleteEdge: (id) => set((state) => ({
    edges: state.edges.filter((edge) => edge.id !== id)
  })),
  
  clear: () => set({
    nodes: createDefaultWorkflowNodes(),
    edges: [],
    selectedNode: null,
    currentWorkflowId: null
  })
}));
