import React, { useEffect, useState, useMemo } from 'react';
import {
  FileText,
  Plus,
  Search,
  Filter,
  RefreshCw,
  X,
  ChevronRight,
  AlertCircle,
  Clock,
  CheckCircle2,
  XCircle,
  MessageSquare,
  Lock,
  Laptop,
  Key,
  Receipt,
  Send,
  Eye,
  Building,
  User,
  Calendar,
  AlertTriangle,
  ArrowRight,
  Layers,
  GitFork,
  Users
} from 'lucide-react';
import { apiClient } from '@/lib/api-client';
import { useAuthStore } from '@/features/auth/authStore';
import {
  ApiResponse,
  PageResponse,
  RequestResponseDto,
  RequestCategoryResponseDto,
  RequestCommentResponseDto,
  WorkflowInstanceResponseDto,
  NodeInstanceResponseDto,
  RequestPriority,
  RequestStatus,
  CommentVisibility,
} from '@/types/api';
import { SEO } from '@/components/common/SEO';

const getCategoryIcon = (code?: string, size: 'sm' | 'lg' = 'sm') => {
  const normalized = (code || '').toLowerCase();
  if (normalized.includes('laptop') || normalized.includes('hardware')) {
    return <Laptop className={size === 'lg' ? "h-6 w-6 text-indigo-600" : "h-3.5 w-3.5 text-indigo-600"} />;
  }
  if (normalized.includes('software') || normalized.includes('access') || normalized.includes('key')) {
    return <Key className={size === 'lg' ? "h-6 w-6 text-emerald-600" : "h-3.5 w-3.5 text-emerald-600"} />;
  }
  if (normalized.includes('expense') || normalized.includes('reimbursement') || normalized.includes('receipt')) {
    return <Receipt className={size === 'lg' ? "h-6 w-6 text-amber-600" : "h-3.5 w-3.5 text-amber-600"} />;
  }
  return <FileText className={size === 'lg' ? "h-6 w-6 text-slate-500" : "h-3.5 w-3.5 text-slate-500"} />;
};

export const parsePayloadSchema = (
  rawSchema?: Record<string, any> | string | null,
  categoryCode?: string
): { properties?: Record<string, any>; required?: string[] } | null => {
  let schemaObj: any = rawSchema;

  while (typeof schemaObj === 'string') {
    try {
      schemaObj = JSON.parse(schemaObj);
    } catch {
      break;
    }
  }

  if (typeof schemaObj === 'object' && schemaObj !== null) {
    if (schemaObj.properties && Object.keys(schemaObj.properties).length > 0) {
      return schemaObj;
    }
    if (schemaObj.payloadSchema) {
      return parsePayloadSchema(schemaObj.payloadSchema, categoryCode);
    }
    if (schemaObj.schema) {
      return parsePayloadSchema(schemaObj.schema, categoryCode);
    }
  }

  // Resilient fallback for standard categories if schema is empty or corrupted
  const normalizedCode = (categoryCode || '').toUpperCase();
  if (normalizedCode.includes('EXPENSE') || normalizedCode.includes('REIMBURSEMENT')) {
    return {
      properties: {
        expenseType: {
          type: 'string',
          title: 'Expense Type',
          enum: ['Conference & Training', 'Travel & Lodging', 'Team Lunch & Catering', 'Home Office Hardware'],
        },
        estimatedAmount: {
          type: 'number',
          title: 'Amount ($)',
          minimum: 1,
        },
        expenseDate: {
          type: 'string',
          title: 'Date of Expense',
          format: 'date',
        },
        receiptDetails: {
          type: 'string',
          title: 'Vendor & Receipt Notes',
        },
      },
      required: ['expenseType', 'estimatedAmount', 'expenseDate'],
    };
  }

  if (normalizedCode.includes('SOFTWARE') || normalizedCode.includes('LICENSE') || normalizedCode.includes('ACCESS')) {
    return {
      properties: {
        softwareName: {
          type: 'string',
          title: 'Software Tool',
          enum: ['JetBrains All Products Pack', 'GitHub Copilot Enterprise', 'AWS Dev Sandbox', 'Figma Enterprise', 'Datadog APM'],
        },
        licenseType: {
          type: 'string',
          title: 'License Term',
          enum: ['Annual', 'Monthly'],
        },
        estimatedAmount: {
          type: 'number',
          title: 'Estimated Cost ($)',
          minimum: 1,
        },
        justification: {
          type: 'string',
          title: 'Justification',
        },
      },
      required: ['softwareName', 'licenseType', 'estimatedAmount'],
    };
  }

  if (normalizedCode.includes('LAPTOP') || normalizedCode.includes('HARDWARE') || normalizedCode.includes('WORKSTATION')) {
    return {
      properties: {
        deviceModel: {
          type: 'string',
          title: 'Device Model',
          enum: ['MacBook Pro 16 M3 Max', 'Dell XPS 15 9530', 'Lenovo ThinkPad P1 Gen 6', 'Framework Laptop 16'],
        },
        ramGb: {
          type: 'integer',
          title: 'RAM (GB)',
          enum: [32, 64, 128],
        },
        estimatedAmount: {
          type: 'number',
          title: 'Estimated Amount ($)',
          minimum: 500,
        },
        urgencyReason: {
          type: 'string',
          title: 'Business Justification',
        },
      },
      required: ['deviceModel', 'ramGb', 'estimatedAmount', 'urgencyReason'],
    };
  }

  return null;
};

