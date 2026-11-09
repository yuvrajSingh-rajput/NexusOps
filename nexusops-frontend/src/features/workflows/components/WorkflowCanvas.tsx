import React, { useState, useCallback, useRef, useEffect, useMemo } from 'react';
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  Panel,
  useNodesState,
  useEdgesState,
  addEdge,
  Connection,
  Edge,
  Node,
  MarkerType,
  ReactFlowInstance,
  BackgroundVariant
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import { StartNode, ApprovalNode, ConditionNode, TaskNode, EndNode } from './CustomNodes';
import { CustomEdge } from './CustomEdge';
import { NodePalette } from './NodePalette';
import { NodeConfigSidebar } from './NodeConfigSidebar';
import {
  WorkflowNodeData,
  WorkflowEdgeData,
  WorkflowNodeType,
  SaveWorkflowDraftDto
} from '../types/workflowCanvas';

const nodeTypes = {
  START: StartNode,
  APPROVAL: ApprovalNode,
  CONDITION: ConditionNode,
  TASK: TaskNode,
  END: EndNode
};

const edgeTypes = {
  custom: CustomEdge
};

interface WorkflowCanvasProps {
  initialNodes: any[];
  initialTransitions: any[];
  isReadOnly: boolean;
  onSaveDraft: (draft: SaveWorkflowDraftDto) => void;
  isSaving: boolean;
}

