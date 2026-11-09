export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: ErrorResponse;
  timestamp: string;
}

export interface ErrorResponse {
  code: string;
  message: string;
  details?: ErrorDetail[];
}

export interface ErrorDetail {
  field: string;
  issue: string;
}

export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  isLast: boolean;
}

export interface SystemHealth {
  status: string;
  service: string;
  version: string;
}

export interface AuthResponseDto {
  accessToken: string;
  refreshToken?: string;
  userId: string;
  organizationId: string;
  email: string;
  fullName: string;
  roles: string[];
  permissions: string[];
}

export interface UserOrgSummaryDto {
  organizationId: string;
  organizationName: string;
  organizationSlug: string;
  isCurrent: boolean;
}

export interface UserMeResponseDto {
  userId: string;
  organizationId: string;
  email: string;
  firstName: string;
  lastName: string;
  jobTitle?: string;
  roles: string[];
  permissions: string[];
  availableOrganizations: UserOrgSummaryDto[];
}

export interface DepartmentResponseDto {
  id: string;
  organizationId: string;
  code: string;
  name: string;
  parentDepartmentId?: string;
  parentDepartmentName?: string;
  headMembershipId?: string;
}

export interface EmployeeResponseDto {
  membershipId: string;
  userId: string;
  email: string;
  firstName: string;
  lastName: string;
  fullName: string;
  jobTitle?: string;
  departmentId?: string;
  departmentName?: string;
  managerMembershipId?: string;
  status: string;
  roles: string[];
}

export type RequestPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export type RequestStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'IN_REVIEW'
  | 'WAITING_FOR_INFORMATION'
  | 'APPROVED'
  | 'IN_PROGRESS'
  | 'ON_HOLD'
  | 'COMPLETED'
  | 'REJECTED'
  | 'CANCELLED';

export type CommentVisibility = 'INTERNAL' | 'REQUESTER_VISIBLE';

export interface RequestCategoryResponseDto {
  id: string;
  key: string;
  name: string;
  description?: string;
  icon?: string;
  requiresWorkflow: boolean;
  payloadSchema: Record<string, any>;
  active: boolean;
}

export interface RequestResponseDto {
  id: string;
  requestNumber: string;
  categoryId: string;
  categoryKey: string;
  categoryName: string;
  requesterMembershipId: string;
  requesterName: string;
  requesterEmail: string;
  departmentCode?: string;
  departmentName?: string;
  title: string;
  description?: string;
  priority: RequestPriority;
  status: RequestStatus;
  payload: Record<string, any>;
  submittedAt?: string;
  completedAt?: string;
  createdAt: string;
}

export interface RequestCommentResponseDto {
  id: string;
  authorMembershipId: string;
  authorName: string;
  authorEmail: string;
  content: string;
  visibility: CommentVisibility;
  createdAt: string;
}

export interface CreateRequestDto {
  categoryId: string;
  title: string;
  description?: string;
  priority: RequestPriority;
  payload: Record<string, any>;
}

export interface AddCommentDto {
  content: string;
  visibility: CommentVisibility;
}

// ==============================================================================
// Workflow Engine Types
// ==============================================================================

export type NodeType = 'START' | 'END' | 'APPROVAL' | 'CONDITION' | 'TASK';

export type WorkflowVersionStatus = 'DRAFT' | 'PUBLISHED' | 'RETIRED';

export type WorkflowStatus =
  | 'CREATED'
  | 'RUNNING'
  | 'WAITING'
  | 'SUSPENDED'
  | 'COMPLETED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'FAILED';

export type NodeInstanceStatus =
  | 'PENDING'
  | 'READY'
  | 'RUNNING'
  | 'WAITING'
  | 'COMPLETED'
  | 'FAILED'
  | 'SKIPPED'
  | 'CANCELLED';