export const RequestsPage: React.FC = () => {
  const user = useAuthStore((s) => s.user);
  const canViewAllRequests = useMemo(() => {
    if (!user) return false;
    return (
      user.permissions?.includes('REQUEST_VIEW_ALL') ||
      user.permissions?.includes('ORG_ADMIN_MANAGE') ||
      user.roles?.includes('ORG_ADMIN')
    );
  }, [user]);

  const [viewScope, setViewScope] = useState<'my' | 'all'>('my');

  // Intelligent initial view scope: Operators and Admins default to organization-wide requests
  useEffect(() => {
    if (user?.roles?.includes('OPERATOR') || user?.roles?.includes('ORG_ADMIN')) {
      setViewScope('all');
    }
  }, [user?.roles]);
  const [requests, setRequests] = useState<RequestResponseDto[]>([]);
  const [categories, setCategories] = useState<RequestCategoryResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters & Search
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  // Selected Request for Drawer/Details
  const [selectedRequest, setSelectedRequest] = useState<RequestResponseDto | null>(null);
  const [workflowInstance, setWorkflowInstance] = useState<WorkflowInstanceResponseDto | null>(null);
  const [workflowNodes, setWorkflowNodes] = useState<NodeInstanceResponseDto[]>([]);
  const [loadingWorkflow, setLoadingWorkflow] = useState(false);
  const [comments, setComments] = useState<RequestCommentResponseDto[]>([]);
  const [loadingComments, setLoadingComments] = useState(false);
  const [newComment, setNewComment] = useState('');
  const [commentVisibility, setCommentVisibility] = useState<CommentVisibility>('REQUESTER_VISIBLE');
  const [submittingComment, setSubmittingComment] = useState(false);
  const [cancellingRequest, setCancellingRequest] = useState(false);
  const [submittingDraft, setSubmittingDraft] = useState(false);

  // New Request Wizard State
  const [isWizardOpen, setIsWizardOpen] = useState(false);
  const [wizardStep, setWizardStep] = useState<1 | 2>(1);
  const [selectedCategory, setSelectedCategory] = useState<RequestCategoryResponseDto | null>(null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<RequestPriority>('MEDIUM');
  const [dynamicPayload, setDynamicPayload] = useState<Record<string, any>>({});
  const [submittingRequest, setSubmittingRequest] = useState(false);
  const [wizardError, setWizardError] = useState<string | null>(null);

  const fetchCategories = async () => {
    try {
      const res = await apiClient.get<unknown, ApiResponse<RequestCategoryResponseDto[]>>('/request-categories');
      if (res.data) {
        setCategories(res.data);
      }
    } catch (err: unknown) {
      console.error('Failed to load request categories', err);
    }
  };

  const fetchRequests = async () => {
    setLoading(true);
    setError(null);
    try {
      const baseUrl = viewScope === 'all' && canViewAllRequests ? '/requests' : '/requests/my';
      let url = `${baseUrl}?page=0&size=50`;
      if (statusFilter !== 'ALL') {
        url += `&status=${statusFilter}`;
      }
      const res = await apiClient.get<unknown, ApiResponse<PageResponse<RequestResponseDto>>>(url);
      if (res.data) {
        setRequests(res.data.content);
      }
    } catch (err: unknown) {
      const errObj = err as { message?: string };
      setError(errObj.message || 'Failed to load requests.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCategories();
  }, []);

  useEffect(() => {
    fetchRequests();
  }, [statusFilter, viewScope]);

  const loadComments = async (requestId: string) => {
    setLoadingComments(true);
    try {
      const res = await apiClient.get<unknown, ApiResponse<RequestCommentResponseDto[]>>(
        `/requests/${requestId}/comments`
      );
      if (res.data) {
        setComments(res.data);
      }
    } catch (err: unknown) {
      console.error('Failed to load comments', err);
    } finally {
      setLoadingComments(false);
    }
  };

  const loadWorkflowInstance = async (requestId: string) => {
    setLoadingWorkflow(true);
    try {
      const res = await apiClient.get<unknown, ApiResponse<WorkflowInstanceResponseDto>>(
        `/workflow-instances/request/${requestId}`
      );
      if (res.data) {
        setWorkflowInstance(res.data);
        const nodesRes = await apiClient.get<unknown, ApiResponse<NodeInstanceResponseDto[]>>(
          `/workflow-instances/${res.data.id}/nodes`
        );
        if (nodesRes.data) {
          setWorkflowNodes(nodesRes.data);
        }
      } else {
        setWorkflowInstance(null);
        setWorkflowNodes([]);
      }
    } catch {
      setWorkflowInstance(null);
      setWorkflowNodes([]);
    } finally {
      setLoadingWorkflow(false);
    }
  };

  const handleSelectRequest = (req: RequestResponseDto) => {
    setSelectedRequest(req);
    loadComments(req.id);
    loadWorkflowInstance(req.id);
  };

  const handleAddComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedRequest || !newComment.trim()) return;

    setSubmittingComment(true);
    try {
      const res = await apiClient.post<unknown, ApiResponse<RequestCommentResponseDto>>(
        `/requests/${selectedRequest.id}/comments`,
        {
          content: newComment.trim(),
          visibility: commentVisibility,
        }
      );
      if (res.data) {
        setComments((prev) => [...prev, res.data!]);
        setNewComment('');
      }
    } catch (err: unknown) {
      const errObj = err as { message?: string };
      alert(errObj.message || 'Failed to post comment.');
    } finally {
      setSubmittingComment(false);
    }
  };

  const handleCancelRequest = async () => {
    if (!selectedRequest) return;
    if (!window.confirm(`Are you sure you want to cancel request ${selectedRequest.requestNumber}?`)) {
      return;
    }

    setCancellingRequest(true);
    try {
      const res = await apiClient.post<unknown, ApiResponse<RequestResponseDto>>(
        `/requests/${selectedRequest.id}/cancel`
      );
      if (res.data) {
        setSelectedRequest(res.data);
        await fetchRequests();
      }
    } catch (err: unknown) {
      const errObj = err as { message?: string };
      alert(errObj.message || 'Failed to cancel request.');
    } finally {
      setCancellingRequest(false);
    }
  };

  const handleSubmitDraft = async () => {
    if (!selectedRequest) return;
    setSubmittingDraft(true);
    try {
      const res = await apiClient.post<unknown, ApiResponse<RequestResponseDto>>(
        `/requests/${selectedRequest.id}/submit`
      );
      if (res.data) {
        setSelectedRequest(res.data);
        await fetchRequests();
        loadWorkflowInstance(res.data.id);
      }
    } catch (err: unknown) {
      const errObj = err as { message?: string };
      alert(errObj.message || 'Failed to submit request.');
    } finally {
      setSubmittingDraft(false);
    }
  };

  const handleOpenWizard = () => {
    setWizardStep(1);
    setSelectedCategory(null);
    setTitle('');
    setDescription('');
    setPriority('MEDIUM');
    setDynamicPayload({});
    setWizardError(null);
    setIsWizardOpen(true);
  };

  const handleCategoryChosen = (category: RequestCategoryResponseDto) => {
    setSelectedCategory(category);
    setWizardError(null);

    // Parse payload schema safely (handles both JSON string and object, with built-in fallback)
    const schema = parsePayloadSchema(category.payloadSchema, category.code || category.key);
    const initialPayload: Record<string, any> = {};

    if (schema?.properties) {
      const properties = schema.properties;
      Object.keys(properties).forEach((propKey) => {
        const prop = properties[propKey];
        const keyLower = propKey.toLowerCase();
        const titleLower = (prop.title || '').toLowerCase();

        if (prop.enum && Array.isArray(prop.enum) && prop.enum.length > 0) {
          initialPayload[propKey] = prop.enum[0];
        } else if (prop.type === 'number' || prop.type === 'integer') {
          initialPayload[propKey] =
            prop.minimum !== undefined
              ? prop.minimum
              : (keyLower.includes('amount') || keyLower.includes('cost') ? 100 : 0);
        } else if (prop.format === 'date' || keyLower.includes('date') || titleLower.includes('date')) {
          initialPayload[propKey] = new Date().toISOString().split('T')[0];
        } else {
          initialPayload[propKey] = '';
        }
      });
    }

    setDynamicPayload(initialPayload);
    setWizardStep(2);
  };

  const handlePayloadFieldChange = (key: string, value: any) => {
    setDynamicPayload((prev) => ({
      ...prev,
      [key]: value,
    }));
  };

  const handleCreateRequest = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedCategory) return;

    setWizardError(null);

    const categoryCode = selectedCategory.code || selectedCategory.key;
    const schema = parsePayloadSchema(selectedCategory.payloadSchema, categoryCode);
    const cleanedPayload: Record<string, any> = {};

    if (schema?.properties) {
      const properties = schema.properties;
      Object.keys(properties).forEach((k) => {
        const schemaProp = properties[k];
        const val = dynamicPayload[k];
        if ((schemaProp.type === 'number' || schemaProp.type === 'integer') && val !== '' && val !== undefined) {
          cleanedPayload[k] = Number(val);
        } else {
          cleanedPayload[k] = val;
        }
      });
    }

    // Client-side required fields validation
    if (schema?.required) {
      const missingLabels: string[] = [];
      const properties = schema.properties || {};
      for (const reqKey of schema.required) {
        const val = cleanedPayload[reqKey];
        if (val === undefined || val === null || val === '') {
          const propTitle = properties[reqKey]?.title || reqKey;
          missingLabels.push(propTitle);
        }
      }
      if (missingLabels.length > 0) {
        setWizardError(`Please provide all required specification fields: ${missingLabels.join(', ')}`);
        return;
      }
    }

    setSubmittingRequest(true);

    try {
      const res = await apiClient.post<unknown, ApiResponse<RequestResponseDto>>('/requests', {
        categoryId: selectedCategory.id,
        title: title.trim(),
        description: description.trim() || undefined,
        priority,
        payload: cleanedPayload,
        autoSubmit: true,
      });

      if (res.data) {
        setIsWizardOpen(false);
        await fetchRequests();
        handleSelectRequest(res.data);
      }
    } catch (err: unknown) {
      const errObj = err as { message?: string; details?: Array<{ field: string; issue: string }> };
      if (errObj.details && errObj.details.length > 0) {
        setWizardError(errObj.details.map((d) => `${d.field}: ${d.issue}`).join(', '));
      } else {
        setWizardError(errObj.message || 'Failed to submit request.');
      }
    } finally {
      setSubmittingRequest(false);
    }
  };

  const filteredRequests = useMemo(() => {
    return requests.filter((r) => {
      const q = (searchQuery || '').toLowerCase();
      const matchesSearch =
        (r.requestNumber || '').toLowerCase().includes(q) ||
        (r.title || '').toLowerCase().includes(q) ||
        (r.categoryName || '').toLowerCase().includes(q) ||
        (r.requesterName || '').toLowerCase().includes(q);
      return matchesSearch;
    });
  }, [requests, searchQuery]);

  const getStatusBadge = (status: RequestStatus) => {
    switch (status) {
      case 'SUBMITTED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800 border border-blue-200">
            <Clock className="h-3 w-3" /> Submitted
          </span>
        );
      case 'IN_REVIEW':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-800 border border-amber-200">
            <Clock className="h-3 w-3" /> In Review
          </span>
        );
      case 'APPROVED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-100 text-emerald-800 border border-emerald-200">
            <CheckCircle2 className="h-3 w-3" /> Approved
          </span>
        );
      case 'IN_PROGRESS':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-indigo-100 text-indigo-800 border border-indigo-200">
            <RefreshCw className="h-3 w-3 animate-spin" /> In Progress
          </span>
        );
      case 'COMPLETED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-emerald-100 text-emerald-800 border border-emerald-200">
            <CheckCircle2 className="h-3 w-3" /> Completed
          </span>
        );
      case 'REJECTED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-rose-100 text-rose-800 border border-rose-200">
            <XCircle className="h-3 w-3" /> Rejected
          </span>
        );
      case 'CANCELLED':
        return (
          <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-700 border border-slate-300">
            <XCircle className="h-3 w-3" /> Cancelled
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-700 border border-slate-200">
            {status}
          </span>
        );
    }
  };

  const getPriorityBadge = (p: RequestPriority) => {
    switch (p) {
      case 'URGENT':
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold bg-rose-50 text-rose-700 border border-rose-200">
            <AlertTriangle className="h-3 w-3 text-rose-600" /> URGENT
          </span>
        );
      case 'HIGH':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-orange-50 text-orange-700 border border-orange-200">
            HIGH
          </span>
        );
      case 'MEDIUM':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-slate-100 text-slate-700 border border-slate-200">
            MEDIUM
          </span>
        );
      case 'LOW':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-slate-50 text-slate-500 border border-slate-200">
            LOW
          </span>
        );
    }
  };

  return (
    <div className="space-y-6">
      <SEO title="Operations Requests" noIndex={true} />
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-4 border-b border-slate-200">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
            <FileText className="h-6 w-6 text-indigo-600" />
            Operations Requests
          </h1>
          <p className="text-sm text-slate-500 mt-0.5">
            Submit, track, and orchestrate employee resource and procurement requests.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {canViewAllRequests && (
            <div className="flex items-center bg-slate-100 p-1 rounded-xl border border-slate-200 text-xs font-semibold mr-1 sm:mr-2">
              <button
                type="button"
                onClick={() => setViewScope('my')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all ${
                  viewScope === 'my'
                    ? 'bg-white text-indigo-700 shadow-2xs font-bold'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <User className="h-3.5 w-3.5" />
                <span>My Requests</span>
              </button>
              <button
                type="button"
                onClick={() => setViewScope('all')}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg transition-all ${
                  viewScope === 'all'
                    ? 'bg-white text-indigo-700 shadow-2xs font-bold'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <Users className="h-3.5 w-3.5" />
                <span>All Requests</span>
              </button>
            </div>
          )}
          <button
            onClick={() => {
              fetchCategories();
              fetchRequests();
            }}
            disabled={loading}
            className="p-2 text-slate-500 hover:text-slate-700 hover:bg-slate-100 rounded-lg border border-slate-200 transition-colors"
            title="Refresh requests"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
          <button
            onClick={handleOpenWizard}
            className="flex items-center gap-2 px-3 sm:px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-xs sm:text-sm font-semibold shadow-sm transition-colors"
          >
            <Plus className="h-4 w-4" />
            <span>New Request</span>
          </button>
        </div>
      </div>

      {/* Global Error Banner */}
      {error && (
        <div className="p-4 bg-rose-50 border border-rose-200 rounded-xl flex items-start gap-3 text-rose-800 text-sm">
          <AlertCircle className="h-5 w-5 text-rose-600 shrink-0 mt-0.5" />
          <div className="flex-1">
            <p className="font-semibold">Unable to load requests</p>
            <p className="text-rose-700 mt-0.5">{error}</p>
          </div>
          <button onClick={fetchRequests} className="text-rose-600 font-semibold hover:underline text-xs">
            Retry
          </button>
        </div>
      )}

      {/* Filters & Search Controls */}
      <div className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4">
        {/* Status Filter Tabs */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 md:pb-0 text-xs font-medium text-slate-600 no-scrollbar">
          {[
            { label: 'All', value: 'ALL' },
            { label: 'Draft', value: 'DRAFT' },
            { label: 'Submitted', value: 'SUBMITTED' },
            { label: 'In Review', value: 'IN_REVIEW' },
            { label: 'Approved', value: 'APPROVED' },
            { label: 'In Progress', value: 'IN_PROGRESS' },
            { label: 'Completed', value: 'COMPLETED' },
            { label: 'Cancelled', value: 'CANCELLED' },
          ].map((tab) => (
            <button
              key={tab.value}
              onClick={() => setStatusFilter(tab.value)}
              className={`px-3 py-1.5 rounded-lg whitespace-nowrap transition-colors ${
                statusFilter === tab.value
                  ? 'bg-indigo-50 text-indigo-700 font-semibold border border-indigo-200'
                  : 'hover:bg-slate-100 text-slate-600 border border-transparent'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Search Input */}
        <div className="relative w-full md:w-64 lg:w-80">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            placeholder="Search number, title, or requester..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-3 py-1.5 text-xs bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
          />
        </div>
      </div>

      {/* Requests Table */}
      <div className="bg-white rounded-xl border border-slate-200 overflow-hidden shadow-sm">
        <div className="p-4 border-b border-slate-200 flex items-center justify-between bg-slate-50/50">
          <div className="flex items-center gap-2 text-xs font-medium text-slate-600">
            <Filter className="h-3.5 w-3.5 text-slate-400" />
            <span>
              Showing {filteredRequests.length} of {requests.length} requests
            </span>
          </div>
        </div>

        {loading ? (
          <div className="p-12 text-center text-slate-500">
            <RefreshCw className="h-6 w-6 animate-spin mx-auto text-indigo-600 mb-2" />
            <p className="text-sm font-medium">Loading requests...</p>
          </div>
        ) : filteredRequests.length === 0 ? (
          <div className="p-12 text-center">
            <FileText className="h-10 w-10 text-slate-300 mx-auto mb-3" />
            <p className="text-sm font-semibold text-slate-700">No requests found</p>
            <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
              {searchQuery
                ? 'No requests match your current search query.'
                : 'No operations requests have been submitted in this status category yet.'}
            </p>
            <button
              onClick={handleOpenWizard}
              className="mt-4 inline-flex items-center gap-2 px-3 py-1.5 bg-indigo-50 text-indigo-700 hover:bg-indigo-100 rounded-lg text-xs font-semibold transition-colors"
            >
              <Plus className="h-3.5 w-3.5" />
              <span>Create First Request</span>
            </button>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[700px] text-left text-sm text-slate-600">
              <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wider text-slate-500 border-b border-slate-200">
                <tr>
                  <th className="px-6 py-3">Number</th>
                  <th className="px-6 py-3">Category</th>
                  <th className="px-6 py-3">Title</th>
                  <th className="px-6 py-3">Priority</th>
                  <th className="px-6 py-3">Status</th>
                  <th className="px-6 py-3">Requester</th>
                  <th className="px-6 py-3">Submitted</th>
                  <th className="px-6 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200">
                {filteredRequests.map((req) => (
                  <tr
                    key={req.id}
                    onClick={() => handleSelectRequest(req)}
                    className="hover:bg-slate-50 transition-colors cursor-pointer group"
                  >
                    <td className="px-6 py-4 font-mono font-bold text-indigo-600 group-hover:underline">
                      {req.requestNumber}
                    </td>
                    <td className="px-6 py-4">
                      <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium bg-slate-100 text-slate-800 border border-slate-200">
                        {getCategoryIcon(req.categoryCode || req.categoryKey, 'sm')}
                        {req.categoryName}
                      </span>
                    </td>
                    <td className="px-6 py-4 font-medium text-slate-900 max-w-xs truncate">{req.title}</td>
                    <td className="px-6 py-4">{getPriorityBadge(req.priority)}</td>
                    <td className="px-6 py-4">{getStatusBadge(req.status)}</td>
                    <td className="px-6 py-4">
                      <div className="text-xs">
                        <div className="font-medium text-slate-900">{req.requesterName}</div>
                        <div className="text-slate-400">{req.departmentName || 'General'}</div>
                      </div>
                    </td>
                    <td className="px-6 py-4 text-xs text-slate-500">
                      {req.submittedAt ? new Date(req.submittedAt).toLocaleDateString() : 'Draft'}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          handleSelectRequest(req);
                        }}
                        className="p-1.5 text-slate-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-colors"
                        title="View details"
                      >
                        <Eye className="h-4 w-4" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ========================================================= */}
      {/* REQUEST DETAIL DRAWER / MODAL                             */}
      {/* ========================================================= */}
      {selectedRequest && (
        <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/50 backdrop-blur-sm flex justify-end">
          <div className="w-full max-w-2xl bg-white h-full shadow-2xl flex flex-col overflow-hidden animate-in slide-in-from-right duration-200">
            {/* Drawer Header */}
            <div className="p-6 border-b border-slate-200 flex items-start justify-between bg-slate-50/70">
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-mono text-lg font-bold text-indigo-600">
                    {selectedRequest.requestNumber}
                  </span>
                  {getStatusBadge(selectedRequest.status)}
                  {getPriorityBadge(selectedRequest.priority)}
                </div>
                <h2 className="text-xl font-bold text-slate-900 mt-2">{selectedRequest.title}</h2>
                <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-xs text-slate-500 mt-2">
                  <span className="flex items-center gap-1">
                    <User className="h-3.5 w-3.5" />
                    {selectedRequest.requesterName} ({selectedRequest.requesterEmail})
                  </span>
                  <span className="flex items-center gap-1">
                    <Building className="h-3.5 w-3.5" />
                    {selectedRequest.departmentName || 'No Department'}
                  </span>
                  <span className="flex items-center gap-1">
                    <Calendar className="h-3.5 w-3.5" />
                    {selectedRequest.submittedAt
                      ? new Date(selectedRequest.submittedAt).toLocaleString()
                      : 'Not submitted'}
                  </span>
                </div>
              </div>
              <button
                onClick={() => setSelectedRequest(null)}
                className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {/* Drawer Content */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6">
              {/* Description */}
              {selectedRequest.description && (
                <div>
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500 mb-1.5">
                    Justification / Description
                  </h3>
                  <div className="p-3 bg-slate-50 border border-slate-200 rounded-lg text-sm text-slate-700 whitespace-pre-wrap">
                    {selectedRequest.description}
                  </div>
                </div>
              )}

              {/* Dynamic Payload Attributes */}
              <div>
                <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500 mb-2 flex items-center gap-1.5">
                  <Layers className="h-4 w-4 text-indigo-600" />
                  Category Details ({selectedRequest.categoryName})
                </h3>
                {selectedRequest.payload && Object.keys(selectedRequest.payload).length > 0 ? (
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 bg-slate-50 border border-slate-200 rounded-xl p-4">
                    {Object.entries(selectedRequest.payload).map(([k, v]) => (
                      <div key={k} className="p-2.5 bg-white rounded-lg border border-slate-200 shadow-2xs">
                        <span className="text-xs font-medium text-slate-400 block capitalize">
                          {k.replace(/([A-Z])/g, ' $1')}
                        </span>
                        <span className="text-sm font-semibold text-slate-800 mt-0.5 block">
                          {typeof v === 'number' && k?.toLowerCase().includes('amount')
                            ? `$${v.toLocaleString()}`
                            : String(v)}
                        </span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-slate-400 italic">No structured attributes specified.</p>
                )}
              </div>

              {/* Workflow Execution Lifecycle */}
              <div>
                <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500 mb-2 flex items-center justify-between">
                  <span className="flex items-center gap-1.5">
                    <GitFork className="h-4 w-4 text-indigo-600" />
                    Workflow Orchestration Audit Trail
                  </span>
                  {workflowInstance && (
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-200">
                      Status: {workflowInstance.status}
                    </span>
                  )}
                </h3>

                {loadingWorkflow ? (
                  <div className="p-4 text-center text-xs text-slate-400">Loading workflow state...</div>
                ) : !workflowInstance ? (
                  <p className="text-xs text-slate-400 italic">No automated workflow active for this request.</p>
                ) : (
                  <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 space-y-3">
                    <div className="flex items-center justify-between text-xs text-slate-500 pb-2 border-b border-slate-200/80">
                      <span>Currently at node: <strong className="font-mono text-indigo-600">{workflowInstance.currentNodeKey || 'START'}</strong></span>
                      <span>Attempt #{workflowInstance.attemptNumber}</span>
                    </div>

                    <div className="space-y-2">
                      {workflowNodes.map((n, idx) => (
                        <div key={n.id} className="flex items-center justify-between text-xs bg-white p-2.5 rounded-lg border border-slate-200 shadow-2xs">
                          <div className="flex items-center gap-2">
                            <span className="w-5 h-5 rounded-full flex items-center justify-center font-bold text-[10px] bg-indigo-100 text-indigo-700">
                              {idx + 1}
                            </span>
                            <div>
                              <div className="font-semibold text-slate-800 flex items-center gap-1.5">
                                {n.title}
                                <span className="font-mono text-[10px] text-slate-400">({n.nodeKey})</span>
                              </div>
                              <span className="text-[10px] text-slate-400">
                                {new Date(n.enteredAt).toLocaleTimeString()}
                              </span>
                            </div>
                          </div>
                          <div>
                            {n.status === 'COMPLETED' ? (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                                <CheckCircle2 className="h-3 w-3 text-emerald-600" /> Completed
                              </span>
                            ) : n.status === 'WAITING' ? (
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold bg-blue-50 text-blue-700 border border-blue-200 animate-pulse">
                                <Clock className="h-3 w-3 text-blue-600" /> Awaiting Action
                              </span>
                            ) : (
                              <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-slate-100 text-slate-600">
                                {n.status}
                              </span>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {/* Draft Submission Banner */}
              {selectedRequest.status === 'DRAFT' && (
                <div className="p-4 bg-indigo-50 border border-indigo-200 rounded-xl flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-2xs">
                  <div>
                    <h4 className="text-sm font-semibold text-indigo-950 flex items-center gap-1.5">
                      <Send className="h-4 w-4 text-indigo-600" />
                      Draft Requisition Ready for Submission
                    </h4>
                    <p className="text-xs text-indigo-700 mt-0.5">
                      Submit this request to initiate automated workflow orchestration and approver routing.
                    </p>
                  </div>
                  <button
                    onClick={handleSubmitDraft}
                    disabled={submittingDraft}
                    className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-xs font-semibold shadow-sm transition-all flex items-center justify-center gap-2 shrink-0 disabled:opacity-50"
                  >
                    {submittingDraft ? (
                      <>
                        <RefreshCw className="h-3.5 w-3.5 animate-spin" />
                        <span>Submitting...</span>
                      </>
                    ) : (
                      <>
                        <Send className="h-3.5 w-3.5" />
                        <span>Submit for Approval</span>
                      </>
                    )}
                  </button>
                </div>
              )}

              {/* Cancellation Option */}
              {['DRAFT', 'SUBMITTED', 'IN_REVIEW'].includes(selectedRequest.status) && (
                <div className="p-4 bg-amber-50 border border-amber-200 rounded-xl flex items-center justify-between">
                  <div>
                    <h4 className="text-sm font-semibold text-amber-900">Need to cancel this request?</h4>
                    <p className="text-xs text-amber-700 mt-0.5">
                      You can cancel this request while it is still awaiting approvals.
                    </p>
                  </div>
                  <button
                    onClick={handleCancelRequest}
                    disabled={cancellingRequest}
                    className="px-3 py-1.5 bg-white hover:bg-rose-50 text-rose-600 border border-rose-200 hover:border-rose-300 rounded-lg text-xs font-semibold transition-colors shadow-2xs"
                  >
                    {cancellingRequest ? 'Cancelling...' : 'Cancel Request'}
                  </button>
                </div>
              )}

              {/* Comments Timeline */}
              <div className="pt-4 border-t border-slate-200">
                <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-500 mb-3 flex items-center gap-1.5">
                  <MessageSquare className="h-4 w-4 text-indigo-600" />
                  Activity &amp; Comments
                </h3>

                {loadingComments ? (
                  <div className="p-4 text-center text-xs text-slate-400">Loading comments...</div>
                ) : comments.length === 0 ? (
                  <p className="text-xs text-slate-400 italic mb-4">No comments or activity notes yet.</p>
                ) : (
                  <div className="space-y-3 mb-4">
                    {comments.map((c) => (
                      <div
                        key={c.id}
                        className={`p-3 rounded-lg border text-xs ${
                          c.visibility === 'INTERNAL'
                            ? 'bg-amber-50/60 border-amber-200 text-amber-900'
                            : 'bg-slate-50 border-slate-200 text-slate-800'
                        }`}
                      >
                        <div className="flex items-center justify-between mb-1.5">
                          <span className="font-semibold flex items-center gap-1.5">
                            {c.authorName}
                            {c.visibility === 'INTERNAL' && (
                              <span className="inline-flex items-center gap-0.5 px-1.5 py-0.5 rounded text-[10px] font-bold bg-amber-200 text-amber-800">
                                <Lock className="h-2.5 w-2.5" /> INTERNAL NOTE
                              </span>
                            )}
                          </span>
                          <span className="text-[10px] text-slate-400">
                            {new Date(c.createdAt).toLocaleString()}
                          </span>
                        </div>
                        <p className="whitespace-pre-wrap">{c.content}</p>
                      </div>
                    ))}
                  </div>
                )}

                {/* Post New Comment Form */}
                <form onSubmit={handleAddComment} className="space-y-3 bg-slate-50 p-3 rounded-xl border border-slate-200">
                  <textarea
                    rows={2}
                    value={newComment}
                    onChange={(e) => setNewComment(e.target.value)}
                    placeholder="Add an update or question..."
                    className="w-full text-xs p-2.5 bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                  />
                  <div className="flex items-center justify-between">
                    <label className="flex items-center gap-1.5 text-xs text-slate-600 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={commentVisibility === 'INTERNAL'}
                        onChange={(e) =>
                          setCommentVisibility(e.target.checked ? 'INTERNAL' : 'REQUESTER_VISIBLE')
                        }
                        className="rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
                      />
                      <span className="flex items-center gap-1">
                        <Lock className="h-3 w-3 text-slate-400" />
                        Internal note (operators &amp; approvers only)
                      </span>
                    </label>
                    <button
                      type="submit"
                      disabled={submittingComment || !newComment.trim()}
                      className="flex items-center gap-1.5 px-3 py-1.5 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-lg text-xs font-semibold transition-colors"
                    >
                      <Send className="h-3 w-3" />
                      <span>{submittingComment ? 'Posting...' : 'Comment'}</span>
                    </button>
                  </div>
                </form>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ========================================================= */}
      {/* NEW REQUEST WIZARD MODAL                                  */}
      {/* ========================================================= */}
      {isWizardOpen && (
        <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/50 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-2xl bg-white rounded-2xl shadow-xl overflow-hidden border border-slate-200 animate-in fade-in zoom-in-95 duration-150">
            {/* Modal Header */}
            <div className="p-6 border-b border-slate-200 flex items-center justify-between bg-slate-50/50">
              <div>
                <h2 className="text-xl font-bold text-slate-900 tracking-tight">Create Operations Request</h2>
                <p className="text-xs text-slate-500 mt-0.5">
                  {wizardStep === 1
                    ? 'Step 1: Select request category'
                    : `Step 2: Enter details for ${selectedCategory?.name}`}
                </p>
              </div>
              <button
                onClick={() => setIsWizardOpen(false)}
                className="p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {/* Wizard Body */}
            {wizardStep === 1 ? (
              /* Step 1: Category Selection */
              <div className="p-4 sm:p-6 space-y-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3 sm:gap-4">
                  {categories.map((cat) => (
                    <button
                      key={cat.id}
                      onClick={() => handleCategoryChosen(cat)}
                      className="p-4 sm:p-5 border border-slate-200 hover:border-indigo-400 hover:bg-indigo-50/30 rounded-xl text-left transition-all group flex flex-col justify-between"
                    >
                      <div>
                        <div className="p-3 bg-slate-100 group-hover:bg-white rounded-lg w-fit transition-colors mb-3">
                          {getCategoryIcon(cat.code || cat.key, 'lg')}
                        </div>
                        <h3 className="font-bold text-slate-900 text-sm group-hover:text-indigo-600 transition-colors">
                          {cat.name}
                        </h3>
                        <p className="text-xs text-slate-500 mt-1 line-clamp-3 leading-relaxed">
                          {cat.description || 'Submit resources under this operational category.'}
                        </p>
                      </div>
                      <div className="mt-4 pt-3 border-t border-slate-100 flex items-center justify-between text-xs font-semibold text-indigo-600">
                        <span>Select</span>
                        <ChevronRight className="h-4 w-4 group-hover:translate-x-1 transition-transform" />
                      </div>
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              /* Step 2: Dynamic Form */
              <form onSubmit={handleCreateRequest} className="p-4 sm:p-6 space-y-4 sm:space-y-5">
                {wizardError && (
                  <div className="p-3 bg-rose-50 border border-rose-200 rounded-lg flex items-center gap-2 text-rose-800 text-xs">
                    <AlertCircle className="h-4 w-4 shrink-0 text-rose-600" />
                    <span>{wizardError}</span>
                  </div>
                )}

                {/* Title & Priority Row */}
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 sm:gap-4">
                  <div className="sm:col-span-2">
                    <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                      Request Title *
                    </label>
                    <input
                      type="text"
                      required
                      placeholder="e.g., MacBook Pro 16 M3 Max for Senior Staff"
                      value={title}
                      onChange={(e) => setTitle(e.target.value)}
                      className="w-full text-sm px-3 py-2 bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                      Priority *
                    </label>
                    <select
                      value={priority}
                      onChange={(e) => setPriority(e.target.value as RequestPriority)}
                      className="w-full text-sm px-3 py-2 bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                    >
                      <option value="LOW">Low</option>
                      <option value="MEDIUM">Medium</option>
                      <option value="HIGH">High</option>
                      <option value="URGENT">Urgent</option>
                    </select>
                  </div>
                </div>

                {/* Description / Business Justification */}
                <div>
                  <label className="block text-xs font-semibold text-slate-700 uppercase tracking-wider mb-1">
                    Business Justification
                  </label>
                  <textarea
                    rows={2}
                    placeholder="Provide context or project rationale for approvers..."
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    className="w-full text-sm px-3 py-2 bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                  />
                </div>

                {/* Dynamic JSON Schema Fields */}
                {(() => {
                  const schema = parsePayloadSchema(
                    selectedCategory?.payloadSchema,
                    selectedCategory?.code || (selectedCategory as any)?.key
                  );
                  if (!schema?.properties || Object.keys(schema.properties).length === 0) {
                    return null;
                  }

                  const properties = schema.properties;

                  return (
                    <div className="p-4 bg-slate-50 border border-slate-200 rounded-xl space-y-3 sm:space-y-4">
                      <div className="flex items-center justify-between">
                        <h4 className="text-xs font-bold text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
                          <Layers className="h-4 w-4 text-indigo-600" />
                          {selectedCategory?.name || 'Category'} Specifications
                        </h4>
                        <span className="text-[11px] text-slate-500 font-medium">
                          Fill required specification fields
                        </span>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4">
                        {Object.keys(properties).map((key) => {
                          const prop = properties[key];
                          const isRequired = schema.required?.includes(key);
                          const keyLower = key.toLowerCase();
                          const titleLower = (prop.title || '').toLowerCase();
                          const displayLabel = prop.title || key.replace(/([A-Z])/g, ' $1');

                          // Enum dropdown (handles both numeric and string options)
                          if (prop.enum && Array.isArray(prop.enum)) {
                            return (
                              <div key={key}>
                                <label className="block text-xs font-semibold text-slate-700 mb-1 capitalize">
                                  {displayLabel} {isRequired && <span className="text-rose-500">*</span>}
                                </label>
                                <select
                                  required={isRequired}
                                  value={dynamicPayload[key] ?? ''}
                                  onChange={(e) => {
                                    const raw = e.target.value;
                                    const val = (prop.type === 'number' || prop.type === 'integer') ? Number(raw) : raw;
                                    handlePayloadFieldChange(key, val);
                                  }}
                                  className="w-full text-xs px-3 py-2 bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 font-medium text-slate-800"
                                >
                                  {prop.enum.map((opt: any) => (
                                    <option key={String(opt)} value={opt}>
                                      {String(opt)}
                                    </option>
                                  ))}
                                </select>
                              </div>
                            );
                          }

                          // Number / Currency input
                          if (prop.type === 'number' || prop.type === 'integer') {
                            const isCurrency = keyLower.includes('amount') || keyLower.includes('cost') || keyLower.includes('price');
                            return (
                              <div key={key}>
                                <label className="block text-xs font-semibold text-slate-700 mb-1 capitalize">
                                  {displayLabel} {isCurrency ? '($)' : ''} {isRequired && <span className="text-rose-500">*</span>}
                                </label>
                                <div className="relative">
                                  {isCurrency && (
                                    <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-xs font-bold">$</span>
                                  )}
                                  <input
                                    type="number"
                                    required={isRequired}
                                    min={prop.minimum ?? 0}
                                    max={prop.maximum}
                                    step={prop.type === 'integer' ? '1' : 'any'}
                                    value={dynamicPayload[key] ?? ''}
                                    onChange={(e) => handlePayloadFieldChange(key, e.target.value === '' ? '' : Number(e.target.value))}
                                    className={`w-full text-xs py-2 pr-3 bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 font-medium text-slate-800 ${
                                      isCurrency ? 'pl-7' : 'px-3'
                                    }`}
                                  />
                                </div>
                              </div>
                            );
                          }

                          // Date picker input
                          if (prop.format === 'date' || keyLower.includes('date') || titleLower.includes('date')) {
                            return (
                              <div key={key}>
                                <label className="block text-xs font-semibold text-slate-700 mb-1 capitalize">
                                  {displayLabel} {isRequired && <span className="text-rose-500">*</span>}
                                </label>
                                <input
                                  type="date"
                                  required={isRequired}
                                  value={dynamicPayload[key] ?? ''}
                                  onChange={(e) => handlePayloadFieldChange(key, e.target.value)}
                                  className="w-full text-xs px-3 py-2 bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 font-medium text-slate-800"
                                />
                              </div>
                            );
                          }

                          // Multi-line text for details, justification, notes
                          const isMultiline = keyLower.includes('reason') || keyLower.includes('justification') || keyLower.includes('details') || keyLower.includes('notes');
                          if (isMultiline) {
                            return (
                              <div key={key} className="sm:col-span-2">
                                <label className="block text-xs font-semibold text-slate-700 mb-1 capitalize">
                                  {displayLabel} {isRequired && <span className="text-rose-500">*</span>}
                                </label>
                                <textarea
                                  rows={2}
                                  required={isRequired}
                                  placeholder={`Provide ${displayLabel.toLowerCase()}...`}
                                  value={dynamicPayload[key] ?? ''}
                                  onChange={(e) => handlePayloadFieldChange(key, e.target.value)}
                                  className="w-full text-xs px-3 py-2 bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 font-medium text-slate-800"
                                />
                              </div>
                            );
                          }

                          // Default string input
                          return (
                            <div key={key}>
                              <label className="block text-xs font-semibold text-slate-700 mb-1 capitalize">
                                {displayLabel} {isRequired && <span className="text-rose-500">*</span>}
                              </label>
                              <input
                                type="text"
                                required={isRequired}
                                placeholder={`Enter ${displayLabel.toLowerCase()}...`}
                                value={dynamicPayload[key] ?? ''}
                                onChange={(e) => handlePayloadFieldChange(key, e.target.value)}
                                className="w-full text-xs px-3 py-2 bg-white border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 font-medium text-slate-800"
                              />
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  );
                })()}

                {/* Footer Controls */}
                <div className="pt-4 border-t border-slate-200 flex items-center justify-between">
                  <button
                    type="button"
                    onClick={() => setWizardStep(1)}
                    className="px-4 py-2 border border-slate-200 hover:bg-slate-50 text-slate-600 rounded-lg text-xs font-semibold transition-colors"
                  >
                    Back to Categories
                  </button>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => setIsWizardOpen(false)}
                      className="px-4 py-2 text-slate-500 hover:text-slate-700 text-xs font-semibold"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={submittingRequest}
                      className="flex items-center gap-1.5 px-5 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-lg text-xs font-semibold transition-colors shadow-sm"
                    >
                      <span>{submittingRequest ? 'Submitting...' : 'Submit Request'}</span>
                      <ArrowRight className="h-3.5 w-3.5" />
                    </button>
                  </div>
                </div>
              </form>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
