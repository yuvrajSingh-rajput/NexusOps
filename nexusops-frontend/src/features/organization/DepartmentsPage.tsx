import React, { useEffect, useState, useMemo } from 'react';
import { Building2, Plus, AlertCircle, FolderTree, RefreshCw, X } from 'lucide-react';
import { apiClient } from '@/lib/api-client';
import { useAuthStore } from '@/features/auth/authStore';
import { ApiResponse, DepartmentResponseDto } from '@/types/api';

export const DepartmentsPage: React.FC = () => {
  const user = useAuthStore((s) => s.user);
  const canManageDept = useMemo(() => {
    if (!user) return false;
    return user.permissions?.includes('ORG_DEPT_MANAGE') || user.roles?.includes('ORG_ADMIN');
  }, [user]);

  const [departments, setDepartments] = useState<DepartmentResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [parentDepartmentId, setParentDepartmentId] = useState<string>('');
  const [submitting, setSubmitting] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);

  const fetchDepartments = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown, ApiResponse<DepartmentResponseDto[]>>('/departments');
      if (res.data) {
        setDepartments(res.data);
      }
    } catch (err: unknown) {
      const errObj = err as { message?: string };
      setError(errObj.message || 'Failed to load organizational departments.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDepartments();
  }, []);

  const handleCreateDepartment = async (e: React.FormEvent) => {
    e.preventDefault();
    setModalError(null);
    setSubmitting(true);

    try {
      const payload: { code: string; name: string; parentDepartmentId?: string } = {
        code: code.trim().toUpperCase(),
        name: name.trim(),
      };
      if (parentDepartmentId) {
        payload.parentDepartmentId = parentDepartmentId;
      }

      await apiClient.post<unknown, ApiResponse<DepartmentResponseDto>>('/departments', payload);
      setIsModalOpen(false);
      setCode('');
      setName('');
      setParentDepartmentId('');
      await fetchDepartments();
    } catch (err: unknown) {
      const errObj = err as { message?: string; details?: Array<{ field: string; issue: string }> };
      if (errObj.details && errObj.details.length > 0) {
        setModalError(errObj.details.map((d) => `${d.field}: ${d.issue}`).join(', '));
      } else {
        setModalError(errObj.message || 'Failed to create department.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
            <Building2 className="h-6 w-6 text-indigo-600" />
            Departments
          </h1>
          <p className="text-sm text-slate-500 mt-0.5">
            Define organizational hierarchy, business units, and approval routing scopes.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={fetchDepartments}
            disabled={loading}
            className="p-2 text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-lg border border-slate-200 transition-colors"
            title="Refresh departments"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
          {canManageDept && (
            <button
              onClick={() => setIsModalOpen(true)}
              className="flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-sm font-semibold shadow-sm transition-colors"
            >
              <Plus className="h-4 w-4" />
              <span>Add Department</span>
            </button>
          )}
        </div>
      </div>

      {/* Error alert */}
      {error && (
        <div className="p-4 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-sm flex items-center gap-2">
          <AlertCircle className="h-5 w-5 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Department Directory Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200">
            <thead className="bg-slate-50">
              <tr>
                <th className="px-6 py-3.5 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">
                  Code
                </th>
                <th className="px-6 py-3.5 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">
                  Department Name
                </th>
                <th className="px-6 py-3.5 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">
                  Parent Department
                </th>
                <th className="px-6 py-3.5 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">
                  Hierarchy Level
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {loading && departments.length === 0 ? (
                <tr>
                  <td colSpan={4} className="px-6 py-12 text-center text-sm text-slate-400">
                    <RefreshCw className="h-6 w-6 animate-spin mx-auto mb-2 text-indigo-500" />
                    Loading organizational departments...
                  </td>
                </tr>
              ) : departments.length === 0 ? (
                <tr>
                  <td colSpan={4} className="px-6 py-12 text-center">
                    <FolderTree className="h-10 w-10 text-slate-300 mx-auto mb-3" />
                    <p className="text-sm font-medium text-slate-700">No departments established yet</p>
                    <p className="text-xs text-slate-400 mt-1">
                      Create departments like Engineering, HR, Finance to structure approval workflows.
                    </p>
                    {canManageDept && (
                      <button
                        onClick={() => setIsModalOpen(true)}
                        className="mt-4 inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-indigo-600 hover:text-indigo-800 bg-indigo-50 rounded-lg hover:bg-indigo-100 transition-colors"
                      >
                        <Plus className="h-3.5 w-3.5" />
                        Create first department
                      </button>
                    )}
                  </td>
                </tr>
              ) : (
                departments.map((dept) => (
                  <tr key={dept.id} className="hover:bg-slate-50/80 transition-colors">
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded font-mono text-xs font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200">
                        {dept.code}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className="text-sm font-semibold text-slate-900">{dept.name}</span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      {dept.parentDepartmentName ? (
                        <span className="inline-flex items-center gap-1 text-xs text-slate-600 bg-slate-100 px-2 py-0.5 rounded">
                          <FolderTree className="h-3 w-3 text-slate-400" />
                          {dept.parentDepartmentName}
                        </span>
                      ) : (
                        <span className="text-xs text-slate-400 italic">None (Top-level)</span>
                      )}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className="text-xs text-slate-500">
                        {dept.parentDepartmentId ? 'Sub-department' : 'Root Department'}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Create Department Modal */}
      {isModalOpen && canManageDept && (
        <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full shadow-2xl border border-slate-100 overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            <div className="px-6 py-4 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="p-1.5 bg-indigo-100 text-indigo-700 rounded-lg">
                  <Building2 className="h-5 w-5" />
                </div>
                <h3 className="text-base font-bold text-slate-900">Add New Department</h3>
              </div>
              <button
                onClick={() => setIsModalOpen(false)}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-lg hover:bg-slate-200/50"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleCreateDepartment} className="p-6 space-y-4">
              {modalError && (
                <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-center gap-2">
                  <AlertCircle className="h-4 w-4 shrink-0" />
                  <span>{modalError}</span>
                </div>
              )}

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                  Department Code
                </label>
                <input
                  type="text"
                  required
                  value={code}
                  onChange={(e) => setCode(e.target.value.toUpperCase())}
                  placeholder="e.g. ENG, FIN, HR, IT"
                  maxLength={20}
                  className="mt-1 block w-full px-3 py-2 text-sm bg-white border border-slate-300 rounded-lg font-mono uppercase focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                />
                <p className="mt-1 text-[11px] text-slate-400">
                  Unique business code used in workflow triggers and routing rules.
                </p>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                  Department Name
                </label>
                <input
                  type="text"
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="e.g. Engineering & Platform"
                  className="mt-1 block w-full px-3 py-2 text-sm bg-white border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                  Parent Department (Optional)
                </label>
                <select
                  value={parentDepartmentId}
                  onChange={(e) => setParentDepartmentId(e.target.value)}
                  className="mt-1 block w-full px-3 py-2 text-sm bg-white border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                >
                  <option value="">None (Top-level Organization Unit)</option>
                  {departments.map((dept) => (
                    <option key={dept.id} value={dept.id}>
                      {dept.code} – {dept.name}
                    </option>
                  ))}
                </select>
              </div>

              <div className="pt-3 border-t border-slate-200 flex items-center justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-4 py-2 text-sm font-medium text-slate-600 hover:text-slate-800 hover:bg-slate-100 rounded-lg transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg shadow-sm transition-colors disabled:opacity-50"
                >
                  {submitting ? 'Creating...' : 'Create Department'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