export interface WorkflowNodeDto {
  id: string;
  nodeKey: string;
  nodeType: NodeType;
  title: string;
  configuration: Record<string, any>;
  positionX: number;
  positionY: number;
}

export interface WorkflowTransitionDto {
  id: string;
  sourceNodeId: string;
  targetNodeId: string;
  conditionExpression?: Record<string, any>;
  priority: number;
  isDefault: boolean;
}

export interface WorkflowDefinitionResponseDto {
  id: string;
  organizationId: string;
  categoryId: string;
  code: string;
  name: string;
  description?: string;
  active: boolean;
  latestVersionNumber?: number;
  latestVersionStatus?: WorkflowVersionStatus;
  createdAt: string;
}

export interface WorkflowVersionResponseDto {
  id: string;
  workflowDefinitionId: string;
  versionNumber: number;
  status: WorkflowVersionStatus;
  publishedAt?: string;
  nodes: WorkflowNodeDto[];
  transitions: WorkflowTransitionDto[];
  createdAt: string;
}

export interface WorkflowInstanceResponseDto {
  id: string;
  organizationId: string;
  workflowVersionId: string;
  requestId: string;
  attemptNumber: number;
  status: WorkflowStatus;
  currentNodeKey?: string;
  contextData: Record<string, any>;
  startedAt: string;
  completedAt?: string;
}

export interface NodeInstanceResponseDto {
  id: string;
  workflowInstanceId: string;
  nodeId: string;
  nodeKey: string;
  title: string;
  nodeType: NodeType;
  status: NodeInstanceStatus;
  result?: Record<string, any>;
  enteredAt: string;
  exitedAt?: string;
}

// ==============================================================================
// APPROVAL ENGINE TYPES
// ==============================================================================

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'REQUESTED_INFO' | 'EXPIRED' | 'CANCELLED';
export type ApprovalActionType = 'APPROVE' | 'REJECT' | 'REQUEST_INFORMATION' | 'DELEGATE' | 'REASSIGN';
export type ApproverType = 'DIRECT_MEMBERSHIP' | 'MANAGER' | 'DEPT_HEAD' | 'ROLE';

export interface ApprovalActionResponseDto {
  id: string;
  actorMembershipId: string;
  actorName: string;
  action: ApprovalActionType;
  comments?: string;
  createdAt: string;
}

export interface ApprovalInstanceResponseDto {
  id: string;
  organizationId: string;
  nodeInstanceId: string;
  requestId: string;
  requestNumber: string;
  requestTitle: string;
  requesterName: string;
  departmentName: string;
  categoryName: string;
  requestPayload: Record<string, any>;
  assignedMembershipId?: string;
  assignedRoleId?: string;
  approverRoleName: string;
  approverType: ApproverType;
  status: ApprovalStatus;
  dueAt?: string;
  createdAt: string;
  actions: ApprovalActionResponseDto[];
}

export interface ApprovalDecisionDto {
  action: ApprovalActionType;
  comments?: string;
  delegateToMembershipId?: string;
}

// ==============================================================================
// TASK MANAGEMENT & FULFILLMENT QUEUE TYPES
// ==============================================================================

export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'BLOCKED' | 'COMPLETED' | 'CANCELLED';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export interface TaskCommentResponseDto {
  id: string;
  authorMembershipId: string;
  authorName: string;
  content: string;
  createdAt: string;
}

export interface TaskHistoryResponseDto {
  id: string;
  actorMembershipId: string;
  actorName: string;
  action: string;
  oldState: Record<string, any>;
  newState: Record<string, any>;
  createdAt: string;
}

export interface TaskResponseDto {
  id: string;
  organizationId: string;
  taskNumber: string;
  title: string;
  description?: string;
  requestId?: string;
  requestNumber?: string;
  requestTitle?: string;
  workflowInstanceId?: string;
  nodeInstanceId?: string;
  teamId?: string;
  assigneeMembershipId?: string;
  assigneeName: string;
  status: TaskStatus;
  priority: TaskPriority;
  payload: Record<string, any>;
  outputData: Record<string, any>;
  dueAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
  comments: TaskCommentResponseDto[];
  history: TaskHistoryResponseDto[];
}

