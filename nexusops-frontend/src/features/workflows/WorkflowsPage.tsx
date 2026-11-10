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
  Eye,
  ArrowLeft,
  Sparkles,
  UploadCloud,
  FileCheck2,
  AlertTriangle
} from 'lucide-react';
import { apiClient } from '@/lib/api-client';
import { useAuthStore } from '@/features/auth/authStore';
import {
  ApiResponse,
  WorkflowDefinitionResponseDto,
  WorkflowVersionResponseDto,
  RequestCategoryResponseDto,
  NodeType
} from '@/types/api';
import { WorkflowCanvas } from './components/WorkflowCanvas';
import {
  WorkflowVersionSummary,
  WorkflowValidationResult,
  SaveWorkflowDraftDto
} from './types/workflowCanvas';
import { SEO } from '@/components/common/SEO';

export const WorkflowsPage: React.FC = () => {
  const user = useAuthStore((s) => s.user);
  const canManageWorkflows = useMemo(() => {
    if (!user) return false;
    return (
      user.permissions?.includes('WORKFLOW_MANAGE') ||
      user.permissions?.includes('ORG_ADMIN_MANAGE') ||
      user.roles?.includes('ORG_ADMIN')
    );
  }, [user]);

  const [definitions, setDefinitions] = useState<WorkflowDefinitionResponseDto[]>([]);
  const [categories, setCategories] = useState<RequestCategoryResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Active Studio State
  const [activeDef, setActiveDef] = useState<WorkflowDefinitionResponseDto | null>(null);
  const [versions, setVersions] = useState<WorkflowVersionSummary[]>([]);
  const [selectedVersionNum, setSelectedVersionNum] = useState<number>(1);
  const [activeVersionData, setActiveVersionData] = useState<WorkflowVersionResponseDto | null>(null);
  const [loadingVersion, setLoadingVersion] = useState(false);

  // Studio View Mode: 'canvas' (React Flow) vs 'inspector' (Cards & Table)
  const [viewMode, setViewMode] = useState<'canvas' | 'inspector'>('canvas');

  // Validation & Save State
  const [validationResult, setValidationResult] = useState<WorkflowValidationResult | null>(null);
  const [isValidating, setIsValidating] = useState(false);
  const [isSavingDraft, setIsSavingDraft] = useState(false);
  const [isPublishing, setIsPublishing] = useState(false);
  const [studioFeedback, setStudioFeedback] = useState<{ type: 'success' | 'error' | 'warning'; text: string } | null>(null);

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

  // Open Studio for a workflow definition
  const handleOpenStudio = async (def: WorkflowDefinitionResponseDto, initialView: 'canvas' | 'inspector' = 'canvas') => {
    setActiveDef(def);
    setViewMode(initialView);
    setStudioFeedback(null);
    setValidationResult(null);

    try {
      // 1. Fetch version history
      const vRes = await apiClient.get<unknown, ApiResponse<WorkflowVersionSummary[]>>(`/workflows/${def.id}/versions`);
      const vList = vRes.data || [];
      setVersions(vList);

      const targetVer = def.latestVersionNumber || (vList.length > 0 ? vList[0].versionNumber : 1);
      setSelectedVersionNum(targetVer);

      // 2. Fetch version details
      await loadVersionDetails(def.id, targetVer);
    } catch (err: any) {
      console.error('Failed to load workflow studio versions', err);
    }
  };

  const loadVersionDetails = async (defId: string, versionNumber: number) => {
    setLoadingVersion(true);
    setStudioFeedback(null);
    try {
      const res = await apiClient.get<unknown, ApiResponse<WorkflowVersionResponseDto>>(
        `/workflows/${defId}/versions/${versionNumber}`
      );
      if (res.data) {
        setActiveVersionData(res.data);
      }
    } catch (err: any) {
      console.error('Failed to load version details', err);
      setStudioFeedback({ type: 'error', text: err?.message || 'Failed to load version details' });
    } finally {
      setLoadingVersion(false);
    }
  };

  const handleSelectVersion = async (verNum: number) => {
    if (!activeDef) return;
    setSelectedVersionNum(verNum);
    setValidationResult(null);
    await loadVersionDetails(activeDef.id, verNum);
  };

  // Create new draft version (from published)
  const handleCreateNewVersion = async () => {
    if (!activeDef || !canManageWorkflows) return;
    setLoadingVersion(true);
    try {
      const res = await apiClient.post<unknown, ApiResponse<WorkflowVersionResponseDto>>(
        `/workflows/${activeDef.id}/versions`
      );
      if (res.data) {
        setStudioFeedback({
          type: 'success',
          text: `Created new Draft version v${res.data.versionNumber}.0 cloned from previous configuration.`
        });

        // Refresh versions list
        const vRes = await apiClient.get<unknown, ApiResponse<WorkflowVersionSummary[]>>(`/workflows/${activeDef.id}/versions`);
        setVersions(vRes.data || []);
        setSelectedVersionNum(res.data.versionNumber);
        setActiveVersionData(res.data);
      }
    } catch (err: any) {
      setStudioFeedback({ type: 'error', text: err?.message || 'Failed to create new draft version' });
    } finally {
      setLoadingVersion(false);
    }
  };

  // Save Draft
  const handleSaveDraft = async (draft: SaveWorkflowDraftDto) => {
    if (!activeDef || !canManageWorkflows) return;
    setIsSavingDraft(true);
    setStudioFeedback(null);

    try {
      const res = await apiClient.put<unknown, ApiResponse<WorkflowVersionResponseDto>>(
        `/workflows/${activeDef.id}/versions/${selectedVersionNum}`,
        draft
      );
      if (res.data) {
        setActiveVersionData(res.data);
        setStudioFeedback({ type: 'success', text: `Draft version v${selectedVersionNum}.0 saved successfully.` });
        setTimeout(() => setStudioFeedback(null), 4000);
      }
    } catch (err: any) {
      setStudioFeedback({ type: 'error', text: err?.message || 'Failed to save workflow draft.' });
    } finally {
      setIsSavingDraft(false);
    }
  };

  // Validate Graph
  const handleValidateGraph = async () => {
    if (!activeDef) return;
    setIsValidating(true);
    setStudioFeedback(null);

    try {
      const res = await apiClient.post<unknown, ApiResponse<WorkflowValidationResult>>(
        `/workflows/${activeDef.id}/versions/${selectedVersionNum}/validate`
      );
      if (res.data) {
        setValidationResult(res.data);
        if (res.data.valid) {
          if (res.data.warnings && res.data.warnings.length > 0) {
            setStudioFeedback({
              type: 'warning',
              text: `Workflow structure is valid with ${res.data.warnings.length} warning(s).`
            });
          } else {
            setStudioFeedback({
              type: 'success',
              text: 'Workflow graph structure is 100% valid! Ready to publish.'
            });
          }
        } else {
          setStudioFeedback({
            type: 'error',
            text: `Validation failed with ${res.data.errors.length} critical issue(s).`
          });
        }
      }
    } catch (err: any) {
      setStudioFeedback({ type: 'error', text: err?.message || 'Validation request failed.' });
    } finally {
      setIsValidating(false);
    }
  };

  // Publish Version
  const handlePublishVersion = async () => {
    if (!activeDef || !canManageWorkflows) return;
    setIsPublishing(true);
    setStudioFeedback(null);

    try {
      const res = await apiClient.post<unknown, ApiResponse<WorkflowVersionResponseDto>>(
        `/workflows/${activeDef.id}/versions/${selectedVersionNum}/publish`
      );
      if (res.data) {
        setActiveVersionData(res.data);
        setStudioFeedback({
          type: 'success',
          text: `Version v${selectedVersionNum}.0 published! This version snapshot is now active and immutable.`
        });

        // Refresh definitions and versions list
        await fetchDefinitions();
        const vRes = await apiClient.get<unknown, ApiResponse<WorkflowVersionSummary[]>>(`/workflows/${activeDef.id}/versions`);
        setVersions(vRes.data || []);
      }
    } catch (err: any) {
      setStudioFeedback({ type: 'error', text: err?.message || 'Failed to publish workflow version.' });
    } finally {
      setIsPublishing(false);
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
        description: description.trim() || undefined
      });

      if (res.data) {
        setIsModalOpen(false);
        setName('');
        setCode('');
        setCategoryId('');
        setDescription('');
        await fetchDefinitions();
        handleOpenStudio(res.data, 'canvas');
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

  const isCurrentVersionPublished = activeVersionData?.status === 'PUBLISHED';
  const isCurrentVersionRetired = activeVersionData?.status === 'RETIRED';
  const isCurrentVersionDraft = activeVersionData?.status === 'DRAFT';

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

  /* -------------------------------------------------------------------------- */
  /* Render: Workflow Studio (Designer & Inspector)                             */
  /* -------------------------------------------------------------------------- */
  if (activeDef) {
    return (
      <div className="space-y-4">
        <SEO title={`Workflow Studio – ${activeDef.name}`} noIndex={true} />
        {/* Top Studio Bar */}
        <div className="bg-white rounded-2xl border border-slate-200 p-4 shadow-sm flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <button
              onClick={() => {
                setActiveDef(null);
                setActiveVersionData(null);
                setValidationResult(null);
              }}
              className="p-2 text-slate-500 hover:text-slate-800 hover:bg-slate-100 rounded-xl transition-colors"
              title="Return to workflows list"
            >
              <ArrowLeft className="h-5 w-5" />
            </button>

            <div>
              <div className="flex items-center gap-2 flex-wrap">
                <span className="font-mono text-xs font-bold px-2.5 py-0.5 rounded-lg bg-indigo-50 text-indigo-700 border border-indigo-200">
                  {activeDef.code}
                </span>
                <h1 className="text-lg font-bold text-slate-900 tracking-tight">{activeDef.name}</h1>

                {/* Status Indicator */}
                {isCurrentVersionPublished && (
                  <span className="inline-flex items-center gap-1 text-xs font-bold bg-emerald-50 text-emerald-700 border border-emerald-200 px-2.5 py-0.5 rounded-full">
                    <CheckCircle2 className="h-3 w-3" /> v{selectedVersionNum}.0 (PUBLISHED)
                  </span>
                )}
                {isCurrentVersionDraft && (
                  <span className="inline-flex items-center gap-1 text-xs font-bold bg-amber-50 text-amber-700 border border-amber-200 px-2.5 py-0.5 rounded-full">
                    <Sparkles className="h-3 w-3 text-amber-600" /> v{selectedVersionNum}.0 (DRAFT)
                  </span>
                )}
                {isCurrentVersionRetired && (
                  <span className="inline-flex items-center gap-1 text-xs font-semibold bg-slate-100 text-slate-600 border border-slate-200 px-2.5 py-0.5 rounded-full">
                    v{selectedVersionNum}.0 (RETIRED)
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-500 mt-0.5">{activeDef.description}</p>
            </div>
          </div>

          {/* Right Action Controls */}
          <div className="flex items-center gap-2 flex-wrap">
            {/* Version Switcher */}
            <div className="flex items-center gap-1.5 bg-slate-50 p-1 rounded-xl border border-slate-200">
              <span className="text-[11px] font-semibold text-slate-500 pl-2">Version:</span>
              <select
                value={selectedVersionNum}
                onChange={(e) => handleSelectVersion(Number(e.target.value))}
                className="text-xs font-semibold bg-white border border-slate-200 rounded-lg px-2.5 py-1 text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              >
                {versions.map((v) => (
                  <option key={v.id} value={v.versionNumber}>
                    v{v.versionNumber}.0 ({v.status})
                  </option>
                ))}
              </select>
            </div>

            {/* View Mode Toggle */}
            <div className="flex items-center bg-slate-100 p-1 rounded-xl border border-slate-200 text-xs font-semibold">
              <button
                type="button"
                onClick={() => setViewMode('canvas')}
                className={`px-3 py-1.5 rounded-lg transition-all ${
                  viewMode === 'canvas'
                    ? 'bg-white text-indigo-700 shadow-2xs font-bold'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Visual Canvas
              </button>
              <button
                type="button"
                onClick={() => setViewMode('inspector')}
                className={`px-3 py-1.5 rounded-lg transition-all ${
                  viewMode === 'inspector'
                    ? 'bg-white text-indigo-700 shadow-2xs font-bold'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                Inspect Graph
              </button>
            </div>

            {/* Validate Button */}
            <button
              type="button"
              onClick={handleValidateGraph}
              disabled={isValidating}
              className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:text-indigo-600 bg-white hover:bg-slate-50 border border-slate-200 rounded-xl shadow-2xs transition-colors"
            >
              <FileCheck2 className={`h-3.5 w-3.5 ${isValidating ? 'animate-spin text-indigo-600' : ''}`} />
              <span>Validate</span>
            </button>

            {/* Publish Button (if draft) */}
            {canManageWorkflows && isCurrentVersionDraft && (
              <button
                type="button"
                onClick={handlePublishVersion}
                disabled={isPublishing}
                className="flex items-center gap-1.5 px-3.5 py-1.5 bg-emerald-600 hover:bg-emerald-700 active:scale-95 text-white text-xs font-semibold rounded-xl shadow-sm transition-all disabled:opacity-50"
              >
                <UploadCloud className="h-3.5 w-3.5" />
                <span>{isPublishing ? 'Publishing...' : 'Publish Version'}</span>
              </button>
            )}

            {/* Create New Draft (if currently published) */}
            {canManageWorkflows && isCurrentVersionPublished && (
              <button
                type="button"
                onClick={handleCreateNewVersion}
                disabled={loadingVersion}
                className="flex items-center gap-1.5 px-3.5 py-1.5 bg-indigo-600 hover:bg-indigo-700 active:scale-95 text-white text-xs font-semibold rounded-xl shadow-sm transition-all"
              >
                <Plus className="h-3.5 w-3.5" />
                <span>New Draft Version</span>
              </button>
            )}
          </div>
        </div>

        {/* Studio Feedback Alert */}
        {studioFeedback && (
          <div
            className={`p-3.5 rounded-xl border flex items-center justify-between text-xs font-medium animate-fadeIn ${
              studioFeedback.type === 'success'
                ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
                : studioFeedback.type === 'warning'
                ? 'bg-amber-50 text-amber-800 border-amber-200'
                : 'bg-red-50 text-red-800 border-red-200'
            }`}
          >
            <div className="flex items-center gap-2">
              {studioFeedback.type === 'success' ? (
                <CheckCircle2 className="h-4 w-4 text-emerald-600 shrink-0" />
              ) : studioFeedback.type === 'warning' ? (
                <AlertTriangle className="h-4 w-4 text-amber-600 shrink-0" />
              ) : (
                <AlertCircle className="h-4 w-4 text-red-600 shrink-0" />
              )}
              <span>{studioFeedback.text}</span>
            </div>
            <button
              onClick={() => setStudioFeedback(null)}
              className="text-slate-400 hover:text-slate-600 font-bold ml-2"
            >
              ×
            </button>
          </div>
        )}

        {/* Validation Errors & Warnings Details Banner */}
        {validationResult && (!validationResult.valid || validationResult.warnings.length > 0) && (
          <div className="bg-white rounded-2xl border border-slate-200 p-4 shadow-sm space-y-3">
            <div className="flex items-center justify-between">
              <h4 className="text-xs font-bold uppercase tracking-wider text-slate-700 flex items-center gap-1.5">
                <FileCheck2 className="h-4 w-4 text-indigo-600" />
                Workflow Validation Analysis
              </h4>
              <span
                className={`text-xs font-bold px-2.5 py-0.5 rounded-full ${
                  validationResult.valid
                    ? 'bg-emerald-100 text-emerald-800'
                    : 'bg-red-100 text-red-800'
                }`}
              >
                {validationResult.valid ? 'PASSED WITH WARNINGS' : 'VALIDATION FAILED'}
              </span>
            </div>

            {validationResult.errors && validationResult.errors.length > 0 && (
              <div className="space-y-1">
                <div className="text-[11px] font-bold text-red-700 uppercase tracking-wider">Critical Errors:</div>
                <ul className="list-disc list-inside text-xs text-red-600 space-y-0.5">
                  {validationResult.errors.map((err, i) => (
                    <li key={i}>{err}</li>
                  ))}
                </ul>
              </div>
            )}

            {validationResult.warnings && validationResult.warnings.length > 0 && (
              <div className="space-y-1">
                <div className="text-[11px] font-bold text-amber-700 uppercase tracking-wider">Recommendations &amp; Warnings:</div>
                <ul className="list-disc list-inside text-xs text-amber-700 space-y-0.5">
                  {validationResult.warnings.map((w, i) => (
                    <li key={i}>{w}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}

        {/* Loading Version State */}
        {loadingVersion ? (
          <div className="h-[600px] bg-white rounded-2xl border border-slate-200 flex flex-col items-center justify-center text-slate-500">
            <RefreshCw className="h-8 w-8 animate-spin text-indigo-600 mb-3" />
            <p className="text-sm font-semibold text-slate-700">Loading workflow graph...</p>
          </div>
        ) : activeVersionData ? (
          viewMode === 'canvas' ? (
            /* VISUAL CANVAS (REACT FLOW) */
            <WorkflowCanvas
              initialNodes={activeVersionData.nodes}
              initialTransitions={activeVersionData.transitions}
              isReadOnly={!isCurrentVersionDraft || !canManageWorkflows}
              onSaveDraft={handleSaveDraft}
              isSaving={isSavingDraft}
            />
          ) : (
            /* GRAPH INSPECTOR (CARDS & TABLES) */
            <div className="bg-white rounded-2xl border border-slate-200 p-6 space-y-6 shadow-sm">
              <div className="p-4 bg-indigo-50/50 border border-indigo-100 rounded-xl flex items-center justify-between text-xs text-indigo-900">
                <div className="flex items-center gap-2">
                  <Layers className="h-4 w-4 text-indigo-600" />
                  <span>
                    Graph Structure: <strong>{activeVersionData.nodes.length} Nodes</strong> and{' '}
                    <strong>{activeVersionData.transitions.length} Directed Transitions</strong>
                  </span>
                </div>
                <span className="text-indigo-600 font-medium">
                  {activeVersionData.publishedAt
                    ? `Published on ${new Date(activeVersionData.publishedAt).toLocaleDateString()}`
                    : 'Draft Snapshot'}
                </span>
              </div>

              {/* Nodes Grid */}
              <div>
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-3 flex items-center gap-1.5">
                  <Layers className="h-4 w-4 text-indigo-600" />
                  Graph Execution Nodes
                </h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {activeVersionData.nodes.map((node) => (
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
                      {activeVersionData.transitions.map((t) => {
                        const source = activeVersionData.nodes.find((n) => n.id === t.sourceNodeId);
                        const target = activeVersionData.nodes.find((n) => n.id === t.targetNodeId);

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
                                  {JSON.stringify(t.conditionExpression)}
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
            </div>
          )
        ) : null}
      </div>
    );
  }

  /* -------------------------------------------------------------------------- */
  /* Render: Workflow Definitions List                                          */
  /* -------------------------------------------------------------------------- */
  return (
    <div className="space-y-6">
      <SEO title="Workflow Engine Studio" noIndex={true} />
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
            <GitFork className="h-6 w-6 text-indigo-600" />
            Workflow Engine Studio
          </h1>
          <p className="text-sm text-slate-500 mt-0.5">
            Visual graph designer and runtime coordinator for automated approval routing and task fulfillment.
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
        <div className="p-4 border-b border-slate-200 bg-slate-50/50 flex flex-col sm:flex-row sm:items-center justify-between gap-1">
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
              Workflows route organizational requests through configurable multi-step approval graphs.
            </p>
            {canManageWorkflows && (
              <button
                onClick={() => setIsModalOpen(true)}
                className="mt-4 inline-flex items-center gap-2 px-3.5 py-1.5 bg-indigo-50 text-indigo-700 hover:bg-indigo-100 rounded-lg text-xs font-semibold transition-colors"
              >
                <Plus className="h-3.5 w-3.5" />
                <span>Establish Workflow</span>
              </button>
            )}
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
                      {def.latestVersionStatus === 'DRAFT' && (
                        <span className="inline-flex items-center gap-1 text-xs bg-amber-100 text-amber-800 font-bold px-2 py-0.5 rounded-full border border-amber-200">
                          <Sparkles className="h-3 w-3" /> v{def.latestVersionNumber}.0 (DRAFT)
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-slate-500 mt-1.5 max-w-2xl leading-relaxed">
                      {def.description || 'Configured operational workflow orchestration.'}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <button
                    onClick={() => handleOpenStudio(def, 'canvas')}
                    className="flex items-center gap-1.5 px-3.5 py-2 text-xs font-bold text-white bg-indigo-600 hover:bg-indigo-700 rounded-xl shadow-xs transition-colors"
                  >
                    <Sparkles className="h-3.5 w-3.5" />
                    <span>Visual Designer</span>
                  </button>

                  <button
                    onClick={() => handleOpenStudio(def, 'inspector')}
                    className="flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-slate-600 hover:text-slate-800 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors"
                    title="View node list and transition table"
                  >
                    <Eye className="h-3.5 w-3.5" />
                    <span>Inspect</span>
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* NEW WORKFLOW MODAL */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/50 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full border border-slate-200 shadow-2xl p-6">
            <h3 className="text-base font-bold text-slate-900 mb-1">Create Workflow Definition</h3>
            <p className="text-xs text-slate-500 mb-4">
              Establishes a new visual approval process linked to a service category.
            </p>

            {modalError && (
              <div className="p-3 mb-4 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-700">
                {modalError}
              </div>
            )}

            <form onSubmit={handleCreateWorkflow} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Workflow Name *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Developer Laptop Requisition"
                  value={name}
                  onChange={(e) => {
                    setName(e.target.value);
                    if (!code) {
                      setCode(
                        'WF_' +
                          e.target.value
                            .toUpperCase()
                            .replace(/[^A-Z0-9]/g, '_')
                            .slice(0, 30)
                      );
                    }
                  }}
                  className="w-full text-sm px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Unique Code *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. WF_LAPTOP_PURCHASE"
                  value={code}
                  onChange={(e) => setCode(e.target.value.toUpperCase().replace(/\s+/g, '_'))}
                  className="w-full text-sm px-3 py-2 border border-slate-200 rounded-lg font-mono focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Associated Category *</label>
                <select
                  required
                  value={categoryId}
                  onChange={(e) => setCategoryId(e.target.value)}
                  className="w-full text-sm px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white"
                >
                  <option value="">Select a service category...</option>
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name} ({c.code})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Description</label>
                <textarea
                  rows={2}
                  placeholder="Describe the routing policy and tiers..."
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  className="w-full text-sm px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 resize-none"
                />
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-4 py-2 border border-slate-200 text-slate-600 hover:bg-slate-50 text-xs font-semibold rounded-lg"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={submitting}
                  className="px-5 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold rounded-lg shadow-sm disabled:opacity-50"
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
