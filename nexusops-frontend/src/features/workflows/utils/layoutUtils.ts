import dagre from 'dagre';
import { Node, Edge, Position } from '@xyflow/react';
import { WorkflowNodeData, WorkflowEdgeData } from '../types/workflowCanvas';

export const NODE_WIDTH = 270;
export const NODE_HEIGHT = 110;

export interface LayoutOptions {
  direction?: 'LR' | 'TB';
  rankSep?: number;
  nodeSep?: number;
}

/**
 * Computes an automatic hierarchical Dagre layout for workflow nodes and edges.
 * Arranges nodes strictly from Left to Right (Start on far left, Ends on far right).
 */
export function getLayoutedElements(
  nodes: Node<WorkflowNodeData>[],
  edges: Edge<WorkflowEdgeData>[],
  options: LayoutOptions = {}
): { nodes: Node<WorkflowNodeData>[]; edges: Edge<WorkflowEdgeData>[] } {
  const { direction = 'LR', rankSep = 100, nodeSep = 50 } = options;

  const dagreGraph = new dagre.graphlib.Graph();
  dagreGraph.setDefaultEdgeLabel(() => ({}));

  dagreGraph.setGraph({
    rankdir: direction,
    ranksep: rankSep,
    nodesep: nodeSep,
    marginx: 40,
    marginy: 40,
  });

  // Add nodes to Dagre
  nodes.forEach((node) => {
    dagreGraph.setNode(node.id, {
      width: NODE_WIDTH,
      height: NODE_HEIGHT,
    });
  });

  // Sort edges by priority (lower priority number = evaluated earlier = placed on top)
  const sortedEdges = [...edges].sort((a, b) => {
    const prioA = a.data?.priority ?? 99;
    const prioB = b.data?.priority ?? 99;
    return prioA - prioB;
  });

  // Add edges to Dagre
  sortedEdges.forEach((edge) => {
    dagreGraph.setEdge(edge.source, edge.target);
  });

  // Run the layout calculation
  dagre.layout(dagreGraph);

  // Map calculated coordinates back to React Flow nodes
  const layoutedNodes = nodes.map((node) => {
    const nodeWithPosition = dagreGraph.node(node.id);

    // Dagre returns center coordinates; convert to top-left for React Flow
    const x = Math.round(nodeWithPosition.x - NODE_WIDTH / 2);
    const y = Math.round(nodeWithPosition.y - NODE_HEIGHT / 2);

    return {
      ...node,
      targetPosition: Position.Left,
      sourcePosition: Position.Right,
      position: { x, y },
    };
  });

  return { nodes: layoutedNodes, edges };
}

/**
 * Checks whether nodes already have valid, distinct saved positions.
 */
export function hasValidSavedPositions(nodes: any[]): boolean {
  if (!nodes || nodes.length <= 1) return false;

  const validPositions = nodes.filter(
    (n) => typeof n.positionX === 'number' && n.positionX > 0 && typeof n.positionY === 'number' && n.positionY > 0
  );

  // If fewer than half of nodes have positions, or all positions are identical (e.g. 100, 200), auto-layout is needed
  if (validPositions.length < nodes.length) return false;

  const uniqueX = new Set(nodes.map((n) => Math.round(n.positionX || 0)));
  const uniqueY = new Set(nodes.map((n) => Math.round(n.positionY || 0)));

  return uniqueX.size > 2 && uniqueY.size > 2;
}

