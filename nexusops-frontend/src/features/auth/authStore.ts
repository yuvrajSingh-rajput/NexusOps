import { create } from 'zustand';
import { UserMeResponseDto } from '@/types/api';
import { apiClient } from '@/lib/api-client';

interface AuthState {
  token: string | null;
  user: UserMeResponseDto | null;
  isAuthenticated: boolean;
  setAuth: (token: string, user: UserMeResponseDto) => void;
  logout: () => void;
  loadCurrentUser: () => Promise<void>;
  switchOrganization: (targetOrgId: string) => Promise<void>;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  token: localStorage.getItem('nexusops_access_token'),
  user: null,
  isAuthenticated: !!localStorage.getItem('nexusops_access_token'),

  setAuth: (token: string, user: UserMeResponseDto) => {
    localStorage.setItem('nexusops_access_token', token);
    set({ token, user, isAuthenticated: true });
  },

  logout: () => {
    apiClient.post('/auth/logout').catch(() => {});
    localStorage.removeItem('nexusops_access_token');
    set({ token: null, user: null, isAuthenticated: false });
  },

  loadCurrentUser: async () => {
    try {
      const response = await apiClient.get<unknown, { success: boolean; data: UserMeResponseDto }>('/auth/me');
      if (response.data) {
        set({ user: response.data, isAuthenticated: true });
      }
    } catch {
      get().logout();
    }
  },

  switchOrganization: async (targetOrgId: string) => {
    const res = await apiClient.post<unknown, { success: boolean; data: { accessToken: string } }>('/auth/switch-org', {
      targetOrganizationId: targetOrgId,
    });
    if (res.data?.accessToken) {
      localStorage.setItem('nexusops_access_token', res.data.accessToken);
      set({ token: res.data.accessToken });
      await get().loadCurrentUser();
    }
  },
}));
