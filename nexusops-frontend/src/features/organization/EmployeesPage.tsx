import React, { useEffect, useState, useMemo } from 'react';
import { Users, UserPlus, Search, AlertCircle, RefreshCw, X, Shield, Mail, Briefcase, Building2 } from 'lucide-react';
import { apiClient } from '@/lib/api-client';
import { useAuthStore } from '@/features/auth/authStore';
import { ApiResponse, EmployeeResponseDto, DepartmentResponseDto, PageResponse } from '@/types/api';

const AVAILABLE_ROLES = [
  { code: 'ROLE_EMPLOYEE', label: 'Employee', description: 'Can submit requests and view personal items' },
  { code: 'ROLE_MANAGER', label: 'Manager', description: 'Can approve subordinate requests and reviews' },
  { code: 'ROLE_FINANCE_OPERATOR', label: 'Finance Operator', description: 'Can approve and fulfill financial disbursements' },
  { code: 'ROLE_IT_OPERATOR', label: 'IT Operator', description: 'Can fulfill hardware and access procurement tasks' },
  { code: 'ROLE_ORG_ADMIN', label: 'Organization Admin', description: 'Full organizational settings and member administration' },
];

export const EmployeesPage: React.FC = () => {
  const user = useAuthStore((s) => s.user);
  const canInvite = useMemo(() => {
    if (!user) return false;
    return user.permissions?.includes('ORG_USER_INVITE') || user.roles?.includes('ORG_ADMIN');
  }, [user]);

  const [employees, setEmployees] = useState<EmployeeResponseDto[]>([]);
  const [departments, setDepartments] = useState<DepartmentResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');

  // Invite Modal State
  const [isInviteModalOpen, setIsInviteModalOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [jobTitle, setJobTitle] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [selectedRoles, setSelectedRoles] = useState<string[]>(['ROLE_EMPLOYEE']);
  const [inviting, setInviting] = useState(false);
  const [inviteError, setInviteError] = useState<string | null>(null);

  const fetchEmployeesAndDepartments = async () => {
    setLoading(true);
    setError(null);
    try {
      const [empRes, deptRes] = await Promise.all([
        apiClient.get<unknown, ApiResponse<PageResponse<EmployeeResponseDto>>>('/memberships?size=100'),
        apiClient.get<unknown, ApiResponse<DepartmentResponseDto[]>>('/departments'),
      ]);

      if (empRes.data) {
        setEmployees(empRes.data.content);
      }
      if (deptRes.data) {
        setDepartments(deptRes.data);
      }
    } catch (err: unknown) {
      const errObj = err as { message?: string };
      setError(errObj.message || 'Failed to load employee directory.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEmployeesAndDepartments();
  }, []);

  const handleRoleToggle = (roleCode: string) => {
    if (selectedRoles.includes(roleCode)) {
      if (selectedRoles.length > 1) {
        setSelectedRoles(selectedRoles.filter((r) => r !== roleCode));
      }
    } else {
      setSelectedRoles([...selectedRoles, roleCode]);
    }
  };

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    setInviteError(null);
    setInviting(true);

    try {
      const payload: {
        email: string;
        firstName: string;
        lastName: string;
        jobTitle?: string;
        departmentId?: string;
        roleCodes: string[];
      } = {
        email: inviteEmail.trim(),
        firstName: firstName.trim(),
        lastName: lastName.trim(),
        roleCodes: selectedRoles,
      };

      if (jobTitle.trim()) {
        payload.jobTitle = jobTitle.trim();
      }
      if (departmentId) {
        payload.departmentId = departmentId;
      }

      await apiClient.post<unknown, ApiResponse<EmployeeResponseDto>>('/memberships/invite', payload);
      setIsInviteModalOpen(false);
      setInviteEmail('');
      setFirstName('');
      setLastName('');
      setJobTitle('');
      setDepartmentId('');
      setSelectedRoles(['ROLE_EMPLOYEE']);
      await fetchEmployeesAndDepartments();
    } catch (err: unknown) {
      const errObj = err as { message?: string; details?: Array<{ field: string; issue: string }> };
      if (errObj.details && errObj.details.length > 0) {
        setInviteError(errObj.details.map((d) => `${d.field}: ${d.issue}`).join(', '));
      } else {
        setInviteError(errObj.message || 'Failed to invite employee.');
      }
    } finally {
      setInviting(false);
    }
  };

  const filteredEmployees = employees.filter((emp) => {
    const term = searchTerm.toLowerCase();
    return (
      emp.fullName?.toLowerCase().includes(term) ||
      emp.email.toLowerCase().includes(term) ||
      emp.departmentName?.toLowerCase().includes(term) ||
      emp.jobTitle?.toLowerCase().includes(term)
    );
  });

  const getRoleBadge = (role: string) => {
    switch (role) {
      case 'ROLE_ORG_ADMIN':
        return (
          <span key={role} className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold bg-purple-100 text-purple-800 border border-purple-200">
            Admin
          </span>
        );
      case 'ROLE_MANAGER':
        return (
          <span key={role} className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold bg-amber-100 text-amber-800 border border-amber-200">
            Manager
          </span>
        );
      case 'ROLE_FINANCE_OPERATOR':
        return (
          <span key={role} className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold bg-emerald-100 text-emerald-800 border border-emerald-200">
            Finance
          </span>
        );
      case 'ROLE_IT_OPERATOR':
        return (
          <span key={role} className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold bg-blue-100 text-blue-800 border border-blue-200">
            IT Ops
          </span>
        );
      default:
        return (
          <span key={role} className="inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold bg-slate-100 text-slate-700 border border-slate-200">
            Employee
          </span>
        );
    }
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
            <Users className="h-6 w-6 text-indigo-600" />
            Employees &amp; Memberships
          </h1>
          <p className="text-sm text-slate-500 mt-0.5">
            Manage organization directory, assigned job roles, department affiliations, and RBAC permissions.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={fetchEmployeesAndDepartments}
            disabled={loading}
            className="p-2 text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-lg border border-slate-200 transition-colors"
            title="Refresh members"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
          {canInvite && (
            <button
              onClick={() => setIsInviteModalOpen(true)}
              className="flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-sm font-semibold shadow-sm transition-colors"
            >
              <UserPlus className="h-4 w-4" />
              <span>Invite Employee</span>
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

      {/* Search & Filter Bar */}
      <div className="flex items-center justify-between gap-4">
        <div className="relative w-full max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search by name, email, department, or job title..."
            className="w-full pl-9 pr-4 py-2 text-sm bg-white border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all placeholder:text-slate-400"
          />
        </div>
        <div className="text-xs text-slate-500 font-medium">
          Showing {filteredEmployees.length} of {employees.length} members
        </div>
      </div>

      {/* Employee Directory Table */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200">
            <thead className="bg-slate-50">
              <tr>
                <th className="px-6 py-3.5 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">
                  Member
                </th>
                <th className="px-6 py-3.5 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">
                  Department
                </th>
                <th className="px-6 py-3.5 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">
                  Job Title
                </th>
                <th className="px-6 py-3.5 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">
                  Roles &amp; Access
                </th>
                <th className="px-6 py-3.5 text-left text-xs font-semibold text-slate-600 uppercase tracking-wider">
                  Status
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {loading && employees.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-12 text-center text-sm text-slate-400">
                    <RefreshCw className="h-6 w-6 animate-spin mx-auto mb-2 text-indigo-500" />
                    Loading employee directory...
                  </td>
                </tr>
              ) : filteredEmployees.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-12 text-center">
                    <Users className="h-10 w-10 text-slate-300 mx-auto mb-3" />
                    <p className="text-sm font-medium text-slate-700">No employees match your search</p>
                    <p className="text-xs text-slate-400 mt-1">Try adjusting the filter query or invite new staff members.</p>
                  </td>
                </tr>
              ) : (
                filteredEmployees.map((emp) => (
                  <tr key={emp.membershipId} className="hover:bg-slate-50/80 transition-colors">
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="flex items-center gap-3">
                        <div className="h-9 w-9 rounded-full bg-indigo-100 text-indigo-700 flex items-center justify-center font-bold text-xs border border-indigo-200 shrink-0">
                          {emp.firstName?.[0]}
                          {emp.lastName?.[0]}
                        </div>
                        <div>
                          <div className="text-sm font-semibold text-slate-900">{emp.fullName}</div>
                          <div className="text-xs text-slate-400 flex items-center gap-1">
                            <Mail className="h-3 w-3" />
                            {emp.email}
                          </div>
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      {emp.departmentName ? (
                        <span className="inline-flex items-center gap-1 text-xs text-slate-700 font-medium">
                          <Building2 className="h-3.5 w-3.5 text-slate-400" />
                          {emp.departmentName}
                        </span>
                      ) : (
                        <span className="text-xs text-slate-400 italic">Unassigned</span>
                      )}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span className="text-xs text-slate-600 font-medium">
                        {emp.jobTitle || 'Team Member'}
                      </span>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <div className="flex flex-wrap gap-1">
                        {emp.roles.map((r) => getRoleBadge(r))}
                      </div>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap">
                      <span
                        className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold ${
                          emp.status === 'ACTIVE'
                            ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                            : 'bg-amber-50 text-amber-700 border border-amber-200'
                        }`}
                      >
                        {emp.status}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Invite Employee Modal */}
      {isInviteModalOpen && canInvite && (
        <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full shadow-2xl border border-slate-100 overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            <div className="px-6 py-4 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <div className="p-1.5 bg-indigo-100 text-indigo-700 rounded-lg">
                  <UserPlus className="h-5 w-5" />
                </div>
                <h3 className="text-base font-bold text-slate-900">Invite Employee to Workspace</h3>
              </div>
              <button
                onClick={() => setIsInviteModalOpen(false)}
                className="text-slate-400 hover:text-slate-600 p-1 rounded-lg hover:bg-slate-200/50"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleInvite} className="p-6 space-y-4">
              {inviteError && (
                <div className="p-3 rounded-lg bg-rose-50 border border-rose-200 text-rose-700 text-xs flex items-center gap-2">
                  <AlertCircle className="h-4 w-4 shrink-0" />
                  <span>{inviteError}</span>
                </div>
              )}

              {/* Name Fields */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    First Name
                  </label>
                  <input
                    type="text"
                    required
                    value={firstName}
                    onChange={(e) => setFirstName(e.target.value)}
                    placeholder="Alex"
                    className="mt-1 block w-full px-3 py-2 text-sm bg-white border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Last Name
                  </label>
                  <input
                    type="text"
                    required
                    value={lastName}
                    onChange={(e) => setLastName(e.target.value)}
                    placeholder="Rivera"
                    className="mt-1 block w-full px-3 py-2 text-sm bg-white border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                  />
                </div>
              </div>

              {/* Email */}
              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                  Work Email Address
                </label>
                <div className="mt-1 relative rounded-lg shadow-sm">
                  <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <Mail className="h-4 w-4 text-slate-400" />
                  </div>
                  <input
                    type="email"
                    required
                    value={inviteEmail}
                    onChange={(e) => setInviteEmail(e.target.value)}
                    placeholder="alex.rivera@company.com"
                    className="block w-full pl-9 pr-3 py-2 text-sm bg-white border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                  />
                </div>
              </div>

              {/* Job Title & Department */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Job Title
                  </label>
                  <div className="mt-1 relative rounded-lg shadow-sm">
                    <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                      <Briefcase className="h-4 w-4 text-slate-400" />
                    </div>
                    <input
                      type="text"
                      value={jobTitle}
                      onChange={(e) => setJobTitle(e.target.value)}
                      placeholder="Senior Engineer"
                      className="block w-full pl-9 pr-3 py-2 text-sm bg-white border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider">
                    Department
                  </label>
                  <select
                    value={departmentId}
                    onChange={(e) => setDepartmentId(e.target.value)}
                    className="mt-1 block w-full px-3 py-2 text-sm bg-white border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                  >
                    <option value="">Unassigned</option>
                    {departments.map((dept) => (
                      <option key={dept.id} value={dept.id}>
                        {dept.name} ({dept.code})
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Roles Selection */}
              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-2 flex items-center gap-1.5">
                  <Shield className="h-4 w-4 text-indigo-600" />
                  System Roles &amp; Permissions
                </label>
                <div className="space-y-2 max-h-48 overflow-y-auto border border-slate-200 rounded-lg p-2.5 bg-slate-50/50">
                  {AVAILABLE_ROLES.map((r) => (
                    <label
                      key={r.code}
                      className="flex items-start gap-2.5 p-2 rounded-lg hover:bg-white hover:shadow-xs cursor-pointer transition-all border border-transparent hover:border-slate-200"
                    >
                      <input
                        type="checkbox"
                        checked={selectedRoles.includes(r.code)}
                        onChange={() => handleRoleToggle(r.code)}
                        className="mt-0.5 h-4 w-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
                      />
                      <div className="text-xs">
                        <div className="font-semibold text-slate-800">{r.label}</div>
                        <div className="text-slate-500">{r.description}</div>
                      </div>
                    </label>
                  ))}
                </div>
              </div>

              <div className="pt-3 border-t border-slate-200 flex items-center justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setIsInviteModalOpen(false)}
                  className="px-4 py-2 text-sm font-medium text-slate-600 hover:text-slate-800 hover:bg-slate-100 rounded-lg transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={inviting}
                  className="px-4 py-2 text-sm font-semibold text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg shadow-sm transition-colors disabled:opacity-50"
                >
                  {inviting ? 'Inviting...' : 'Send Invitation'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

