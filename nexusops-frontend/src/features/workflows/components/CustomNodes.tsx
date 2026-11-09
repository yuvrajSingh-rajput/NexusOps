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
  UserCheck
} from 'lucide-react';
import { WorkflowNodeData } from '../types/workflowCanvas';

export const StartNode: React.FC<NodeProps<Node<WorkflowNodeData>>> = ({ data, selected }) => {
  return (
    <div
      className={`relative w-64 bg-white rounded-xl border-2 transition-all p-3.5 shadow-sm ${
        selected ? 'border-emerald-500 ring-2 ring-emerald-200 shadow-md' : 'border-emerald-200 hover:border-emerald-300'
      }`}
    >
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <div className="p-1.5 bg-emerald-100 text-emerald-700 rounded-lg">
            <Play className="h-4 w-4 fill-emerald-700" />
          </div>
          <span className="text-[11px] font-bold uppercase tracking-wider text-emerald-800 bg-emerald-50 px-2 py-0.5 rounded-full border border-emerald-200">
            Start Node
          </span>
        </div>
        <span className="text-[10px] font-mono text-slate-400 bg-slate-50 px-1.5 py-0.5 rounded border border-slate-200">
          {data.nodeKey}
        </span>
      </div>

      <div className="font-semibold text-sm text-slate-800 mb-1 truncate">{data.name || 'Start'}</div>
      <div className="text-xs text-slate-500">Request submission triggers workflow execution</div>

      <Handle
        type="source"
        position={Position.Right}
        id="out"
        className="!w-3 !h-3 !bg-emerald-500 !border-2 !border-white !rounded-full !-right-1.5"
      />
    </div>
  );
};

export const ApprovalNode: React.FC<NodeProps<Node<WorkflowNodeData>>> = ({ data, selected }) => {
  const config = data.configuration || {};
  const approverLabel = config.approverType
    ? `${config.approverType === 'MANAGER' ? 'Direct Manager' : config.approverType}`
    : config.approverRole
    ? `Role: ${config.approverRole}`
    : 'Config needed';

  return (
    <div
      className={`relative w-64 bg-white rounded-xl border-2 transition-all p-3.5 shadow-sm ${
        selected ? 'border-amber-500 ring-2 ring-amber-200 shadow-md' : 'border-amber-200 hover:border-amber-300'
      } ${data.hasErrors ? 'border-red-400 ring-1 ring-red-200' : ''}`}
    >
      <Handle
        type="target"
        position={Position.Left}
        id="in"
        className="!w-3 !h-3 !bg-slate-400 !border-2 !border-white !rounded-full !-left-1.5"
      />

      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <div className="p-1.5 bg-amber-100 text-amber-700 rounded-lg">
            <UserCheck className="h-4 w-4" />
          </div>
          <span className="text-[11px] font-bold uppercase tracking-wider text-amber-800 bg-amber-50 px-2 py-0.5 rounded-full border border-amber-200">
            Approval
          </span>
        </div>
        <span className="text-[10px] font-mono text-slate-400 bg-slate-50 px-1.5 py-0.5 rounded border border-slate-200">
          {data.nodeKey}
        </span>
      </div>

      <div className="font-semibold text-sm text-slate-800 mb-1.5 truncate">{data.name || 'Approval'}</div>

      <div className="flex flex-wrap gap-1 text-[11px]">
        <span className="inline-flex items-center gap-1 bg-amber-50 text-amber-800 px-2 py-0.5 rounded-md border border-amber-200 font-medium">
          <CheckCircle2 className="h-3 w-3" />
          {approverLabel}
        </span>
        {config.timeoutHours && (
          <span className="inline-flex items-center gap-1 bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded border border-slate-200 text-[10px]">
            <Clock className="h-3 w-3" />
            {config.timeoutHours}h SLA
          </span>
        )}
      </div>

      {data.hasErrors && (
        <div className="mt-2 text-[10px] text-red-600 flex items-center gap-1">
          <AlertTriangle className="h-3 w-3" />
          <span>{data.errorMessage || 'Configuration error'}</span>
        </div>
      )}

      <Handle
        type="source"
        position={Position.Right}
        id="out"
        className="!w-3 !h-3 !bg-amber-500 !border-2 !border-white !rounded-full !-right-1.5"
      />
    </div>
  );
};

