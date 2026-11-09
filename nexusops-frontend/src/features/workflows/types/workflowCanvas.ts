export type WorkflowNodeType = 'START' | 'APPROVAL' | 'CONDITION' | 'TASK' | 'END';

export interface WorkflowNodeData extends Record<string, unknown> {
  nodeKey: string;
  name: string;
  nodeType: WorkflowNodeType;
  configuration: Record<string, any>;
  isPublished?: boolean;
  hasErrors?: boolean;
  errorMessage?: string;
  onUpdate?: (updates: Partial<WorkflowNodeData>) => void;
}

export interface WorkflowEdgeData extends Record<string, unknown> {
  conditionExpression?: Record<string, any>;
  priority?: number;
  isDefault?: boolean;
  onUpdate?: (updates: Partial<WorkflowEdgeData>) => void;
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