export const WorkflowCanvas: React.FC<WorkflowCanvasProps> = ({
  initialNodes,
  initialTransitions,
  isReadOnly,
  onSaveDraft,
  isSaving
}) => {
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const [reactFlowInstance, setReactFlowInstance] = useState<ReactFlowInstance | null>(null);

  // Convert initial backend nodes to React Flow nodes
  const formattedInitialNodes = useMemo(() => {
    return (initialNodes || []).map((n) => {
      const type = (n.nodeType || n.type || 'TASK') as WorkflowNodeType;
      return {
        id: n.nodeKey,
        type: type,
        position: {
          x: typeof n.positionX === 'number' && n.positionX > 0 ? n.positionX : 100,
          y: typeof n.positionY === 'number' && n.positionY > 0 ? n.positionY : 200
        },
        data: {
          nodeKey: n.nodeKey,
          name: n.name || n.title || n.nodeKey,
          nodeType: type,
          configuration: n.configuration || {},
          isPublished: isReadOnly
        }
      } as Node<WorkflowNodeData>;
    });
  }, [initialNodes, isReadOnly]);

  // Convert initial backend transitions to React Flow edges
  const formattedInitialEdges = useMemo(() => {
    // Build node id to nodeKey map
    const idToKeyMap: Record<string, string> = {};
    (initialNodes || []).forEach((n) => {
      if (n.id) idToKeyMap[n.id] = n.nodeKey;
    });

    return (initialTransitions || []).map((t, idx) => {
      const sourceKey = t.sourceNodeKey || idToKeyMap[t.sourceNodeId] || t.sourceNodeId;
      const targetKey = t.targetNodeKey || idToKeyMap[t.targetNodeId] || t.targetNodeId;

      return {
        id: t.id || `edge-${sourceKey}-${targetKey}-${idx}`,
        source: sourceKey,
        target: targetKey,
        type: 'custom',
        markerEnd: {
          type: MarkerType.ArrowClosed,
          width: 14,
          height: 14,
          color: '#6366f1'
        },
        data: {
          conditionExpression: t.conditionExpression,
          priority: t.priority ?? 0,
          isDefault: t.isDefault ?? false
        }
      } as Edge<WorkflowEdgeData>;
    });
  }, [initialTransitions, initialNodes]);

  const [nodes, setNodes, onNodesChange] = useNodesState<Node<WorkflowNodeData>>(formattedInitialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge<WorkflowEdgeData>>(formattedInitialEdges);

  // Sync state if props change (e.g. version switch)
  useEffect(() => {
    setNodes(formattedInitialNodes);
    setEdges(formattedInitialEdges);
  }, [formattedInitialNodes, formattedInitialEdges, setNodes, setEdges]);

  // Selected state
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null);

  const selectedNode = useMemo(() => {
    return nodes.find((n) => n.id === selectedNodeId) || null;
  }, [nodes, selectedNodeId]);

  const selectedEdge = useMemo(() => {
    return edges.find((e) => e.id === selectedEdgeId) || null;
  }, [edges, selectedEdgeId]);

  // Connection Handler
  const onConnect = useCallback(
    (connection: Connection) => {
      if (isReadOnly) return;
      if (!connection.source || !connection.target) return;
      if (connection.source === connection.target) return; // Prevent self-loop

      const newEdge: Edge<WorkflowEdgeData> = {
        id: `edge-${connection.source}-${connection.target}-${Date.now()}`,
        source: connection.source,
        target: connection.target,
        type: 'custom',
        markerEnd: {
          type: MarkerType.ArrowClosed,
          width: 14,
          height: 14,
          color: '#6366f1'
        },
        data: {
          priority: edges.filter((e) => e.source === connection.source).length + 1,
          isDefault: false
        }
      };

      setEdges((eds) => addEdge(newEdge, eds));
    },
    [isReadOnly, edges, setEdges]
  );

  // Drag and drop node placement
  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault();
      if (isReadOnly || !reactFlowInstance || !reactFlowWrapper.current) return;

      const type = event.dataTransfer.getData('application/reactflow') as WorkflowNodeType;
      if (!type) return;

      const position = reactFlowInstance.screenToFlowPosition({
        x: event.clientX,
        y: event.clientY
      });

      const uniqueCount = nodes.filter((n) => n.data.nodeType === type).length + 1;
      const nodeKey = `${type}_${uniqueCount}`;

      const newNode: Node<WorkflowNodeData> = {
        id: nodeKey,
        type,
        position,
        data: {
          nodeKey,
          name: `${type.charAt(0) + type.slice(1).toLowerCase()} Step ${uniqueCount}`,
          nodeType: type,
          configuration: getDefaultConfigForType(type)
        }
      };

      setNodes((nds) => nds.concat(newNode));
      setSelectedNodeId(nodeKey);
      setSelectedEdgeId(null);
    },
    [isReadOnly, reactFlowInstance, nodes, setNodes]
  );

  const handleAddNodeFromPalette = (type: WorkflowNodeType) => {
    if (isReadOnly) return;
    const uniqueCount = nodes.filter((n) => n.data.nodeType === type).length + 1;
    const nodeKey = `${type}_${uniqueCount}`;

    // Place near center
    const x = 350 + Math.floor(Math.random() * 80);
    const y = 200 + Math.floor(Math.random() * 80);

    const newNode: Node<WorkflowNodeData> = {
      id: nodeKey,
      type,
      position: { x, y },
      data: {
        nodeKey,
        name: `${type.charAt(0) + type.slice(1).toLowerCase()} Step ${uniqueCount}`,
        nodeType: type,
        configuration: getDefaultConfigForType(type)
      }
    };

    setNodes((nds) => nds.concat(newNode));
    setSelectedNodeId(nodeKey);
    setSelectedEdgeId(null);
  };

  const handleNodeClick = useCallback((_: React.MouseEvent, node: Node) => {
    setSelectedNodeId(node.id);
    setSelectedEdgeId(null);
  }, []);

  const handleEdgeClick = useCallback((_: React.MouseEvent, edge: Edge) => {
    setSelectedEdgeId(edge.id);
    setSelectedNodeId(null);
  }, []);

  const handlePaneClick = useCallback(() => {
    setSelectedNodeId(null);
    setSelectedEdgeId(null);
  }, []);

  // Updates from Properties Sidebar
  const handleUpdateNode = (id: string, updates: Partial<WorkflowNodeData>) => {
    setNodes((nds) =>
      nds.map((n) => {
        if (n.id === id) {
          const newKey = updates.nodeKey || n.data.nodeKey;
          return {
            ...n,
            id: newKey, // update id if key changed
            data: {
              ...n.data,
              ...updates,
              nodeKey: newKey
            }
          };
        }
        return n;
      })
    );

    // If key changed, update connected edges
    if (updates.nodeKey && updates.nodeKey !== id) {
      setEdges((eds) =>
        eds.map((e) => ({
          ...e,
          source: e.source === id ? updates.nodeKey! : e.source,
          target: e.target === id ? updates.nodeKey! : e.target
        }))
      );
      setSelectedNodeId(updates.nodeKey);
    }
  };

  const handleUpdateEdge = (id: string, updates: Partial<WorkflowEdgeData>) => {
    setEdges((eds) =>
      eds.map((e) => {
        if (e.id === id) {
          return {
            ...e,
            data: {
              ...(e.data || {}),
              ...updates
            }
          };
        }
        return e;
      })
    );
  };

  const handleDeleteNode = (id: string) => {
    if (isReadOnly) return;
    setNodes((nds) => nds.filter((n) => n.id !== id));
    setEdges((eds) => eds.filter((e) => e.source !== id && e.target !== id));
    setSelectedNodeId(null);
  };

  const handleDeleteEdge = (id: string) => {
    if (isReadOnly) return;
    setEdges((eds) => eds.filter((e) => e.id !== id));
    setSelectedEdgeId(null);
  };

  // Serialize to backend SaveWorkflowDraftDto
  const serializeDraft = (): SaveWorkflowDraftDto => {
    const draftNodes = nodes.map((n) => ({
      nodeKey: n.data.nodeKey || n.id,
      nodeType: n.data.nodeType,
      name: n.data.name || n.data.nodeKey,
      configuration: n.data.configuration || {},
      positionX: Math.round(n.position.x),
      positionY: Math.round(n.position.y)
    }));

    const draftTransitions = edges.map((e, idx) => ({
      sourceNodeKey: e.source,
      targetNodeKey: e.target,
      conditionExpression: e.data?.conditionExpression || undefined,
      priority: e.data?.priority ?? idx + 1,
      isDefault: e.data?.isDefault ?? false
    }));

    return {
      nodes: draftNodes,
      transitions: draftTransitions
    };
  };

  return (
    <div className="relative w-full h-[680px] bg-slate-50 border border-slate-200 rounded-2xl overflow-hidden shadow-inner flex">
      {/* Canvas Area */}
      <div ref={reactFlowWrapper} className="flex-1 h-full relative">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange as any}
          onEdgesChange={onEdgesChange as any}
          onConnect={onConnect}
          onInit={setReactFlowInstance}
          onDrop={onDrop}
          onDragOver={onDragOver}
          onNodeClick={handleNodeClick}
          onEdgeClick={handleEdgeClick}
          onPaneClick={handlePaneClick}
          nodeTypes={nodeTypes}
          edgeTypes={edgeTypes}
          fitView
          fitViewOptions={{ padding: 0.2 }}
          minZoom={0.2}
          maxZoom={1.8}
          deleteKeyCode={isReadOnly ? null : ['Delete', 'Backspace']}
        >
          <Background variant={BackgroundVariant.Dots} gap={16} size={1} color="#cbd5e1" />
          <Controls className="!bg-white !border !border-slate-200 !rounded-xl !shadow-md" />
          <MiniMap
            className="!bg-white/90 !border !border-slate-200 !rounded-xl !shadow-md !overflow-hidden"
            nodeColor={(node) => {
              switch (node.type) {
                case 'START':
                  return '#10b981';
                case 'APPROVAL':
                  return '#f59e0b';
                case 'CONDITION':
                  return '#0ea5e9';
                case 'TASK':
                  return '#a855f7';
                case 'END':
                  return '#f43f5e';
                default:
                  return '#94a3b8';
              }
            }}
          />

          {/* Top-Left Palette Panel */}
          <Panel position="top-left" className="m-3">
            <NodePalette onAddNode={handleAddNodeFromPalette} isReadOnly={isReadOnly} />
          </Panel>

          {/* Bottom Floating Save Button for Drafts */}
          {!isReadOnly && (
            <Panel position="bottom-center" className="mb-4">
              <button
                type="button"
                disabled={isSaving}
                onClick={() => onSaveDraft(serializeDraft())}
                className="flex items-center gap-2 px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 active:scale-95 text-white text-xs font-semibold rounded-xl shadow-lg hover:shadow-indigo-500/25 transition-all disabled:opacity-50"
              >
                <span>{isSaving ? 'Saving Draft...' : '💾 Save Workflow Draft'}</span>
              </button>
            </Panel>
          )}
        </ReactFlow>
      </div>

      {/* Right Properties Panel */}
      {(selectedNode || selectedEdge) && (
        <NodeConfigSidebar
          selectedNode={selectedNode}
          selectedEdge={selectedEdge}
          onUpdateNode={handleUpdateNode}
          onUpdateEdge={handleUpdateEdge}
          onDeleteNode={handleDeleteNode}
          onDeleteEdge={handleDeleteEdge}
          onClose={() => {
            setSelectedNodeId(null);
            setSelectedEdgeId(null);
          }}
          isReadOnly={isReadOnly}
        />
      )}
    </div>
  );
};

function getDefaultConfigForType(type: WorkflowNodeType): Record<string, any> {
  switch (type) {
    case 'START':
      return {};
    case 'APPROVAL':
      return {
        approverType: 'MANAGER',
        timeoutHours: 48,
        approvalPolicy: 'SINGLE_APPROVER'
      };
    case 'CONDITION':
      return {
        field: 'payload.estimatedAmount'
      };
    case 'TASK':
      return {
        taskQueue: 'IT_OPERATIONS',
        assignmentStrategy: 'DIRECT_CLAIM',
        slaHours: 120,
        priority: 'MEDIUM'
      };
    case 'END':
      return {
        outcome: 'COMPLETED'
      };
    default:
      return {};
  }
}
