import React, { useEffect, useState, useMemo } from 'react';
import {
  CheckCircle2,
  Clock,
  Search,
  RefreshCw,
  AlertCircle,
  User,
  Calendar,
  ChevronRight,
  UserCheck,
  Send,
  AlertTriangle,
  PackageCheck,
  MessageSquare,
  History,
  XCircle,
  FileCheck2,
  Tag
} from 'lucide-react';
import { apiClient } from '@/lib/api-client';
import {
  ApiResponse,
  TaskResponseDto,
  TaskStatus,
  CompleteTaskDto,
  UpdateTaskStatusDto,
  AddTaskCommentDto
} from '@/types/api';

export const TasksPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'QUEUE' | 'MY'>('QUEUE');
  const [tasks, setTasks] = useState<TaskResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Search & Filter
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');

  // Selected Task Drawer / Modal
  const [selectedTask, setSelectedTask] = useState<TaskResponseDto | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  // Fulfillment Output Form State
  const [serialNumber, setSerialNumber] = useState('');
  const [carrier, setCarrier] = useState('');
  const [trackingNumber, setTrackingNumber] = useState('');
  const [fulfillmentNotes, setFulfillmentNotes] = useState('');

  // Comment State
  const [newComment, setNewComment] = useState('');
  const [submittingComment, setSubmittingComment] = useState(false);

  const fetchTasks = async () => {
    setLoading(true);
    setError(null);
    try {
      const endpoint = activeTab === 'QUEUE' ? '/tasks/queue' : '/tasks/my';
      const res = await apiClient.get<unknown, ApiResponse<TaskResponseDto[]>>(endpoint);
      if (res.data) {
        setTasks(res.data);
      }
    } catch (err: any) {
      console.error('Failed to load tasks:', err);
      setError(err?.message || 'Failed to load fulfillment tasks.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTasks();
  }, [activeTab]);

  const handleOpenTask = (task: TaskResponseDto) => {
    setSelectedTask(task);
    setActionError(null);
    setSerialNumber(task.outputData?.serialNumber || '');
    setCarrier(task.outputData?.carrier || '');
    setTrackingNumber(task.outputData?.trackingNumber || '');
    setFulfillmentNotes('');
    setNewComment('');
  };

  const handleClaimTask = async (taskId: string) => {
    setActionLoading(true);
    setActionError(null);
    try {
      const res = await apiClient.post<unknown, ApiResponse<TaskResponseDto>>(`/tasks/${taskId}/claim`);
      if (res.data) {
        await fetchTasks();
        if (selectedTask?.id === taskId) {
          setSelectedTask(res.data);
        }
      }
    } catch (err: any) {
      setActionError(err?.message || 'Failed to claim task.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleUpdateStatus = async (status: TaskStatus) => {
    if (!selectedTask) return;
    setActionLoading(true);
    setActionError(null);
    try {
      const dto: UpdateTaskStatusDto = {
        status,
        notes: fulfillmentNotes.trim() || undefined
      };
      const res = await apiClient.patch<unknown, ApiResponse<TaskResponseDto>>(
        `/tasks/${selectedTask.id}/status`,
        dto
      );
      if (res.data) {
        await fetchTasks();
        setSelectedTask(res.data);
      }
    } catch (err: any) {
      setActionError(err?.message || 'Failed to update task status.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleCompleteTask = async () => {
    if (!selectedTask) return;
    setActionLoading(true);
    setActionError(null);

    const outputData: Record<string, any> = {};
    if (serialNumber.trim()) outputData.serialNumber = serialNumber.trim();
    if (carrier.trim()) outputData.carrier = carrier.trim();
    if (trackingNumber.trim()) outputData.trackingNumber = trackingNumber.trim();

    const dto: CompleteTaskDto = {
      outputData,
      notes: fulfillmentNotes.trim() || undefined
    };

    try {
      const res = await apiClient.post<unknown, ApiResponse<TaskResponseDto>>(
        `/tasks/${selectedTask.id}/complete`,
        dto
      );
      if (res.data) {
        await fetchTasks();
        setSelectedTask(null);
      }
    } catch (err: any) {
      setActionError(err?.message || 'Failed to complete task fulfillment.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleAddComment = async () => {
    if (!selectedTask || !newComment.trim()) return;
    setSubmittingComment(true);
    try {
      const dto: AddTaskCommentDto = { content: newComment.trim() };
      await apiClient.post(`/tasks/${selectedTask.id}/comments`, dto);
      setNewComment('');
      // Reload full task details
      const refreshed = await apiClient.get<unknown, ApiResponse<TaskResponseDto>>(`/tasks/${selectedTask.id}`);
      if (refreshed.data) {
        setSelectedTask(refreshed.data);
      }
    } catch (err: any) {
      setActionError(err?.message || 'Failed to add comment.');
    } finally {
      setSubmittingComment(false);
    }
  };

  const filteredTasks = useMemo(() => {
    return tasks.filter((t) => {
      if (statusFilter !== 'ALL' && t.status !== statusFilter) return false;
      if (!searchQuery.trim()) return true;
      const q = searchQuery.toLowerCase();
      return (
        t.taskNumber.toLowerCase().includes(q) ||
        t.title.toLowerCase().includes(q) ||
        (t.requestNumber && t.requestNumber.toLowerCase().includes(q)) ||
        (t.requestTitle && t.requestTitle.toLowerCase().includes(q)) ||
        t.assigneeName.toLowerCase().includes(q)
      );
    });
  }, [tasks, statusFilter, searchQuery]);

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Fulfillment Tasks &amp; Queues</h1>
          <p className="text-sm text-slate-500 mt-1">
            Operational queues for IT, Procurement, Hardware, and Facilities fulfillments.
          </p>
        </div>

        <button
          onClick={fetchTasks}
          disabled={loading}
          className="flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-slate-700 bg-white hover:bg-slate-50 border border-slate-200 rounded-lg shadow-sm transition-colors self-start sm:self-auto"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      {/* Tabs and Filter Bar */}
      <div className="bg-white rounded-xl border border-slate-200 p-4 shadow-sm flex flex-col md:flex-row gap-4 justify-between items-center">
        <div className="flex items-center gap-2 w-full md:w-auto">
          <button
            onClick={() => setActiveTab('QUEUE')}
            className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors flex items-center gap-2 ${
              activeTab === 'QUEUE'
                ? 'bg-indigo-600 text-white'
                : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
            }`}
          >
            Unassigned Queue
            <span
              className={`px-1.5 py-0.2 rounded-full text-[10px] font-bold ${
                activeTab === 'QUEUE'
                  ? 'bg-indigo-800 text-white'
                  : 'bg-indigo-100 text-indigo-800'
              }`}
            >
              {tasks.filter((t) => t.status === 'OPEN').length}
            </span>
          </button>

          <button
            onClick={() => setActiveTab('MY')}
            className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors flex items-center gap-2 ${
              activeTab === 'MY'
                ? 'bg-indigo-600 text-white'
                : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
            }`}
          >
            My In-Progress Tasks
          </button>
        </div>

        <div className="flex items-center gap-3 w-full md:w-auto">
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="px-3 py-1.5 text-xs border border-slate-200 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
          >
            <option value="ALL">All Statuses</option>
            <option value="OPEN">Open</option>
            <option value="IN_PROGRESS">In Progress</option>
            <option value="BLOCKED">Blocked</option>
            <option value="COMPLETED">Completed</option>
          </select>

          <div className="relative w-full md:w-64">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search tasks, serials, req..."
              className="w-full pl-9 pr-3 py-1.5 text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
            />
          </div>
        </div>
      </div>

      {error && (
        <div className="p-4 bg-rose-50 border border-rose-200 rounded-xl text-rose-800 text-xs flex items-center gap-3">
          <AlertCircle className="h-4 w-4 text-rose-600 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Task List */}
      {loading ? (
        <div className="p-12 text-center bg-white rounded-xl border border-slate-200 shadow-sm space-y-3">
          <RefreshCw className="h-6 w-6 text-indigo-600 animate-spin mx-auto" />
          <p className="text-xs font-medium text-slate-600">Loading fulfillment queue...</p>
        </div>
      ) : filteredTasks.length === 0 ? (
        <div className="p-12 text-center bg-white rounded-xl border border-slate-200 shadow-sm space-y-3">
          <div className="h-12 w-12 rounded-full bg-slate-50 text-slate-400 flex items-center justify-center mx-auto border border-slate-200">
            <PackageCheck className="h-6 w-6" />
          </div>
          <h3 className="text-sm font-bold text-slate-900">Queue is Clear</h3>
          <p className="text-xs text-slate-500 max-w-sm mx-auto">
            {activeTab === 'QUEUE'
              ? 'There are no pending fulfillment tasks waiting to be claimed.'
              : 'You have no tasks currently assigned to you.'}
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {filteredTasks.map((task) => (
            <div
              key={task.id}
              className="bg-white rounded-xl border border-slate-200 shadow-sm hover:border-slate-300 transition-all p-5 flex flex-col lg:flex-row items-start lg:items-center justify-between gap-4"
            >
              <div className="space-y-2 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-xs font-bold text-emerald-700 bg-emerald-50 border border-emerald-100 px-2 py-0.5 rounded">
                    {task.taskNumber}
                  </span>
                  {task.requestNumber && task.requestNumber !== 'N/A' && (
                    <span className="text-xs font-medium text-indigo-600 bg-indigo-50 border border-indigo-100 px-2 py-0.5 rounded flex items-center gap-1">
                      <Tag className="h-3 w-3" />
                      Linked to {task.requestNumber}
                    </span>
                  )}
                  <span
                    className={`text-[11px] font-bold px-2 py-0.5 rounded uppercase ${
                      task.status === 'OPEN'
                        ? 'bg-blue-50 text-blue-700 border border-blue-100'
                        : task.status === 'IN_PROGRESS'
                        ? 'bg-amber-50 text-amber-700 border border-amber-100'
                        : task.status === 'BLOCKED'
                        ? 'bg-rose-50 text-rose-700 border border-rose-100'
                        : 'bg-emerald-50 text-emerald-700 border border-emerald-100'
                    }`}
                  >
                    {task.status.replace('_', ' ')}
                  </span>
                  <span
                    className={`text-[11px] font-semibold px-2 py-0.5 rounded ${
                      task.priority === 'URGENT'
                        ? 'bg-rose-100 text-rose-800'
                        : task.priority === 'HIGH'
                        ? 'bg-amber-100 text-amber-800'
                        : 'bg-slate-100 text-slate-700'
                    }`}
                  >
                    {task.priority}
                  </span>
                </div>

                <h3 className="text-base font-semibold text-slate-900 leading-snug">
                  {task.title}
                </h3>
                {task.description && (
                  <p className="text-xs text-slate-500">{task.description}</p>
                )}

                <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500">
                  <span className="flex items-center gap-1">
                    <User className="h-3.5 w-3.5 text-slate-400" />
                    Assignee: <strong>{task.assigneeName}</strong>
                  </span>
                  <span className="flex items-center gap-1">
                    <Calendar className="h-3.5 w-3.5 text-slate-400" />
                    Created: {new Date(task.createdAt).toLocaleDateString()}
                  </span>
                  {task.dueAt && (
                    <span className="flex items-center gap-1 text-amber-700 font-medium">
                      <Clock className="h-3.5 w-3.5 text-amber-600" />
                      SLA Target: {new Date(task.dueAt).toLocaleDateString()}
                    </span>
                  )}
                </div>
              </div>

              <div className="flex items-center gap-2 w-full lg:w-auto justify-end border-t lg:border-t-0 pt-3 lg:pt-0">
                {task.status === 'OPEN' && (
                  <button
                    onClick={() => handleClaimTask(task.id)}
                    disabled={actionLoading}
                    className="px-3.5 py-1.5 text-xs font-semibold text-indigo-700 bg-indigo-50 hover:bg-indigo-100 border border-indigo-200 rounded-lg transition-colors flex items-center gap-1.5"
                  >
                    <UserCheck className="h-3.5 w-3.5" />
                    Claim Task
                  </button>
                )}

                <button
                  onClick={() => handleOpenTask(task)}
                  className="px-4 py-1.5 text-xs font-semibold text-white bg-slate-900 hover:bg-slate-800 rounded-lg shadow-sm transition-colors flex items-center gap-1.5"
                >
                  <FileCheck2 className="h-3.5 w-3.5" />
                  Fulfill &amp; Manage
                  <ChevronRight className="h-3.5 w-3.5 text-slate-400" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Task Fulfillment & Details Drawer / Modal */}
      {selectedTask && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm animate-in fade-in duration-150">
          <div className="bg-white rounded-2xl shadow-xl border border-slate-200 w-full max-w-3xl overflow-hidden flex flex-col max-h-[92vh]">
            {/* Modal Header */}
            <div className="p-6 border-b border-slate-100 flex items-start justify-between bg-slate-50/50">
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-bold text-emerald-700 bg-emerald-50 border border-emerald-100 px-2 py-0.5 rounded">
                    {selectedTask.taskNumber}
                  </span>
                  {selectedTask.requestNumber && selectedTask.requestNumber !== 'N/A' && (
                    <span className="text-xs font-medium text-indigo-600 bg-indigo-50 border border-indigo-100 px-2 py-0.5 rounded">
                      Linked: {selectedTask.requestNumber}
                    </span>
                  )}
                  <span className="text-[11px] font-bold uppercase px-2 py-0.5 bg-slate-100 text-slate-700 rounded">
                    {selectedTask.status.replace('_', ' ')}
                  </span>
                </div>
                <h2 className="text-lg font-bold text-slate-900">{selectedTask.title}</h2>
                <p className="text-xs text-slate-500">
                  Assigned operator: <strong>{selectedTask.assigneeName}</strong>
                </p>
              </div>

              <button
                onClick={() => setSelectedTask(null)}
                className="text-slate-400 hover:text-slate-600 p-1.5 rounded-lg hover:bg-slate-100 transition-colors"
              >
                <XCircle className="h-5 w-5" />
              </button>
            </div>

            {/* Modal Content */}
            <div className="p-6 overflow-y-auto space-y-6">
              {actionError && (
                <div className="p-3.5 bg-rose-50 border border-rose-200 rounded-xl text-rose-800 text-xs flex items-center gap-2.5">
                  <AlertCircle className="h-4 w-4 text-rose-600 shrink-0" />
                  <span>{actionError}</span>
                </div>
              )}

              {/* Input Specifications (from Request) */}
              <div className="space-y-3">
                <h4 className="text-xs font-bold text-slate-700 uppercase tracking-wider">
                  Request Specifications &amp; Parameters
                </h4>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 bg-slate-50 border border-slate-200 rounded-xl p-4 text-xs">
                  {Object.entries(selectedTask.payload || {}).map(([k, v]) => (
                    <div key={k} className="space-y-0.5">
                      <span className="font-medium text-slate-500 capitalize">
                        {k.replace(/([A-Z])/g, ' $1')}
                      </span>
                      <p className="font-semibold text-slate-800 break-words">
                        {typeof v === 'object' ? JSON.stringify(v) : String(v)}
                      </p>
                    </div>
                  ))}
                  {Object.keys(selectedTask.payload || {}).length === 0 && (
                    <p className="text-slate-400 italic col-span-2">No payload parameters provided.</p>
                  )}
                </div>
              </div>

              {/* Operational Fulfillment Form */}
              {selectedTask.status !== 'COMPLETED' && (
                <div className="space-y-4 border-t border-slate-100 pt-5">
                  <h4 className="text-xs font-bold text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
                    <PackageCheck className="h-4 w-4 text-indigo-600" />
                    Fulfillment Output Data
                  </h4>

                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                    <div className="space-y-1">
                      <label className="text-xs font-semibold text-slate-700">Serial Number / Asset Tag</label>
                      <input
                        type="text"
                        value={serialNumber}
                        onChange={(e) => setSerialNumber(e.target.value)}
                        placeholder="e.g. SN-998822"
                        className="w-full p-2 text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                      />
                    </div>

                    <div className="space-y-1">
                      <label className="text-xs font-semibold text-slate-700">Delivery Carrier</label>
                      <input
                        type="text"
                        value={carrier}
                        onChange={(e) => setCarrier(e.target.value)}
                        placeholder="e.g. FedEx / Internal Desk"
                        className="w-full p-2 text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                      />
                    </div>

                    <div className="space-y-1">
                      <label className="text-xs font-semibold text-slate-700">Tracking Number / Ticket</label>
                      <input
                        type="text"
                        value={trackingNumber}
                        onChange={(e) => setTrackingNumber(e.target.value)}
                        placeholder="e.g. 789123456789"
                        className="w-full p-2 text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                      />
                    </div>
                  </div>

                  <div className="space-y-1">
                    <label className="text-xs font-semibold text-slate-700">Operational Fulfillment Notes</label>
                    <textarea
                      rows={2}
                      value={fulfillmentNotes}
                      onChange={(e) => setFulfillmentNotes(e.target.value)}
                      placeholder="Add configuration details, image versions, or delivery confirmation..."
                      className="w-full p-2.5 text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                    />
                  </div>

                  {/* Status Action Buttons */}
                  <div className="flex flex-wrap items-center gap-2 pt-2">
                    {selectedTask.status === 'OPEN' && (
                      <button
                        type="button"
                        onClick={() => handleClaimTask(selectedTask.id)}
                        disabled={actionLoading}
                        className="px-3.5 py-1.5 text-xs font-semibold text-indigo-700 bg-indigo-50 hover:bg-indigo-100 border border-indigo-200 rounded-lg transition-colors flex items-center gap-1.5"
                      >
                        <UserCheck className="h-3.5 w-3.5" />
                        Claim Task
                      </button>
                    )}

                    {selectedTask.status === 'IN_PROGRESS' && (
                      <button
                        type="button"
                        onClick={() => handleUpdateStatus('BLOCKED')}
                        disabled={actionLoading}
                        className="px-3.5 py-1.5 text-xs font-semibold text-amber-800 bg-amber-50 hover:bg-amber-100 border border-amber-200 rounded-lg transition-colors flex items-center gap-1.5"
                      >
                        <AlertTriangle className="h-3.5 w-3.5 text-amber-600" />
                        Mark as Blocked
                      </button>
                    )}

                    {selectedTask.status === 'BLOCKED' && (
                      <button
                        type="button"
                        onClick={() => handleUpdateStatus('IN_PROGRESS')}
                        disabled={actionLoading}
                        className="px-3.5 py-1.5 text-xs font-semibold text-indigo-700 bg-indigo-50 hover:bg-indigo-100 border border-indigo-200 rounded-lg transition-colors flex items-center gap-1.5"
                      >
                        <RefreshCw className="h-3.5 w-3.5 text-indigo-600" />
                        Resume In-Progress
                      </button>
                    )}

                    <button
                      type="button"
                      onClick={handleCompleteTask}
                      disabled={actionLoading}
                      className="px-4 py-1.5 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-700 rounded-lg shadow-sm transition-colors flex items-center gap-1.5 ml-auto"
                    >
                      <CheckCircle2 className="h-3.5 w-3.5" />
                      Complete Task &amp; Resume Workflow
                    </button>
                  </div>
                </div>
              )}

              {/* Already Completed Output Data Display */}
              {selectedTask.status === 'COMPLETED' && selectedTask.outputData && (
                <div className="space-y-3 border-t border-slate-100 pt-5">
                  <h4 className="text-xs font-bold text-emerald-700 uppercase tracking-wider flex items-center gap-1.5">
                    <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                    Completed Fulfillment Output
                  </h4>
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 bg-emerald-50/50 border border-emerald-200 rounded-xl p-4 text-xs">
                    {Object.entries(selectedTask.outputData).map(([k, v]) => (
                      <div key={k} className="space-y-0.5">
                        <span className="font-medium text-emerald-800 capitalize">
                          {k.replace(/([A-Z])/g, ' $1')}
                        </span>
                        <p className="font-semibold text-slate-900">{String(v)}</p>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Comments & Activity Timeline */}
              <div className="space-y-4 border-t border-slate-100 pt-5">
                <h4 className="text-xs font-bold text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
                  <MessageSquare className="h-4 w-4 text-slate-500" />
                  Task Comments &amp; Notes
                </h4>

                <div className="flex gap-2">
                  <input
                    type="text"
                    value={newComment}
                    onChange={(e) => setNewComment(e.target.value)}
                    placeholder="Add an operational comment or update..."
                    className="flex-1 p-2 text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500/20"
                  />
                  <button
                    type="button"
                    onClick={handleAddComment}
                    disabled={!newComment.trim() || submittingComment}
                    className="px-3 py-2 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg shadow-sm transition-colors disabled:opacity-50 flex items-center gap-1"
                  >
                    <Send className="h-3 w-3" />
                    Post
                  </button>
                </div>

                <div className="space-y-2">
                  {selectedTask.comments?.map((c) => (
                    <div key={c.id} className="p-3 bg-slate-50 rounded-xl border border-slate-200 text-xs space-y-1">
                      <div className="flex items-center justify-between">
                        <span className="font-semibold text-slate-900">{c.authorName}</span>
                        <span className="text-[10px] text-slate-400">
                          {new Date(c.createdAt).toLocaleString()}
                        </span>
                      </div>
                      <p className="text-slate-700">{c.content}</p>
                    </div>
                  ))}
                  {(!selectedTask.comments || selectedTask.comments.length === 0) && (
                    <p className="text-xs text-slate-400 italic">No comments yet on this task.</p>
                  )}
                </div>
              </div>

              {/* Audit History */}
              {selectedTask.history && selectedTask.history.length > 0 && (
                <div className="space-y-2 border-t border-slate-100 pt-5">
                  <h4 className="text-xs font-bold text-slate-700 uppercase tracking-wider flex items-center gap-1.5">
                    <History className="h-4 w-4 text-slate-500" />
                    Audit Log
                  </h4>
                  <div className="space-y-1.5">
                    {selectedTask.history.map((h) => (
                      <div key={h.id} className="text-xs text-slate-500 flex items-center justify-between py-1 border-b border-slate-100 last:border-0">
                        <span className="flex items-center gap-2">
                          <span className="font-bold text-[10px] bg-slate-100 px-1.5 py-0.5 rounded text-slate-700 uppercase">
                            {h.action}
                          </span>
                          <span>by <strong>{h.actorName}</strong></span>
                        </span>
                        <span className="text-[10px] text-slate-400">
                          {new Date(h.createdAt).toLocaleTimeString()}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Modal Footer */}
            <div className="p-4 border-t border-slate-100 bg-slate-50 flex justify-end">
              <button
                type="button"
                onClick={() => setSelectedTask(null)}
                className="px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-200 rounded-xl transition-colors"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
