import React from 'react';
import { Handle, Position, NodeProps, Node } from '@xyflow/react';
import {
  Play,
  CheckCircle2,
  GitBranch,
  ClipboardList,
  Flag,
  AlertTriangle,
  Clock,
  UserCheck,
  Loader2,
  ShieldAlert
} from 'lucide-react';
import { WorkflowNodeData, NodeExecutionStatus } from '../types/workflowCanvas';

function getExecutionBadge(status?: NodeExecutionStatus, isCurrent?: boolean) {
  if (isCurrent || status === 'RUNNING') {
    return (
      <div className="flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[11px] font-bold bg-blue-100 text-blue-800 border border-blue-300 animate-pulse">
        <Loader2 className="h-3 w-3 animate-spin text-blue-600" />
        <span>CURRENT STEP</span>
      </div>
    );
  }
  if (status === 'COMPLETED') {
    return (
      <div className="flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-bold bg-emerald-100 text-emerald-800 border border-emerald-300">
        <CheckCircle2 className="h-3 w-3 text-emerald-600" />
        <span>COMPLETED</span>
      </div>
    );
  }
  if (status === 'WAITING') {
    return (
      <div className="flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-bold bg-amber-100 text-amber-800 border border-amber-300">
        <Clock className="h-3 w-3 text-amber-600" />
        <span>WAITING ACTION</span>
      </div>
    );
  }
  if (status === 'FAILED') {
    return (
      <div className="flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-bold bg-rose-100 text-rose-800 border border-rose-300">
        <ShieldAlert className="h-3 w-3 text-rose-600" />
        <span>FAILED</span>
      </div>
    );
  }
  if (status === 'PENDING') {
    return (
      <div className="flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-medium bg-slate-100 text-slate-500 border border-slate-200">
        <span>PENDING</span>
      </div>
    );
  }
  return null;
}

export const StartNode: React.FC<NodeProps<Node<WorkflowNodeData>>> = ({ data, selected }) => {
  const isExecuting = !!data.executionStatus;
  const isCurrent = data.isCurrentExecutionNode || data.executionStatus === 'RUNNING';
  const isCompleted = data.executionStatus === 'COMPLETED';

  return (
    <div
      className={`relative w-[270px] min-h-[110px] bg-white rounded-2xl border-2 transition-all p-4 shadow-sm select-none ${
        selected
          ? 'border-emerald-600 ring-4 ring-emerald-500/20 shadow-lg scale-[1.02]'
          : isCurrent
          ? 'border-blue-600 ring-4 ring-blue-400/30 shadow-lg bg-blue-50/20'
          : isCompleted
          ? 'border-emerald-500 bg-emerald-50/15'
          : 'border-emerald-200 hover:border-emerald-400 hover:shadow-md'
      } ${data.hasErrors ? 'border-rose-400 ring-2 ring-rose-300/30' : ''}`}
    >
      <div className="flex items-center justify-between mb-2.5">
        <div className="flex items-center gap-2">
          <div className="p-1.5 bg-emerald-100 text-emerald-700 rounded-lg shadow-2xs">
            <Play className="h-4 w-4 fill-emerald-700" />
          </div>
          <span className="text-[11px] font-bold uppercase tracking-wider text-emerald-800 bg-emerald-50 px-2 py-0.5 rounded-md border border-emerald-200">
            Start
          </span>
        </div>
        {isExecuting ? (
          getExecutionBadge(data.executionStatus, isCurrent)
        ) : (
          <span className="text-[10px] font-mono text-slate-400 bg-slate-50 px-1.5 py-0.5 rounded border border-slate-200">
            {data.nodeKey}
          </span>
        )}
      </div>

      <div className="font-semibold text-sm text-slate-900 mb-1 leading-snug line-clamp-1" title={data.name}>
        {data.name || 'Start Workflow'}
      </div>
      <div className="text-xs text-slate-500 leading-normal">
        Request submission triggers automated orchestration
      </div>

      <Handle
        type="source"
        position={Position.Right}
        id="out"
        className="!w-3.5 !h-3.5 !bg-emerald-500 !border-2 !border-white !rounded-full !-right-2 shadow-sm"
      />
    </div>
  );
};

