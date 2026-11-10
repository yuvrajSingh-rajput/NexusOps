export type WorkflowNodeType = 'START' | 'APPROVAL' | 'CONDITION' | 'TASK' | 'END';

export type NodeExecutionStatus = 'COMPLETED' | 'RUNNING' | 'WAITING' | 'PENDING' | 'FAILED' | 'SKIPPED';

export interface WorkflowNodeData extends Record<string, unknown> {
  nodeKey: string;
  name: string;
  nodeType: WorkflowNodeType;
  configuration: Record<string, any>;
  isPublished?: boolean;
  hasErrors?: boolean;
  errorMessage?: string;
  executionStatus?: NodeExecutionStatus;
  isCurrentExecutionNode?: boolean;
  onUpdate?: (updates: Partial<WorkflowNodeData>) => void;
}

export interface WorkflowEdgeData extends Record<string, unknown> {
  conditionExpression?: Record<string, any>;
  priority?: number;
  isDefault?: boolean;
  isActivePath?: boolean;
  onUpdate?: (updates: Partial<WorkflowEdgeData>) => void;
}

export interface WorkflowExecutionOverlay {
  currentNodeKey?: string;
  nodeStatuses?: Record<string, NodeExecutionStatus>;
  completedNodeKeys?: string[];
  activePathEdgeKeys?: string[];
}

export interface WorkflowVersionSummary {
  id: string;
  versionNumber: number;
  status: 'DRAFT' | 'PUBLISHED' | 'RETIRED';
  publishedAt?: string;
  createdAt: string;
}

export interface WorkflowValidationResult {
  valid: boolean;
  errors: string[];
  warnings: string[];
}

export interface SaveWorkflowNodeDto {
  id?: string;
  nodeKey: string;
  nodeType: WorkflowNodeType;
  name: string;
  configuration: Record<string, any>;
  positionX: number;
  positionY: number;
}

export interface SaveWorkflowTransitionDto {
  id?: string;
  sourceNodeKey: string;
  targetNodeKey: string;
  conditionExpression?: Record<string, any>;
  priority: number;
  isDefault: boolean;
}

export interface SaveWorkflowDraftDto {
  nodes: SaveWorkflowNodeDto[];
  transitions: SaveWorkflowTransitionDto[];
}
