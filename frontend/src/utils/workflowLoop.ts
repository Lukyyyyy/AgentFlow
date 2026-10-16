import type { Edge } from '@xyflow/react';

export const getLoopEdgeIds = (edges: Pick<Edge, 'id' | 'source' | 'target'>[]) => {
  const outgoing = new Map<string, string[]>();
  for (const edge of edges) {
    const targets = outgoing.get(edge.source) || [];
    targets.push(edge.target);
    outgoing.set(edge.source, targets);
  }

  const reachable = new Map<string, Set<string>>();
  const loopEdges = new Set<string>();
  for (const edge of edges) {
    let visited = reachable.get(edge.target);
    if (!visited) {
      visited = new Set<string>();
      const pending = [edge.target];
      while (pending.length) {
        const node = pending.pop()!;
        if (visited.has(node)) continue;
        visited.add(node);
        pending.push(...(outgoing.get(node) || []));
      }
      reachable.set(edge.target, visited);
    }
    if (visited.has(edge.source)) loopEdges.add(edge.id);
  }
  return loopEdges;
};
