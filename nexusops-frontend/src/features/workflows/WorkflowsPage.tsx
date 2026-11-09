import React, { useEffect, useState, useMemo } from 'react';
import {
  GitFork,
  Plus,
  RefreshCw,
  AlertCircle,
  CheckCircle2,
  XCircle,
  ArrowRight,
  ShieldCheck,
  Split,
  Layers,
  CheckSquare,
  X,
  Eye,
} from 'lucide-react';
import { apiClient } from '@/lib/api-client';
import { useAuthStore } from '@/features/auth/authStore';
import {
  ApiResponse,
  WorkflowDefinitionResponseDto,
  WorkflowVersionResponseDto,
  RequestCategoryResponseDto,
  NodeType,
} from '@/types/api';

export const WorkflowsPage: React.FC = () => {
  const user = useAuthStore((s) => s.user);
  const canManageWorkflows = useMemo(() => {
    if (!user) return false;
    return user.permissions?.includes('WORKFLOW_MANAGE') || user.roles?.includes('ORG_ADMIN');
  }, [user]);

  const [definitions, setDefinitions] = useState<WorkflowDefinitionResponseDto[]>([]);
  const [categories, setCategories] = useState<RequestCategoryResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Graph Inspector State
  const [selectedDefinition, setSelectedDefinition] = useState<WorkflowDefinitionResponseDto | null>(null);
  const [selectedVersion, setSelectedVersion] = useState<WorkflowVersionResponseDto | null>(null);
  const [loadingVersion, setLoadingVersion] = useState(false);

  // New Workflow Modal State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);

  const fetchDefinitions = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown, ApiResponse<WorkflowDefinitionResponseDto[]>>('/workflows');
      if (res.data) {
        setDefinitions(res.data);
      }
    } catch (err: unknown) {
      const errObj = err as { message?: string };
      setError(errObj.message || 'Failed to load workflow definitions.');
    } finally {
      setLoading(false);
    }
  };

  const fetchCategories = async () => {
    try {
      const res = await apiClient.get<unknown, ApiResponse<RequestCategoryResponseDto[]>>('/request-categories');
      if (res.data) {
        setCategories(res.data);
      }
    } catch (err: unknown) {
      console.error('Failed to load categories', err);
    }
  };

  useEffect(() => {
    fetchDefinitions();
    fetchCategories();
  }, []);

  const handleInspectGraph = async (def: WorkflowDefinitionResponseDto) => {
    setSelectedDefinition(def);
    const verNum = def.latestVersionNumber || 1;
    setLoadingVersion(true);
    try {
      const res = await apiClient.get<unknown, ApiResponse<WorkflowVersionResponseDto>>(
        `/workflows/${def.id}/versions/${verNum}`
      );
      if (res.data) {
        setSelectedVersion(res.data);
      }
    } catch (err: unknown) {
      console.error('Failed to load workflow version', err);
    } finally {
      setLoadingVersion(false);
    }
  };

  const handleCreateWorkflow = async (e: React.FormEvent) => {
    e.preventDefault();
    setModalError(null);
    setSubmitting(true);

    try {
      const res = await apiClient.post<unknown, ApiResponse<WorkflowDefinitionResponseDto>>('/workflows', {
        categoryId,
        code: code.trim().toUpperCase(),
        name: name.trim(),
        description: description.trim() || undefined,
      });

      if (res.data) {
        setIsModalOpen(false);
        setName('');
        setCode('');
        setCategoryId('');
        setDescription('');
        await fetchDefinitions();
      }
    } catch (err: unknown) {
      const errObj = err as { message?: string; details?: Array<{ field: string; issue: string }> };
      if (errObj.details && errObj.details.length > 0) {
        setModalError(errObj.details.map((d) => `${d.field}: ${d.issue}`).join(', '));
      } else {
        setModalError(errObj.message || 'Failed to create workflow definition.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const getNodeTypeBadge = (type: NodeType, key: string) => {
    const isRejection = key.toLowerCase().includes('rej');

    switch (type) {
      case 'START':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
            <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" /> START
          </span>
        );
      case 'APPROVAL':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-semibold bg-indigo-50 text-indigo-700 border border-indigo-200">
            <ShieldCheck className="h-3.5 w-3.5 text-indigo-600" /> APPROVAL
          </span>
        );
      case 'CONDITION':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-semibold bg-amber-50 text-amber-700 border border-amber-200">
            <Split className="h-3.5 w-3.5 text-amber-600" /> CONDITION
          </span>
        );
      case 'TASK':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-semibold bg-purple-50 text-purple-700 border border-purple-200">
            <CheckSquare className="h-3.5 w-3.5 text-purple-600" /> TASK
          </span>
        );
      case 'END':
        return isRejection ? (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-semibold bg-rose-50 text-rose-700 border border-rose-200">
            <XCircle className="h-3.5 w-3.5 text-rose-600" /> REJECTED END
          </span>
        ) : (
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
            <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" /> COMPLETED END
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
            <GitFork className="h-6 w-6 text-indigo-600" />
            Workflow Engine Studio
          </h1>
          <p className="text-sm text-slate-500 mt-0.5">
            Configure graph-based approval routes, condition predicates, and automated task execution.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={fetchDefinitions}
            disabled={loading}
            className="p-2 text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-lg border border-slate-200 transition-colors"
            title="Refresh workflows"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
          {canManageWorkflows && (
            <button
              onClick={() => setIsModalOpen(true)}
              className="flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-sm font-semibold shadow-sm transition-colors"
            >
              <Plus className="h-4 w-4" />
              <span>New Workflow</span>
            </button>
          )}
        </div>
      </div>

      {/* Global Error Banner */}
      {error && (
        <div className="p-4 bg-rose-50 border border-rose-200 rounded-xl flex items-start gap-3 text-rose-800 text-sm">
          <AlertCircle className="h-5 w-5 text-rose-600 shrink-0 mt-0.5" />
          <div className="flex-1">
            <p className="font-semibold">Failed to load workflows</p>
            <p className="text-rose-700 mt-0.5">{error}</p>
          </div>
          <button onClick={fetchDefinitions} className="text-rose-600 font-semibold hover:underline text-xs">
            Retry
          </button>
        </div>
      )}

      {/* Workflow Definitions Cards */}
      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm">
        <div className="p-4 border-b border-slate-200 bg-slate-50/50 flex items-center justify-between">
          <span className="text-xs font-semibold text-slate-700 uppercase tracking-wider">
            Active Workflow Definitions ({definitions.length})
          </span>
          <span className="text-xs text-slate-500">
            Immutable version snapshots enforced per organization
          </span>
        </div>

        {loading ? (
          <div className="p-12 text-center text-slate-500">
            <RefreshCw className="h-6 w-6 animate-spin mx-auto text-indigo-600 mb-2" />
            <p className="text-sm font-medium">Loading workflows...</p>
          </div>
        ) : definitions.length === 0 ? (
          <div className="p-12 text-center">
            <GitFork className="h-10 w-10 text-slate-300 mx-auto mb-3" />
            <p className="text-sm font-semibold text-slate-700">No workflows defined</p>
            <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
              Workflows are automatically bootstrapped when relevant requests are submitted, or you can establish a new business process.
            </p>
            <button
              onClick={() => setIsModalOpen(true)}
              className="mt-4 inline-flex items-center gap-2 px-3.5 py-1.5 bg-indigo-50 text-indigo-700 hover:bg-indigo-100 rounded-lg text-xs font-semibold transition-colors"
            >
              <Plus className="h-3.5 w-3.5" />
              <span>Establish Workflow</span>
            </button>
          </div>
        ) : (
          <div className="divide-y divide-slate-200">
            {definitions.map((def) => (
              <div
                key={def.id}
                className="p-6 hover:bg-slate-50 transition-colors flex flex-col md:flex-row md:items-center justify-between gap-4"
              >
                <div className="flex items-start gap-4">
                  <div className="p-3 bg-indigo-50 text-indigo-600 rounded-xl shrink-0 mt-0.5">
                    <GitFork className="h-6 w-6" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2 flex-wrap">
                      <h3 className="font-bold text-slate-900 text-base">{def.name}</h3>
                      <span className="font-mono text-xs px-2 py-0.5 rounded bg-slate-100 text-slate-700 border border-slate-200">
                        {def.code}
                      </span>
                      {def.latestVersionStatus === 'PUBLISHED' && (
                        <span className="inline-flex items-center gap-1 text-xs bg-emerald-100 text-emerald-800 font-bold px-2 py-0.5 rounded-full border border-emerald-200">
                          <CheckCircle2 className="h-3 w-3" /> v{def.latestVersionNumber}.0 (PUBLISHED)
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-slate-500 mt-1.5 max-w-2xl leading-relaxed">
                      {def.description || 'Configured operational workflow orchestration.'}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-3 shrink-0">
                  <button
                    onClick={() => handleInspectGraph(def)}
                    className="flex items-center gap-1.5 px-3.5 py-2 text-xs font-semibold text-indigo-600 hover:text-indigo-700 bg-indigo-50 hover:bg-indigo-100 border border-indigo-200 rounded-lg transition-colors"
                  >
                    <Eye className="h-3.5 w-3.5" />
                    <span>Inspect Graph</span>
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ========================================================= */}
      {/* GRAPH INSPECTOR MODAL                                     */}
      {/* ========================================================= */}
      {selectedDefinition && (
        <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/50 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-4xl bg-white rounded-2xl shadow-2xl overflow-hidden border border-slate-200 animate-in fade-in zoom-in-95 duration-150 flex flex-col max-h-[90vh]">
            {/* Header */}
            <div className="p-6 border-b border-slate-200 flex items-start justify-between bg-slate-50/70">
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-mono text-xs px-2 py-0.5 rounded bg-indigo-100 text-indigo-800 font-bold">
                    {selectedDefinition.code}
                  </span>
                  <span className="text-xs bg-emerald-100 text-emerald-800 font-semibold px-2 py-0.5 rounded-full">
                    Version {selectedDefinition.latestVersionNumber || 1}.0 (Published)
                  </span>
                </div>
                <h2 className="text-xl font-bold text-slate-900 mt-1.5">{selectedDefinition.name}</h2>
                <p className="text-xs text-slate-500 mt-0.5">{selectedDefinition.description}</p>
              </div>
              <button
                onClick={() => {
                  setSelectedDefinition(null);
                  setSelectedVersion(null);
                }}
                className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {/* Content Body */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6">
              {loadingVersion ? (
                <div className="p-12 text-center text-slate-500">
                  <RefreshCw className="h-6 w-6 animate-spin mx-auto text-indigo-600 mb-2" />
                  <p className="text-sm font-medium">Loading compiled graph blueprint...</p>
                </div>
              ) : selectedVersion ? (
                <>
                  {/* Graph Overview Banner */}
                  <div className="p-4 bg-indigo-50/50 border border-indigo-100 rounded-xl flex items-center justify-between text-xs text-indigo-900">
                    <div className="flex items-center gap-2">
                      <Layers className="h-4 w-4 text-indigo-600" />
                      <span>
                        Graph Structure: <strong>{selectedVersion.nodes.length} Nodes</strong> and{' '}
                        <strong>{selectedVersion.transitions.length} Directed Transitions</strong>
                      </span>
                    </div>
                    <span className="text-indigo-600 font-medium">
                      Published on {selectedVersion.publishedAt ? new Date(selectedVersion.publishedAt).toLocaleDateString() : 'N/A'}
                    </span>
                  </div>

                  {/* Nodes Grid */}
                  <div>
                    <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-3 flex items-center gap-1.5">
                      <Layers className="h-4 w-4 text-indigo-600" />
                      Graph Execution Nodes
                    </h3>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                      {selectedVersion.nodes.map((node) => (
                        <div
                          key={node.id}
                          className="p-4 bg-slate-50 border border-slate-200 rounded-xl flex flex-col justify-between"
                        >
                          <div>
                            <div className="flex items-center justify-between gap-2 mb-2">
                              {getNodeTypeBadge(node.nodeType, node.nodeKey)}
                              <span className="font-mono text-[11px] text-slate-400 font-bold">
                                {node.nodeKey}
                              </span>
                            </div>
                            <h4 className="font-bold text-slate-900 text-sm">{node.title}</h4>
                            {node.configuration && Object.keys(node.configuration).length > 0 && (
                              <div className="mt-2 pt-2 border-t border-slate-200/80 text-[11px] text-slate-600 space-y-1">
                                {Object.entries(node.configuration).map(([k, v]) => (
                                  <div key={k} className="flex justify-between">
                                    <span className="text-slate-400 capitalize">{k}:</span>
                                    <span className="font-medium text-slate-800">{String(v)}</span>
                                  </div>
                                ))}
                              </div>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* Directed Transitions Table */}
                  <div>
                    <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-3 flex items-center gap-1.5">
                      <ArrowRight className="h-4 w-4 text-indigo-600" />
                      Transition Routing &amp; Predicate Conditions
                    </h3>
                    <div className="overflow-x-auto border border-slate-200 rounded-xl">
                      <table className="w-full text-left text-xs text-slate-600">
                        <thead className="bg-slate-50 font-semibold uppercase tracking-wider text-slate-500 border-b border-slate-200">
                          <tr>
                            <th className="px-4 py-2.5">Source Node</th>
                            <th className="px-4 py-2.5">Target Node</th>
                            <th className="px-4 py-2.5">Priority</th>
                            <th className="px-4 py-2.5">Condition Predicate</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-200">
                          {selectedVersion.transitions.map((t) => {
                            const source = selectedVersion.nodes.find((n) => n.id === t.sourceNodeId);
                            const target = selectedVersion.nodes.find((n) => n.id === t.targetNodeId);

                            return (
                              <tr key={t.id} className="hover:bg-slate-50">
                                <td className="px-4 py-3 font-mono font-medium text-slate-900">
                                  {source ? source.nodeKey : t.sourceNodeId}
                                </td>
                                <td className="px-4 py-3 font-mono font-medium text-indigo-600 flex items-center gap-1">
                                  <ArrowRight className="h-3 w-3 text-slate-400" />
                                  {target ? target.nodeKey : t.targetNodeId}
                                </td>
                                <td className="px-4 py-3 font-semibold text-slate-700">{t.priority}</td>
                                <td className="px-4 py-3">
                                  {t.conditionExpression ? (
                                    <code className="px-2 py-0.5 rounded bg-amber-50 text-amber-800 border border-amber-200 font-mono text-[11px]">
                                      {t.conditionExpression.field} {t.conditionExpression.operator}{' '}
                                      {String(t.conditionExpression.value)}
                                    </code>
                                  ) : t.isDefault ? (
                                    <span className="text-slate-400 italic">Default fallback route</span>
                                  ) : (
                                    <span className="text-slate-400 italic">Always advance</span>
                                  )}
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </>
              ) : (
                <div className="p-8 text-center text-slate-400">No version data available.</div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ========================================================= */}
      {/* NEW WORKFLOW MODAL                                        */}
      {/* ========================================================= */}
      {isModalOpen && canManageWorkflows && (
        <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/50 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-lg bg-white rounded-2xl shadow-xl overflow-hidden border border-slate-200 animate-in fade-in zoom-in-95 duration-150">
            <div className="p-6 border-b border-slate-200 flex items-center justify-between bg-slate-50/50">
              <h2 className="text-lg font-bold text-slate-900 tracking-tight">Create Workflow Definition</h2>
              <button
                onClick={() => setIsModalOpen(false)}
                className="p-1.5 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <form onSubmit={handleCreateWorkflow} className="p-6 space-y-4">
              {modalError && (
                <div className="p-3 bg-rose-50 border border-rose-200 rounded-lg flex items-center gap-2 text-rose-800 text-xs">
                  <AlertCircle className="h-4 w-4 shrink-0 text-rose-600" />
                  <span>{modalError}</span>
                </div>
              )}

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                  Target Category *
                </label>
                <select
                  required
                  value={categoryId}
                  onChange={(e) => setCategoryId(e.target.value)}
                  className="w-full text-xs px-3 py-2 bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                >
                  <option value="">Select a request category...</option>
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name} ({c.key})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                  Workflow Name *
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g., Software Access Approval Routing"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full text-sm px-3 py-2 bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                  Workflow Code *
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g., WF_SOFTWARE_ACCESS"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  className="w-full text-sm font-mono px-3 py-2 bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                  Description
                </label>
                <textarea
                  rows={2}
                  placeholder="Summarize the routing and fulfillment steps..."
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  className="w-full text-xs px-3 py-2 bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                />
              </div>

              <div className="pt-4 border-t border-slate-200 flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-4 py-2 text-slate-500 hover:text-slate-700 text-xs font-semibold"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-5 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-lg text-xs font-semibold transition-colors shadow-sm"
                >
                  {submitting ? 'Creating...' : 'Create Workflow'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
