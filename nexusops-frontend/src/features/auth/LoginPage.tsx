import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Sparkles, Lock, Mail, ArrowRight, AlertCircle, UserCheck, ShieldCheck, Cpu, Wallet, Wrench, Building } from 'lucide-react';
import { apiClient } from '@/lib/api-client';
import { ApiResponse, AuthResponseDto, UserMeResponseDto } from '@/types/api';
import { useAuthStore } from './authStore';

interface DemoPersona {
  role: string;
  name: string;
  email: string;
  title: string;
  desc: string;
  badge: string;
  badgeColor: string;
  icon: React.ReactNode;
}

const DEMO_PERSONAS: DemoPersona[] = [
  {
    role: 'EMPLOYEE',
    name: 'Alice Chen',
    email: 'alice@acme.com',
    title: 'Senior Software Engineer',
    desc: 'Submits requests & questions Nexus AI',
    badge: 'Employee',
    badgeColor: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
    icon: <Cpu className="h-4 w-4 text-emerald-400" />,
  },
  {
    role: 'MANAGER',
    name: 'Bob Martinez',
    email: 'bob@acme.com',
    title: 'Engineering Manager',
    desc: 'Department level 1 approver',
    badge: 'Manager',
    badgeColor: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
    icon: <UserCheck className="h-4 w-4 text-blue-400" />,
  },
  {
    role: 'FINANCE',
    name: 'Carol Davis',
    email: 'carol@acme.com',
    title: 'Finance Controller',
    desc: 'Threshold & CapEx budget approver',
    badge: 'Finance',
    badgeColor: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
    icon: <Wallet className="h-4 w-4 text-amber-400" />,
  },
  {
    role: 'OPERATOR',
    name: 'Dave Wilson',
    email: 'dave@acme.com',
    title: 'Senior IT Specialist',
    desc: 'Fulfills hardware & IT tasks',
    badge: 'IT Operator',
    badgeColor: 'bg-purple-500/10 text-purple-400 border-purple-500/20',
    icon: <Wrench className="h-4 w-4 text-purple-400" />,
  },
  {
    role: 'ORG_ADMIN',
    name: 'Eve Vance',
    email: 'eve@acme.com',
    title: 'VP Operations & Admin',
    desc: 'Full visibility, analytics & audit trail',
    badge: 'Admin',
    badgeColor: 'bg-rose-500/10 text-rose-400 border-rose-500/20',
    icon: <ShieldCheck className="h-4 w-4 text-rose-400" />,
  },
  {
    role: 'ISOLATED_TENANT',
    name: 'Frank Grimes',
    email: 'frank@globex.com',
    title: 'Nuclear Safety Tech (Globex)',
    desc: 'Verifies cross-tenant isolation',
    badge: 'Globex Corp',
    badgeColor: 'bg-slate-500/10 text-slate-400 border-slate-500/20',
    icon: <Building className="h-4 w-4 text-slate-400" />,
  },
];

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const performLogin = async (loginEmail: string, loginPass: string) => {
    setError(null);
    setLoading(true);

    try {
      const res = await apiClient.post<unknown, ApiResponse<AuthResponseDto>>('/auth/login', {
        email: loginEmail,
        password: loginPass,
      });

      if (res.data?.accessToken) {
        localStorage.setItem('nexusops_access_token', res.data.accessToken);
        // Fetch User Me profile
        const meRes = await apiClient.get<unknown, ApiResponse<UserMeResponseDto>>('/auth/me');
        if (meRes.data) {
          setAuth(res.data.accessToken, meRes.data);
          navigate('/');
        }
      }
    } catch (err: unknown) {
      const errorObj = err as { message?: string };
      setError(errorObj.message || 'Invalid email or password. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    await performLogin(email, password);
  };

  const handleSelectPersona = async (persona: DemoPersona) => {
    setEmail(persona.email);
    setPassword('Password123!');
    await performLogin(persona.email, 'Password123!');
  };

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col justify-center py-10 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-2xl text-center">
        <div className="mx-auto h-12 w-12 rounded-xl bg-indigo-600 flex items-center justify-center text-white shadow-lg shadow-indigo-500/30">
          <Sparkles className="h-7 w-7" />
        </div>
        <h2 className="mt-4 text-3xl font-extrabold text-white tracking-tight">NexusOps</h2>
        <p className="mt-1 text-sm text-slate-400">Intelligent Enterprise Operations Portal</p>
      </div>

      <div className="mt-6 sm:mx-auto sm:w-full sm:max-w-2xl">
        {/* Quick Demo Personas Panel */}
        <div className="mb-6 bg-slate-800/90 rounded-2xl border border-slate-700/80 p-5 shadow-xl">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <span className="flex h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-300">
                1-Click Evaluator Personas (Pre-Seeded)
              </h3>
            </div>
            <span className="text-[11px] text-slate-400 font-medium">Default Password: Password123!</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2.5">
            {DEMO_PERSONAS.map((p) => (
              <button
                key={p.email}
                type="button"
                onClick={() => handleSelectPersona(p)}
                disabled={loading}
                className="group flex flex-col items-start p-3 rounded-xl bg-slate-900/60 border border-slate-700 hover:border-indigo-500/60 hover:bg-slate-900 transition-all text-left shadow-sm hover:shadow-indigo-500/10 disabled:opacity-50"
              >
                <div className="flex items-center justify-between w-full mb-1">
                  <div className="flex items-center gap-1.5">
                    {p.icon}
                    <span className="text-xs font-semibold text-white group-hover:text-indigo-300 transition-colors">
                      {p.name}
                    </span>
                  </div>
                  <span className={`text-[10px] px-1.5 py-0.5 rounded-full border font-medium ${p.badgeColor}`}>
                    {p.badge}
                  </span>
                </div>
                <span className="text-[11px] text-slate-300 font-medium line-clamp-1">{p.title}</span>
                <span className="text-[10px] text-slate-500 mt-0.5 line-clamp-1">{p.desc}</span>
              </button>
            ))}
          </div>
        </div>

        {/* Standard Credentials Form */}
        <div className="bg-slate-800 py-6 px-6 shadow-xl rounded-2xl border border-slate-700/80 sm:px-10">
          <form className="space-y-4" onSubmit={handleSubmit}>
            {error && (
              <div className="p-3 rounded-lg bg-rose-500/10 border border-rose-500/30 text-rose-400 text-xs flex items-center gap-2">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <div>
              <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider">
                Work Email
              </label>
              <div className="mt-1 relative rounded-lg shadow-sm">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <Mail className="h-4 w-4 text-slate-400" />
                </div>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="alice@acme.com"
                  className="block w-full pl-9 pr-3 py-2 text-sm bg-slate-900/60 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider">
                Password
              </label>
              <div className="mt-1 relative rounded-lg shadow-sm">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                  <Lock className="h-4 w-4 text-slate-400" />
                </div>
                <input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••••••"
                  className="block w-full pl-9 pr-3 py-2 text-sm bg-slate-900/60 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full flex justify-center items-center gap-2 py-2.5 px-4 border border-transparent rounded-lg shadow-md text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-500 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 transition-colors disabled:opacity-50"
            >
              <span>{loading ? 'Authenticating...' : 'Sign In'}</span>
              <ArrowRight className="h-4 w-4" />
            </button>
          </form>

          <div className="mt-5 border-t border-slate-700/80 pt-4 text-center">
            <span className="text-xs text-slate-400">Need a new workspace? </span>
            <Link to="/register" className="text-xs font-semibold text-indigo-400 hover:text-indigo-300">
              Register Organization
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};