export const ApprovalNode: React.FC<NodeProps<Node<WorkflowNodeData>>> = ({ data, selected }) => {
  const config = data.configuration || {};
  const approverLabel = config.approverType
    ? config.approverType === 'MANAGER'
      ? 'Direct Manager'
      : config.approverType === 'DEPT_HEAD'
      ? 'Department Head'
      : config.approverType === 'ROLE'
      ? `Role: ${config.approverRole || 'Approver'}`
      : config.approverType
    : config.approverRole
    ? `Role: ${config.approverRole}`
    : 'Manager Approval';

  const isExecuting = !!data.executionStatus;
  const isCurrent = data.isCurrentExecutionNode || data.executionStatus === 'RUNNING' || data.executionStatus === 'WAITING';
  const isCompleted = data.executionStatus === 'COMPLETED';

  return (
    <div
      className={`relative w-[270px] min-h-[110px] bg-white rounded-2xl border-2 transition-all p-4 shadow-sm select-none ${
        selected
          ? 'border-amber-600 ring-4 ring-amber-500/20 shadow-lg scale-[1.02]'
          : isCurrent
          ? 'border-blue-600 ring-4 ring-blue-400/30 shadow-lg bg-blue-50/20'
          : isCompleted
          ? 'border-emerald-500 bg-emerald-50/15'
          : 'border-amber-200 hover:border-amber-400 hover:shadow-md'
      } ${data.hasErrors ? 'border-rose-400 ring-2 ring-rose-300/30' : ''}`}
    >
      <Handle
        type="target"
        position={Position.Left}
        id="in"
        className="!w-3.5 !h-3.5 !bg-slate-500 !border-2 !border-white !rounded-full !-left-2 shadow-sm"
      />

      <div className="flex items-center justify-between mb-2.5">
        <div className="flex items-center gap-2">
          <div className="p-1.5 bg-amber-100 text-amber-700 rounded-lg shadow-2xs">
            <UserCheck className="h-4 w-4" />
          </div>
          <span className="text-[11px] font-bold uppercase tracking-wider text-amber-800 bg-amber-50 px-2 py-0.5 rounded-md border border-amber-200">
            Approval
          </span>
        </div>
        {isExecuting ? (
          getExecutionBadge(data.executionStatus, isCurrent)
        ) : (
          <span className="text-[10px] font-mono text-slate-400 bg-slate-50 px-1.5 py-0.5 rounded border border-slate-200">
            {data.nodeKey}
          </span>
        )}
      </div>

      <div className="font-semibold text-sm text-slate-900 mb-1.5 leading-snug line-clamp-1" title={data.name}>
        {data.name || 'Approval'}
      </div>

      <div className="flex flex-wrap items-center gap-1.5 text-xs">
        <span className="inline-flex items-center gap-1 bg-amber-50 text-amber-900 px-2.5 py-0.5 rounded-md border border-amber-200 font-medium">
          <CheckCircle2 className="h-3 w-3 text-amber-600" />
          {approverLabel}
        </span>
        {config.timeoutHours ? (
          <span className="inline-flex items-center gap-1 bg-slate-100 text-slate-600 px-2 py-0.5 rounded-md border border-slate-200 text-[11px]">
            <Clock className="h-3 w-3 text-slate-400" />
            {config.timeoutHours}h SLA
          </span>
        ) : null}
      </div>

      {data.hasErrors && (
        <div className="mt-2 text-xs text-rose-600 flex items-center gap-1 font-medium">
          <AlertTriangle className="h-3.5 w-3.5 shrink-0" />
          <span className="truncate">{data.errorMessage || 'Configuration error'}</span>
        </div>
      )}

      <Handle
        type="source"
        position={Position.Right}
        id="out"
        className="!w-3.5 !h-3.5 !bg-amber-500 !border-2 !border-white !rounded-full !-right-2 shadow-sm"
      />
    </div>
  );
};