export interface CompleteTaskDto {
  outputData?: Record<string, any>;
  notes?: string;
}

export interface UpdateTaskStatusDto {
  status: TaskStatus;
  notes?: string;
}

export interface AddTaskCommentDto {
  content: string;
}

// ----------------------------------------------------------------------------
// Knowledge Base & RAG Semantic Search
// ----------------------------------------------------------------------------

export type DocumentAccessScope = 'ALL' | 'DEPARTMENT' | 'ROLE';
export type DocumentProcessingStatus = 'UPLOADED' | 'PROCESSING' | 'INDEXED' | 'FAILED' | 'RETIRED';

export interface DocumentResponseDto {
  id: string;
  organizationId: string;
  title: string;
  description?: string;
  category: string;
  accessScope: DocumentAccessScope;
  targetScopeId?: string;
  createdByMembershipId?: string;
  createdAt: string;
  updatedAt: string;
  latestVersionNumber: number;
  latestStatus: DocumentProcessingStatus;
  latestFileName: string;
  latestFileSizeBytes: number;
  totalChunks: number;
}

export interface DocumentVersionResponseDto {
  id: string;
  documentId: string;
  versionNumber: number;
  fileName: string;
  fileSizeBytes: number;
  mimeType: string;
  processingStatus: DocumentProcessingStatus;
  errorMessage?: string;
  chunkCount: number;
  createdAt: string;
  processedAt?: string;
}

export interface SearchResultDto {
  chunkId: string;
  documentId: string;
  documentTitle: string;
  pageNumber?: number;
  chunkIndex: number;
  similarityScore: number;
  content: string;
  citation: string;
}

export interface SemanticSearchRequestDto {
  query: string;
  limit?: number;
  minScore?: number;
}

// ----------------------------------------------------------------------------
// Nexus AI Assistant & Governed Tool Registry Types
// ----------------------------------------------------------------------------

export type AiMessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM' | 'TOOL';

export type ToolExecutionStatus =
  | 'PROPOSED'
  | 'AWAITING_CONFIRMATION'
  | 'CONFIRMED'
  | 'EXECUTING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'REJECTED'
  | 'EXPIRED';

export interface ActionProposalDto {
  actionId: string;
  toolName: string;
  title: string;
  description: string;
  parameters: Record<string, any>;
  expiresAt: string;
  status: ToolExecutionStatus;
}

export interface ChatCitationDto {
  documentId: string;
  documentTitle: string;
  pageNumber?: number;
  similarityScore: number;
  snippet: string;
}

export interface ChatResponseDto {
  conversationId: string;
  messageId: string;
  reply: string;
  citations: ChatCitationDto[];
  proposal?: ActionProposalDto | null;
  toolUsed?: string | null;
}

export interface ChatRequestDto {
  conversationId?: string | null;
  message: string;
}

export interface ConfirmActionDto {
  confirm: boolean;
  notes?: string;
}

export interface ConversationSummaryDto {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  messageCount: number;
}

export interface ChatMessageDto {
  id: string;
  role: AiMessageRole;
  content: string;
  citations: ChatCitationDto[];
  proposal?: ActionProposalDto | null;
  createdAt: string;
}

export interface NotificationResponseDto {
  id: string;
  recipientMembershipId: string;
  eventType: string;
  title: string;
  message: string;
  linkUrl?: string;
  read: boolean;
  createdAt: string;
}

export interface AuditLogResponseDto {
  id: string;
  actorMembershipId?: string;
  eventType: string;
  aggregateType: string;
  aggregateId: string;
  correlationId?: string;
  beforeState?: string;
  afterState?: string;
  createdAt: string;
}






