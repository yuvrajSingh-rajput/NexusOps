import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { ApiResponse } from '@/types/api';

export const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

// Request Interceptor: Attach JWT & Correlation ID
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('nexusops_access_token');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // Attach correlation ID if missing
    if (config.headers && !config.headers['X-Correlation-Id']) {
      config.headers['X-Correlation-Id'] = crypto.randomUUID();
    }

    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor: Parse Api Response or format errors
apiClient.interceptors.response.use(
  (response) => response.data,
  (error: AxiosError<ApiResponse<unknown>>) => {
    if (error.response?.data?.error) {
      return Promise.reject(error.response.data.error);
    }
    return Promise.reject({
      code: 'NETWORK_ERROR',
      message: error.message || 'Unable to connect to the server.',
    });
  }
);