export const ConditionNode: React.FC<NodeProps<Node<WorkflowNodeData>>> = ({ data, selected }) => {
  const config = data.configuration || {};
  const field = config.field || 'payload.estimatedAmount';
  const cleanField = field.replace('payload.', '').replace('estimatedAmount', 'Amount');

  const isExecuting = !!data.executionStatus;
  const isCurrent = data.isCurrentExecutionNode || data.executionStatus === 'RUNNING';
  const isCompleted = data.executionStatus === 'COMPLETED';

  return (
    <div
      className={`relative w-[270px] min-h-[110px] bg-white rounded-2xl border-2 transition-all p-4 shadow-sm select-none ${
        selected
          ? 'border-sky-600 ring-4 ring-sky-500/20 shadow-lg scale-[1.02]'
          : isCurrent
          ? 'border-blue-600 ring-4 ring-blue-400/30 shadow-lg bg-blue-50/20'
          : isCompleted
          ? 'border-emerald-500 bg-emerald-50/15'
          : 'border-sky-200 hover:border-sky-400 hover:shadow-md'
      } ${data.hasErrors ? 'border-rose-400 ring-2 ring-rose-300/30' : ''}`}
    >
      <Handle
        type="target"
        position={Position.Left}
        id="in"
        className="!w-3.5 !h-3.5 !bg-slate-500 !border-2 !border-white !rounded-full !-left-2 shadow-sm"
      />

      <div className="flex items-center justify-between mb-2.5">
        <div className="flex items-center gap-2">
          <div className="p-1.5 bg-sky-100 text-sky-700 rounded-lg shadow-2xs">
            <GitBranch className="h-4 w-4" />
          </div>
          <span className="text-[11px] font-bold uppercase tracking-wider text-sky-800 bg-sky-50 px-2 py-0.5 rounded-md border border-sky-200">
            Condition
          </span>
        </div>
        {isExecuting ? (
          getExecutionBadge(data.executionStatus, isCurrent)
        ) : (
          <span className="text-[10px] font-mono text-slate-400 bg-slate-50 px-1.5 py-0.5 rounded border border-slate-200">
            {data.nodeKey}
          </span>
        )}
      </div>

      <div className="font-semibold text-sm text-slate-900 mb-1.5 leading-snug line-clamp-1" title={data.name}>
        {data.name || 'Evaluate Condition'}
      </div>

      <div className="flex items-center gap-1.5 text-xs font-mono bg-sky-50 text-sky-900 px-2.5 py-1 rounded-md border border-sky-200 truncate">
        <span className="text-sky-500 font-sans font-bold">eval:</span>
        <span className="font-semibold">{cleanField}</span>
      </div>

      {data.hasErrors && (
        <div className="mt-2 text-xs text-rose-600 flex items-center gap-1 font-medium">
          <AlertTriangle className="h-3.5 w-3.5 shrink-0" />
          <span className="truncate">{data.errorMessage || 'Default route required'}</span>
        </div>
      )}

      <Handle
        type="source"
        position={Position.Right}
        id="out"
        className="!w-3.5 !h-3.5 !bg-sky-500 !border-2 !border-white !rounded-full !-right-2 shadow-sm"
      />
    </div>
  );
};

export const TaskNode: React.FC<NodeProps<Node<WorkflowNodeData>>> = ({ data, selected }) => {
  const config = data.configuration || {};
  const queue = config.taskQueue || config.assignedQueue || 'IT_OPERATIONS';
  const cleanQueue = queue.replace('_QUEUE', '').replace('_', ' ');

  const isExecuting = !!data.executionStatus;
  const isCurrent = data.isCurrentExecutionNode || data.executionStatus === 'RUNNING' || data.executionStatus === 'WAITING';
  const isCompleted = data.executionStatus === 'COMPLETED';

  return (
    <div
      className={`relative w-[270px] min-h-[110px] bg-white rounded-2xl border-2 transition-all p-4 shadow-sm select-none ${
        selected
          ? 'border-purple-600 ring-4 ring-purple-500/20 shadow-lg scale-[1.02]'
          : isCurrent
          ? 'border-blue-600 ring-4 ring-blue-400/30 shadow-lg bg-blue-50/20'
          : isCompleted
          ? 'border-emerald-500 bg-emerald-50/15'
          : 'border-purple-200 hover:border-purple-400 hover:shadow-md'
      } ${data.hasErrors ? 'border-rose-400 ring-2 ring-rose-300/30' : ''}`}
    >
      <Handle
        type="target"
        position={Position.Left}
        id="in"
        className="!w-3.5 !h-3.5 !bg-slate-500 !border-2 !border-white !rounded-full !-left-2 shadow-sm"
      />

      <div className="flex items-center justify-between mb-2.5">
        <div className="flex items-center gap-2">
          <div className="p-1.5 bg-purple-100 text-purple-700 rounded-lg shadow-2xs">
            <ClipboardList className="h-4 w-4" />
          </div>
          <span className="text-[11px] font-bold uppercase tracking-wider text-purple-800 bg-purple-50 px-2 py-0.5 rounded-md border border-purple-200">
            Task
          </span>
        </div>
        {isExecuting ? (
          getExecutionBadge(data.executionStatus, isCurrent)
        ) : (
          <span className="text-[10px] font-mono text-slate-400 bg-slate-50 px-1.5 py-0.5 rounded border border-slate-200">
            {data.nodeKey}
          </span>
        )}
      </div>

      <div className="font-semibold text-sm text-slate-900 mb-1.5 leading-snug line-clamp-1" title={data.name}>
        {data.name || 'Fulfillment Task'}
      </div>

      <div className="flex flex-wrap items-center gap-1.5 text-xs">
        <span className="inline-flex items-center gap-1 bg-purple-50 text-purple-900 px-2.5 py-0.5 rounded-md border border-purple-200 font-medium capitalize">
          Queue: {cleanQueue}
        </span>
        {config.slaHours ? (
          <span className="inline-flex items-center gap-1 bg-slate-100 text-slate-600 px-2 py-0.5 rounded-md border border-slate-200 text-[11px]">
            <Clock className="h-3 w-3 text-slate-400" />
            {config.slaHours}h SLA
          </span>
        ) : null}
      </div>

      {data.hasErrors && (
        <div className="mt-2 text-xs text-rose-600 flex items-center gap-1 font-medium">
          <AlertTriangle className="h-3.5 w-3.5 shrink-0" />
          <span className="truncate">{data.errorMessage || 'Task configuration error'}</span>
        </div>
      )}

      <Handle
        type="source"
        position={Position.Right}
        id="out"
        className="!w-3.5 !h-3.5 !bg-purple-500 !border-2 !border-white !rounded-full !-right-2 shadow-sm"
      />
    </div>
  );
};

