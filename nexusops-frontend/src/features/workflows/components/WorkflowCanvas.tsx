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

import {
  Sparkles,
  Maximize2,
  HelpCircle,
  X
} from 'lucide-react';

import { StartNode, ApprovalNode, ConditionNode, TaskNode, EndNode } from './CustomNodes';
import { CustomEdge } from './CustomEdge';
import { NodePalette } from './NodePalette';
import { NodeConfigSidebar } from './NodeConfigSidebar';
import { getLayoutedElements, hasValidSavedPositions } from '../utils/layoutUtils';
import {
  WorkflowNodeData,
  WorkflowEdgeData,
  WorkflowNodeType,
  SaveWorkflowDraftDto,
  WorkflowExecutionOverlay
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
  executionOverlay?: WorkflowExecutionOverlay;
}

export const WorkflowCanvas: React.FC<WorkflowCanvasProps> = ({
  initialNodes,
  initialTransitions,
  isReadOnly,
  onSaveDraft,
  isSaving,
  executionOverlay
}) => {
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const [reactFlowInstance, setReactFlowInstance] = useState<ReactFlowInstance | null>(null);
  const [showLegend, setShowLegend] = useState(false);

  // Convert initial backend nodes and transitions into layouted React Flow elements
  const { initialFormattedNodes, initialFormattedEdges } = useMemo(() => {
    const rawNodes: Node<WorkflowNodeData>[] = (initialNodes || []).map((n) => {
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
          isPublished: isReadOnly,
          executionStatus: executionOverlay?.nodeStatuses?.[n.nodeKey],
          isCurrentExecutionNode: executionOverlay?.currentNodeKey === n.nodeKey
        }
      };
    });

    // Build node id to nodeKey map
    const idToKeyMap: Record<string, string> = {};
    (initialNodes || []).forEach((n) => {
      if (n.id) idToKeyMap[n.id] = n.nodeKey;
    });

    const rawEdges: Edge<WorkflowEdgeData>[] = (initialTransitions || []).map((t, idx) => {
      const sourceKey = t.sourceNodeKey || idToKeyMap[t.sourceNodeId] || t.sourceNodeId;
      const targetKey = t.targetNodeKey || idToKeyMap[t.targetNodeId] || t.targetNodeId;
      const edgeId = t.id || `edge-${sourceKey}-${targetKey}-${idx}`;

      const isActive =
        executionOverlay?.activePathEdgeKeys?.includes(edgeId) ||
        (executionOverlay?.nodeStatuses?.[sourceKey] === 'COMPLETED' &&
          (executionOverlay?.nodeStatuses?.[targetKey] === 'COMPLETED' ||
            executionOverlay?.currentNodeKey === targetKey));

      return {
        id: edgeId,
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
          priority: t.priority ?? idx + 1,
          isDefault: t.isDefault ?? false,
          isActivePath: Boolean(isActive)
        }
      };
    });

    // Auto-layout left-to-right using Dagre if no valid saved positions exist
    if (!hasValidSavedPositions(initialNodes) && rawNodes.length > 0) {
      const layouted = getLayoutedElements(rawNodes, rawEdges, { direction: 'LR', rankSep: 100, nodeSep: 50 });
      return {
        initialFormattedNodes: layouted.nodes,
        initialFormattedEdges: layouted.edges
      };
    }

    return {
      initialFormattedNodes: rawNodes,
      initialFormattedEdges: rawEdges
    };
  }, [initialNodes, initialTransitions, isReadOnly, executionOverlay]);

  const [nodes, setNodes, onNodesChange] = useNodesState<Node<WorkflowNodeData>>(initialFormattedNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge<WorkflowEdgeData>>(initialFormattedEdges);

  // Sync state if props change (e.g. version switch)
  useEffect(() => {
    setNodes(initialFormattedNodes);
    setEdges(initialFormattedEdges);
    window.requestAnimationFrame(() => {
      reactFlowInstance?.fitView({ padding: 0.25, duration: 350 });
    });
  }, [initialFormattedNodes, initialFormattedEdges, reactFlowInstance, setNodes, setEdges]);

  // Selected state
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null);

  const selectedNode = useMemo(() => {
    return nodes.find((n) => n.id === selectedNodeId) || null;
  }, [nodes, selectedNodeId]);

  const selectedEdge = useMemo(() => {
    return edges.find((e) => e.id === selectedEdgeId) || null;
  }, [edges, selectedEdgeId]);

  // Handle Tidy Layout (manual click or auto re-run)
  const handleTidyLayout = useCallback(() => {
    const layouted = getLayoutedElements(nodes, edges, { direction: 'LR', rankSep: 100, nodeSep: 50 });
    setNodes(layouted.nodes);
    setEdges(layouted.edges);
    window.requestAnimationFrame(() => {
      reactFlowInstance?.fitView({ padding: 0.25, duration: 400 });
    });
  }, [nodes, edges, reactFlowInstance, setNodes, setEdges]);

  // Handle Fit View
  const handleFitView = useCallback(() => {
    reactFlowInstance?.fitView({ padding: 0.25, duration: 300 });
  }, [reactFlowInstance]);

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
            id: newKey,
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
    <div className="relative w-full h-[700px] bg-slate-50 border border-slate-200 rounded-2xl overflow-hidden shadow-inner flex">
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
          fitViewOptions={{ padding: 0.25 }}
          minZoom={0.2}
          maxZoom={1.8}
          deleteKeyCode={isReadOnly ? null : ['Delete', 'Backspace']}
        >
          <Background variant={BackgroundVariant.Dots} gap={20} size={1.2} color="#cbd5e1" />
          <Controls className="!bg-white !border !border-slate-200 !rounded-xl !shadow-md" showInteractive={false} />
          <MiniMap
            className="hidden md:block !bg-white/95 !border !border-slate-200 !rounded-xl !shadow-md !overflow-hidden"
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
            maskColor="rgba(241, 245, 249, 0.7)"
          />

          {/* Top-Left Palette Panel */}
          <Panel position="top-left" className="m-3">
            <NodePalette onAddNode={handleAddNodeFromPalette} isReadOnly={isReadOnly} />
          </Panel>

          {/* Top-Right Canvas Control Toolbar */}
          <Panel position="top-right" className="m-3 flex items-center gap-2">
            <button
              type="button"
              onClick={handleTidyLayout}
              title="Organize canvas left-to-right with Dagre auto-layout"
              className="flex items-center gap-1.5 px-3 py-1.5 bg-white hover:bg-slate-50 text-slate-700 hover:text-indigo-600 border border-slate-200 rounded-xl shadow-xs text-xs font-semibold transition-all hover:shadow active:scale-95"
            >
              <Sparkles className="h-3.5 w-3.5 text-indigo-600" />
              <span>Tidy Layout</span>
            </button>

            <button
              type="button"
              onClick={handleFitView}
              title="Fit workflow into view"
              className="flex items-center gap-1.5 px-3 py-1.5 bg-white hover:bg-slate-50 text-slate-700 hover:text-indigo-600 border border-slate-200 rounded-xl shadow-xs text-xs font-semibold transition-all hover:shadow active:scale-95"
            >
              <Maximize2 className="h-3.5 w-3.5 text-slate-500" />
              <span>Fit View</span>
            </button>

            <button
              type="button"
              onClick={() => setShowLegend((prev) => !prev)}
              title="Toggle Workflow Legend"
              className={`flex items-center gap-1.5 px-3 py-1.5 border rounded-xl shadow-xs text-xs font-semibold transition-all hover:shadow active:scale-95 ${
                showLegend
                  ? 'bg-indigo-50 border-indigo-200 text-indigo-700'
                  : 'bg-white hover:bg-slate-50 border-slate-200 text-slate-700 hover:text-indigo-600'
              }`}
            >
              <HelpCircle className="h-3.5 w-3.5 text-indigo-500" />
              <span>Legend</span>
            </button>
          </Panel>

          {/* Legend Popover in Bottom-Left Panel */}
          {showLegend && (
            <Panel position="bottom-left" className="m-3">
              <div className="bg-white/95 backdrop-blur-md border border-slate-200 rounded-2xl p-4 shadow-xl text-xs w-72 space-y-3 animate-fadeIn">
                <div className="flex items-center justify-between pb-2 border-b border-slate-100">
                  <span className="font-bold text-slate-900 flex items-center gap-1.5">
                    <HelpCircle className="h-3.5 w-3.5 text-indigo-600" />
                    Workflow Legend
                  </span>
                  <button
                    type="button"
                    onClick={() => setShowLegend(false)}
                    className="text-slate-400 hover:text-slate-600 p-0.5 rounded"
                  >
                    <X className="h-3.5 w-3.5" />
                  </button>
                </div>

                <div>
                  <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400 mb-1.5">Node Types</div>
                  <div className="grid grid-cols-2 gap-1.5 text-[11px]">
                    <div className="flex items-center gap-1.5">
                      <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 shrink-0" />
                      <span className="font-medium text-slate-700">Start</span>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <span className="w-2.5 h-2.5 rounded-full bg-amber-500 shrink-0" />
                      <span className="font-medium text-slate-700">Approval</span>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <span className="w-2.5 h-2.5 rounded-full bg-sky-500 shrink-0" />
                      <span className="font-medium text-slate-700">Condition</span>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <span className="w-2.5 h-2.5 rounded-full bg-purple-500 shrink-0" />
                      <span className="font-medium text-slate-700">Task</span>
                    </div>
                    <div className="flex items-center gap-1.5 col-span-2">
                      <span className="w-2.5 h-2.5 rounded-full bg-rose-500 shrink-0" />
                      <span className="font-medium text-slate-700">End (Complete / Reject)</span>
                    </div>
                  </div>
                </div>

                <div className="pt-2 border-t border-slate-100">
                  <div className="text-[10px] font-bold uppercase tracking-wider text-slate-400 mb-1.5">Transition Routes</div>
                  <div className="space-y-1.5 text-[11px]">
                    <div className="flex items-center gap-2">
                      <span className="w-4 h-0.5 bg-emerald-500 shrink-0 rounded" />
                      <span className="text-slate-700 font-medium">Approved / Success</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="w-4 h-0.5 bg-rose-500 shrink-0 rounded" />
                      <span className="text-slate-700 font-medium">Rejected</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="w-4 h-0.5 bg-blue-500 shrink-0 rounded" />
                      <span className="text-slate-700 font-medium">Conditional Predicate</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="w-4 h-0.5 border-b-2 border-dashed border-slate-400 shrink-0" />
                      <span className="text-slate-700 font-medium">Default Fallback Route</span>
                    </div>
                  </div>
                </div>
              </div>
            </Panel>
          )}

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
          edges={edges}
          nodes={nodes}
          onSelectEdge={(id) => {
            setSelectedEdgeId(id);
            setSelectedNodeId(null);
          }}
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
