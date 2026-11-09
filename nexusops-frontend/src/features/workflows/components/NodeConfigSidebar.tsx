import React from 'react';
import { Node, Edge } from '@xyflow/react';
import {
  X,
  Trash2,
  Lock,
  Layers,
  ShieldCheck,
  CheckCircle,
  GitBranch,
  Settings
} from 'lucide-react';
import { WorkflowNodeData, WorkflowEdgeData } from '../types/workflowCanvas';

interface NodeConfigSidebarProps {
  selectedNode: Node<WorkflowNodeData> | null;
  selectedEdge: Edge | null;
  onUpdateNode: (id: string, updates: Partial<WorkflowNodeData>) => void;
  onUpdateEdge: (id: string, updates: Partial<WorkflowEdgeData>) => void;
  onDeleteNode: (id: string) => void;
  onDeleteEdge: (id: string) => void;
  onClose: () => void;
  isReadOnly: boolean;
}

export const NodeConfigSidebar: React.FC<NodeConfigSidebarProps> = ({
  selectedNode,
  selectedEdge,
  onUpdateNode,
  onUpdateEdge,
  onDeleteNode,
  onDeleteEdge,
  onClose,
  isReadOnly
}) => {
  if (!selectedNode && !selectedEdge) return null;

  return (
    <aside className="w-88 bg-white border-l border-slate-200 h-full flex flex-col shadow-xl z-20 animate-slide-left">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-slate-100">
        <div className="flex items-center gap-2">
          <div className="p-1.5 bg-indigo-50 text-indigo-600 rounded-lg">
            <Settings className="h-4 w-4" />
          </div>
          <div>
            <h3 className="text-sm font-bold text-slate-900">
              {selectedNode ? 'Node Configuration' : 'Transition Routing'}
            </h3>
            <p className="text-[11px] text-slate-500">
              {selectedNode ? selectedNode.data.nodeType : 'Connection Rule'}
            </p>
          </div>
        </div>
        <button
          onClick={onClose}
          className="p-1 text-slate-400 hover:text-slate-600 rounded-lg hover:bg-slate-100"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      {/* Read-Only Notice */}
      {isReadOnly && (
        <div className="mx-4 mt-3 p-2.5 bg-amber-50 border border-amber-200 rounded-xl flex items-center gap-2 text-xs text-amber-800">
          <Lock className="h-4 w-4 text-amber-600 shrink-0" />
          <span>Published version is immutable. Create a new draft to edit.</span>
        </div>
      )}

      {/* Body Content */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {selectedNode && (
          <NodeConfigForm
            node={selectedNode}
            onUpdate={(updates) => onUpdateNode(selectedNode.id, updates)}
            isReadOnly={isReadOnly}
          />
        )}

        {selectedEdge && (
          <EdgeConfigForm
            edge={selectedEdge}
            onUpdate={(updates) => onUpdateEdge(selectedEdge.id, updates)}
            isReadOnly={isReadOnly}
          />
        )}
      </div>

      {/* Footer Delete Action */}
      {!isReadOnly && (
        <div className="p-4 border-t border-slate-100 bg-slate-50/50">
          {selectedNode && selectedNode.data.nodeType !== 'START' && (
            <button
              type="button"
              onClick={() => onDeleteNode(selectedNode.id)}
              className="w-full flex items-center justify-center gap-2 px-3 py-2 text-xs font-semibold text-red-600 hover:bg-red-50 border border-red-200 rounded-xl transition-colors"
            >
              <Trash2 className="h-3.5 w-3.5" />
              <span>Delete Node</span>
            </button>
          )}

          {selectedEdge && (
            <button
              type="button"
              onClick={() => onDeleteEdge(selectedEdge.id)}
              className="w-full flex items-center justify-center gap-2 px-3 py-2 text-xs font-semibold text-red-600 hover:bg-red-50 border border-red-200 rounded-xl transition-colors"
            >
              <Trash2 className="h-3.5 w-3.5" />
              <span>Delete Connection</span>
            </button>
          )}
        </div>
      )}
    </aside>
  );
};

/* -------------------------------------------------------------------------- */
/* Node Form Component                                                        */
/* -------------------------------------------------------------------------- */
interface NodeConfigFormProps {
  node: Node<WorkflowNodeData>;
  onUpdate: (updates: Partial<WorkflowNodeData>) => void;
  isReadOnly: boolean;
}

const NodeConfigForm: React.FC<NodeConfigFormProps> = ({ node, onUpdate, isReadOnly }) => {
  const data = node.data;
  const config = data.configuration || {};

  const handleConfigChange = (key: string, value: any) => {
    if (isReadOnly) return;
    onUpdate({
      configuration: {
        ...config,
        [key]: value
      }
    });
  };

  return (
    <div className="space-y-4 text-xs">
      {/* Node Key */}
      <div>
        <label className="block font-semibold text-slate-700 mb-1">Node Identifier Key</label>
        <input
          type="text"
          disabled={isReadOnly || data.nodeType === 'START'}
          value={data.nodeKey || ''}
          onChange={(e) => onUpdate({ nodeKey: e.target.value.toUpperCase().replace(/\s+/g, '_') })}
          className="w-full px-3 py-2 font-mono text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:bg-slate-50"
        />
        <p className="text-[10px] text-slate-400 mt-0.5">Unique programmatic key used for transitions and engine routing</p>
      </div>

      {/* Node Title */}
      <div>
        <label className="block font-semibold text-slate-700 mb-1">Display Title</label>
        <input
          type="text"
          disabled={isReadOnly}
          value={data.name || ''}
          onChange={(e) => onUpdate({ name: e.target.value })}
          className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:bg-slate-50"
        />
      </div>

      {/* APPROVAL CONFIGURATION */}
      {data.nodeType === 'APPROVAL' && (
        <div className="pt-3 border-t border-slate-100 space-y-3">
          <div className="font-semibold text-slate-800 flex items-center gap-1.5">
            <ShieldCheck className="h-3.5 w-3.5 text-amber-600" />
            <span>Approval Rules</span>
          </div>

          <div>
            <label className="block font-medium text-slate-600 mb-1">Approver Type</label>
            <select
              disabled={isReadOnly}
              value={config.approverType || (config.approverRole ? 'ROLE' : 'MANAGER')}
              onChange={(e) => {
                const val = e.target.value;
                if (val === 'ROLE') {
                  handleConfigChange('approverType', null);
                  handleConfigChange('approverRole', 'FINANCE');
                } else {
                  handleConfigChange('approverType', val);
                  handleConfigChange('approverRole', null);
                }
              }}
              className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white disabled:bg-slate-50"
            >
              <option value="MANAGER">Direct Manager (Organizational Hierarchy)</option>
              <option value="DEPT_HEAD">Department Head</option>
              <option value="ROLE">Role-Based Queue</option>
              <option value="SPECIFIC_USER">Specific Individual</option>
            </select>
          </div>

          {(!config.approverType || config.approverType === 'ROLE') && (
            <div>
              <label className="block font-medium text-slate-600 mb-1">Approver Role</label>
              <select
                disabled={isReadOnly}
                value={config.approverRole || 'FINANCE'}
                onChange={(e) => handleConfigChange('approverRole', e.target.value)}
                className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white disabled:bg-slate-50"
              >
                <option value="FINANCE">Finance Controller (FINANCE)</option>
                <option value="OPERATOR">IT Operator (OPERATOR)</option>
                <option value="ORG_ADMIN">Organization Administrator (ORG_ADMIN)</option>
                <option value="HR">People Operations (HR)</option>
              </select>
            </div>
          )}

          <div>
            <label className="block font-medium text-slate-600 mb-1">SLA Timeout (Hours)</label>
            <input
              type="number"
              min="1"
              max="720"
              disabled={isReadOnly}
              value={config.timeoutHours || 48}
              onChange={(e) => handleConfigChange('timeoutHours', parseInt(e.target.value) || 24)}
              className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:bg-slate-50"
            />
          </div>

          <div>
            <label className="block font-medium text-slate-600 mb-1">Approval Policy</label>
            <select
              disabled={isReadOnly}
              value={config.approvalPolicy || 'SINGLE_APPROVER'}
              onChange={(e) => handleConfigChange('approvalPolicy', e.target.value)}
              className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white disabled:bg-slate-50"
            >
              <option value="SINGLE_APPROVER">Single Approver Required</option>
              <option value="ALL_MUST_APPROVE">Unanimous (All Must Approve)</option>
              <option value="MAJORITY">Majority Consensus</option>
            </select>
          </div>
        </div>
      )}

      {/* CONDITION CONFIGURATION */}
      {data.nodeType === 'CONDITION' && (
        <div className="pt-3 border-t border-slate-100 space-y-3">
          <div className="font-semibold text-slate-800 flex items-center gap-1.5">
            <GitBranch className="h-3.5 w-3.5 text-sky-600" />
            <span>Condition Predicate</span>
          </div>

          <div>
            <label className="block font-medium text-slate-600 mb-1">Evaluated Field Path</label>
            <select
              disabled={isReadOnly}
              value={config.field || 'payload.estimatedAmount'}
              onChange={(e) => handleConfigChange('field', e.target.value)}
              className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white disabled:bg-slate-50"
            >
              <option value="payload.estimatedAmount">payload.estimatedAmount (Purchase Amount $)</option>
              <option value="payload.deviceModel">payload.deviceModel (Device Model String)</option>
              <option value="payload.ramGb">payload.ramGb (RAM Memory GB)</option>
              <option value="context.signal">context.signal (Upstream Approval Decision)</option>
            </select>
          </div>

          <p className="text-[11px] text-slate-500 bg-sky-50/60 p-2 rounded-lg border border-sky-100">
            Outbound edge conditions will be evaluated against this field in priority order.
          </p>
        </div>
      )}

      {/* TASK CONFIGURATION */}
      {data.nodeType === 'TASK' && (
        <div className="pt-3 border-t border-slate-100 space-y-3">
          <div className="font-semibold text-slate-800 flex items-center gap-1.5">
            <Layers className="h-3.5 w-3.5 text-purple-600" />
            <span>Fulfillment Task Configuration</span>
          </div>

          <div>
            <label className="block font-medium text-slate-600 mb-1">Target Queue</label>
            <select
              disabled={isReadOnly}
              value={config.taskQueue || config.assignedQueue || 'IT_OPERATIONS'}
              onChange={(e) => handleConfigChange('taskQueue', e.target.value)}
              className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white disabled:bg-slate-50"
            >
              <option value="IT_OPERATIONS">IT Procurement & Hardware Provisioning</option>
              <option value="FACILITIES">Facilities & Office Logistics</option>
              <option value="FINANCE_QUEUE">Finance & Reimbursements</option>
              <option value="HR_QUEUE">People Operations & Access</option>
            </select>
          </div>

          <div>
            <label className="block font-medium text-slate-600 mb-1">Assignment Strategy</label>
            <select
              disabled={isReadOnly}
              value={config.assignmentStrategy || 'DIRECT_CLAIM'}
              onChange={(e) => handleConfigChange('assignmentStrategy', e.target.value)}
              className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white disabled:bg-slate-50"
            >
              <option value="DIRECT_CLAIM">Unassigned Queue (Operator Claims Task)</option>
              <option value="ROUND_ROBIN">Round Robin Distribution</option>
              <option value="LEAST_LOADED">Least Loaded Operator</option>
            </select>
          </div>

          <div>
            <label className="block font-medium text-slate-600 mb-1">SLA Fulfillment Duration (Hours)</label>
            <input
              type="number"
              min="1"
              max="720"
              disabled={isReadOnly}
              value={config.slaHours || 120}
              onChange={(e) => handleConfigChange('slaHours', parseInt(e.target.value) || 72)}
              className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:bg-slate-50"
            />
          </div>

          <div>
            <label className="block font-medium text-slate-600 mb-1">Task Priority</label>
            <select
              disabled={isReadOnly}
              value={config.priority || 'MEDIUM'}
              onChange={(e) => handleConfigChange('priority', e.target.value)}
              className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white disabled:bg-slate-50"
            >
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="URGENT">Urgent</option>
            </select>
          </div>
        </div>
      )}

      {/* END CONFIGURATION */}
      {data.nodeType === 'END' && (
        <div className="pt-3 border-t border-slate-100 space-y-3">
          <div className="font-semibold text-slate-800 flex items-center gap-1.5">
            <CheckCircle className="h-3.5 w-3.5 text-rose-600" />
            <span>Terminal Outcome</span>
          </div>

          <div>
            <label className="block font-medium text-slate-600 mb-1">Final Request Outcome</label>
            <select
              disabled={isReadOnly}
              value={config.outcome || 'COMPLETED'}
              onChange={(e) => handleConfigChange('outcome', e.target.value)}
              className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white disabled:bg-slate-50"
            >
              <option value="COMPLETED">COMPLETED (All approvals and fulfillment fulfilled)</option>
              <option value="REJECTED">REJECTED (Request dismissed without fulfillment)</option>
              <option value="CANCELLED">CANCELLED (Cancelled by requester)</option>
            </select>
          </div>
        </div>
      )}
    </div>
  );
};

/* -------------------------------------------------------------------------- */
/* Edge Form Component                                                        */
/* -------------------------------------------------------------------------- */
interface EdgeConfigFormProps {
  edge: Edge;
  onUpdate: (updates: Partial<WorkflowEdgeData>) => void;
  isReadOnly: boolean;
}

const EdgeConfigForm: React.FC<EdgeConfigFormProps> = ({ edge, onUpdate, isReadOnly }) => {
  const edgeData = (edge.data || {}) as WorkflowEdgeData;
  const cond = edgeData.conditionExpression || {};
  const isDefault = edgeData.isDefault || false;

  return (
    <div className="space-y-4 text-xs">
      <div className="flex items-center justify-between p-2.5 bg-slate-50 rounded-xl border border-slate-200 font-mono text-[11px]">
        <span className="text-slate-700 font-semibold">{edge.source}</span>
        <span className="text-slate-400">➔</span>
        <span className="text-slate-700 font-semibold">{edge.target}</span>
      </div>

      {/* Default Route Checkbox */}
      <div className="flex items-start gap-2.5 p-3 bg-slate-50/80 rounded-xl border border-slate-200">
        <input
          type="checkbox"
          id="isDefaultRoute"
          disabled={isReadOnly}
          checked={isDefault}
          onChange={(e) => {
            onUpdate({
              isDefault: e.target.checked,
              conditionExpression: e.target.checked ? undefined : cond
            });
          }}
          className="mt-0.5 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
        />
        <label htmlFor="isDefaultRoute" className="cursor-pointer">
          <span className="font-semibold text-slate-800 block">Default Fallback Route</span>
          <span className="text-[10px] text-slate-500 block leading-tight">
            Followed if no other conditional transitions match.
          </span>
        </label>
      </div>

      {!isDefault && (
        <div className="space-y-3">
          <div>
            <label className="block font-medium text-slate-600 mb-1">Field Path</label>
            <input
              type="text"
              disabled={isReadOnly}
              value={cond.field || 'payload.estimatedAmount'}
              onChange={(e) => {
                onUpdate({
                  conditionExpression: { ...cond, field: e.target.value }
                });
              }}
              className="w-full px-3 py-2 font-mono text-xs border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:bg-slate-50"
            />
          </div>

          <div>
            <label className="block font-medium text-slate-600 mb-1">Operator</label>
            <select
              disabled={isReadOnly}
              value={cond.operator || 'LESS_THAN_OR_EQUAL'}
              onChange={(e) => {
                onUpdate({
                  conditionExpression: { ...cond, operator: e.target.value }
                });
              }}
              className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white disabled:bg-slate-50"
            >
              <option value="EQUALS">EQUALS (=)</option>
              <option value="NOT_EQUALS">NOT_EQUALS (≠)</option>
              <option value="LESS_THAN">LESS_THAN (&lt;)</option>
              <option value="LESS_THAN_OR_EQUAL">LESS_THAN_OR_EQUAL (≤)</option>
              <option value="GREATER_THAN">GREATER_THAN (&gt;)</option>
              <option value="GREATER_THAN_OR_EQUAL">GREATER_THAN_OR_EQUAL (≥)</option>
              <option value="BETWEEN">BETWEEN (Range [min, max])</option>
              <option value="IN">IN (List of allowed values)</option>
              <option value="IS_EMPTY">IS_EMPTY (Null or empty)</option>
            </select>
          </div>

          <div>
            <label className="block font-medium text-slate-600 mb-1">Comparison Value</label>
            <input
              type="text"
              disabled={isReadOnly}
              value={cond.value !== undefined ? cond.value : '10000'}
              onChange={(e) => {
                const num = Number(e.target.value);
                const val = isNaN(num) ? e.target.value : num;
                onUpdate({
                  conditionExpression: { ...cond, value: val }
                });
              }}
              className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:bg-slate-50"
            />
          </div>

          <div>
            <label className="block font-medium text-slate-600 mb-1">Evaluation Priority</label>
            <input
              type="number"
              min="0"
              max="50"
              disabled={isReadOnly}
              value={edgeData.priority ?? 1}
              onChange={(e) => onUpdate({ priority: parseInt(e.target.value) || 0 })}
              className="w-full px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500 disabled:bg-slate-50"
            />
            <p className="text-[10px] text-slate-400 mt-0.5">Lower numbers are evaluated first</p>
          </div>
        </div>
      )}
    </div>
  );
};