export const EndNode: React.FC<NodeProps<Node<WorkflowNodeData>>> = ({ data, selected }) => {
  const config = data.configuration || {};
  const outcome = config.outcome || 'COMPLETED';
  const isRejected = outcome === 'REJECTED';

  const isExecuting = !!data.executionStatus;
  const isCurrent = data.isCurrentExecutionNode || data.executionStatus === 'RUNNING';
  const isCompleted = data.executionStatus === 'COMPLETED';

  return (
    <div
      className={`relative w-[270px] min-h-[110px] bg-white rounded-2xl border-2 transition-all p-4 shadow-sm select-none ${
        selected
          ? isRejected
            ? 'border-rose-600 ring-4 ring-rose-500/20 shadow-lg scale-[1.02]'
            : 'border-emerald-600 ring-4 ring-emerald-500/20 shadow-lg scale-[1.02]'
          : isCurrent
          ? 'border-blue-600 ring-4 ring-blue-400/30 shadow-lg bg-blue-50/20'
          : isCompleted
          ? isRejected
            ? 'border-rose-500 bg-rose-50/15'
            : 'border-emerald-500 bg-emerald-50/15'
          : isRejected
          ? 'border-rose-200 hover:border-rose-400 hover:shadow-md'
          : 'border-emerald-200 hover:border-emerald-400 hover:shadow-md'
      }`}
    >
      <Handle
        type="target"
        position={Position.Left}
        id="in"
        className="!w-3.5 !h-3.5 !bg-slate-500 !border-2 !border-white !rounded-full !-left-2 shadow-sm"
      />

      <div className="flex items-center justify-between mb-2.5">
        <div className="flex items-center gap-2">
          <div
            className={`p-1.5 rounded-lg shadow-2xs ${
              isRejected ? 'bg-rose-100 text-rose-700' : 'bg-emerald-100 text-emerald-700'
            }`}
          >
            <Flag className="h-4 w-4" />
          </div>
          <span
            className={`text-[11px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-md border ${
              isRejected
                ? 'bg-rose-50 text-rose-800 border-rose-200'
                : 'bg-emerald-50 text-emerald-800 border-emerald-200'
            }`}
          >
            End Node
          </span>
        </div>
        {isExecuting ? (
          getExecutionBadge(data.executionStatus, isCurrent)
        ) : (
          <span className="text-[10px] font-mono text-slate-400 bg-slate-50 px-1.5 py-0.5 rounded border border-slate-200">
            {data.nodeKey}
          </span>
        )}
      </div>

      <div className="font-semibold text-sm text-slate-900 mb-1.5 leading-snug line-clamp-1" title={data.name}>
        {data.name || (isRejected ? 'Request Rejected' : 'Process Completed')}
      </div>

      <span
        className={`inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-0.5 rounded-md border ${
          isRejected
            ? 'bg-rose-50 text-rose-700 border-rose-200'
            : 'bg-emerald-50 text-emerald-700 border-emerald-200'
        }`}
      >
        <span className={`w-1.5 h-1.5 rounded-full ${isRejected ? 'bg-rose-500' : 'bg-emerald-500'}`} />
        Outcome: {outcome}
      </span>
    </div>
  );
};
