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
