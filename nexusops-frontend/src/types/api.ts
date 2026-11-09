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

