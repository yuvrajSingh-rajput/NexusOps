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
