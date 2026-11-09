import React, { useEffect, useState } from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuthStore } from '@/features/auth/authStore';
import { Sparkles } from 'lucide-react';

export const ProtectedRoute: React.FC = () => {
  const { isAuthenticated, token, user, loadCurrentUser } = useAuthStore();
  const [checking, setChecking] = useState(!user && !!token);

  useEffect(() => {
    const initAuth = async () => {
      if (token && !user) {
        try {
          await loadCurrentUser();
        } catch {
          // Token expired or invalid
        }
      }
      setChecking(false);
    };

    initAuth();
  }, [token, user, loadCurrentUser]);

  if (checking) {
    return (
      <div className="h-screen w-screen flex flex-col items-center justify-center bg-slate-900 text-slate-300">
        <div className="h-10 w-10 rounded-xl bg-indigo-600 flex items-center justify-center text-white shadow-lg animate-pulse mb-3">
          <Sparkles className="h-5 w-5" />
        </div>
        <p className="text-xs text-slate-400 font-medium">Verifying organization credentials...</p>
      </div>
    );
  }

  if (!isAuthenticated && !token) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
};