export const ConditionNode: React.FC<NodeProps<Node<WorkflowNodeData>>> = ({ data, selected }) => {
  const config = data.configuration || {};
  const field = config.field || 'payload.estimatedAmount';

  return (
    <div
      className={`relative w-64 bg-white rounded-xl border-2 transition-all p-3.5 shadow-sm ${
        selected ? 'border-sky-500 ring-2 ring-sky-200 shadow-md' : 'border-sky-200 hover:border-sky-300'
      } ${data.hasErrors ? 'border-red-400 ring-1 ring-red-200' : ''}`}
    >
      <Handle
        type="target"
        position={Position.Left}
        id="in"
        className="!w-3 !h-3 !bg-slate-400 !border-2 !border-white !rounded-full !-left-1.5"
      />

      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <div className="p-1.5 bg-sky-100 text-sky-700 rounded-lg">
            <GitBranch className="h-4 w-4" />
          </div>
          <span className="text-[11px] font-bold uppercase tracking-wider text-sky-800 bg-sky-50 px-2 py-0.5 rounded-full border border-sky-200">
            Condition
          </span>
        </div>
        <span className="text-[10px] font-mono text-slate-400 bg-slate-50 px-1.5 py-0.5 rounded border border-slate-200">
          {data.nodeKey}
        </span>
      </div>

      <div className="font-semibold text-sm text-slate-800 mb-1.5 truncate">{data.name || 'Evaluate Condition'}</div>

      <div className="text-[11px] font-mono bg-sky-50 text-sky-900 px-2 py-1 rounded border border-sky-200 truncate">
        eval({field})
      </div>

      <Handle
        type="source"
        position={Position.Right}
        id="out"
        className="!w-3 !h-3 !bg-sky-500 !border-2 !border-white !rounded-full !-right-1.5"
      />
    </div>
  );
};

export const TaskNode: React.FC<NodeProps<Node<WorkflowNodeData>>> = ({ data, selected }) => {
  const config = data.configuration || {};
  const queue = config.taskQueue || config.assignedQueue || 'IT_OPERATIONS';

  return (
    <div
      className={`relative w-64 bg-white rounded-xl border-2 transition-all p-3.5 shadow-sm ${
        selected ? 'border-purple-500 ring-2 ring-purple-200 shadow-md' : 'border-purple-200 hover:border-purple-300'
      } ${data.hasErrors ? 'border-red-400 ring-1 ring-red-200' : ''}`}
    >
      <Handle
        type="target"
        position={Position.Left}
        id="in"
        className="!w-3 !h-3 !bg-slate-400 !border-2 !border-white !rounded-full !-left-1.5"
      />

      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <div className="p-1.5 bg-purple-100 text-purple-700 rounded-lg">
            <ClipboardList className="h-4 w-4" />
          </div>
          <span className="text-[11px] font-bold uppercase tracking-wider text-purple-800 bg-purple-50 px-2 py-0.5 rounded-full border border-purple-200">
            Task
          </span>
        </div>
        <span className="text-[10px] font-mono text-slate-400 bg-slate-50 px-1.5 py-0.5 rounded border border-slate-200">
          {data.nodeKey}
        </span>
      </div>

      <div className="font-semibold text-sm text-slate-800 mb-1.5 truncate">{data.name || 'Fulfillment Task'}</div>

      <div className="flex flex-wrap gap-1 text-[11px]">
        <span className="inline-flex items-center gap-1 bg-purple-50 text-purple-800 px-2 py-0.5 rounded-md border border-purple-200 font-medium">
          Queue: {queue}
        </span>
        {config.slaHours && (
          <span className="inline-flex items-center gap-1 bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded border border-slate-200 text-[10px]">
            <Clock className="h-3 w-3" />
            {config.slaHours}h SLA
          </span>
        )}
      </div>

      <Handle
        type="source"
        position={Position.Right}
        id="out"
        className="!w-3 !h-3 !bg-purple-500 !border-2 !border-white !rounded-full !-right-1.5"
      />
    </div>
  );
};

export const EndNode: React.FC<NodeProps<Node<WorkflowNodeData>>> = ({ data, selected }) => {
  const config = data.configuration || {};
  const outcome = config.outcome || 'COMPLETED';
  const isRejected = outcome === 'REJECTED';

  return (
    <div
      className={`relative w-64 bg-white rounded-xl border-2 transition-all p-3.5 shadow-sm ${
        selected
          ? isRejected
            ? 'border-red-500 ring-2 ring-red-200 shadow-md'
            : 'border-slate-800 ring-2 ring-slate-200 shadow-md'
          : isRejected
          ? 'border-red-200 hover:border-red-300'
          : 'border-slate-300 hover:border-slate-400'
      }`}
    >
      <Handle
        type="target"
        position={Position.Left}
        id="in"
        className="!w-3 !h-3 !bg-slate-400 !border-2 !border-white !rounded-full !-left-1.5"
      />

      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <div className={`p-1.5 rounded-lg ${isRejected ? 'bg-red-100 text-red-700' : 'bg-slate-100 text-slate-800'}`}>
            <Flag className="h-4 w-4" />
          </div>
          <span
            className={`text-[11px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full border ${
              isRejected ? 'bg-red-50 text-red-800 border-red-200' : 'bg-slate-100 text-slate-800 border-slate-200'
            }`}
          >
            End Node
          </span>
        </div>
        <span className="text-[10px] font-mono text-slate-400 bg-slate-50 px-1.5 py-0.5 rounded border border-slate-200">
          {data.nodeKey}
        </span>
      </div>

      <div className="font-semibold text-sm text-slate-800 mb-1 truncate">{data.name || 'End'}</div>

      <span
        className={`inline-block text-[11px] font-semibold px-2 py-0.5 rounded border ${
          isRejected ? 'bg-red-50 text-red-700 border-red-200' : 'bg-emerald-50 text-emerald-700 border-emerald-200'
        }`}
      >
        Outcome: {outcome}
      </span>
    </div>
  );
};
