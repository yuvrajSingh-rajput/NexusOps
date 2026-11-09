import React from 'react';
import {
  Play,
  UserCheck,
  GitBranch,
  ClipboardList,
  Flag,
  Plus,
  Lock
} from 'lucide-react';
import { WorkflowNodeType } from '../types/workflowCanvas';

interface NodePaletteProps {
  onAddNode: (type: WorkflowNodeType) => void;
  isReadOnly: boolean;
}

export const NodePalette: React.FC<NodePaletteProps> = ({ onAddNode, isReadOnly }) => {
  const paletteItems: {
    type: WorkflowNodeType;
    label: string;
    description: string;
    icon: React.ReactNode;
    colorClasses: string;
  }[] = [
    {
      type: 'START',
      label: 'Start Node',
      description: 'Trigger point',
      icon: <Play className="h-4 w-4 fill-emerald-600 text-emerald-600" />,
      colorClasses: 'border-emerald-200 hover:border-emerald-400 bg-emerald-50/50 text-emerald-800'
    },
    {
      type: 'APPROVAL',
      label: 'Approval',
      description: 'Manager / Role sign-off',
      icon: <UserCheck className="h-4 w-4 text-amber-600" />,
      colorClasses: 'border-amber-200 hover:border-amber-400 bg-amber-50/50 text-amber-800'
    },
    {
      type: 'CONDITION',
      label: 'Condition',
      description: 'Data branch evaluator',
      icon: <GitBranch className="h-4 w-4 text-sky-600" />,
      colorClasses: 'border-sky-200 hover:border-sky-400 bg-sky-50/50 text-sky-800'
    },
    {
      type: 'TASK',
      label: 'Task',
      description: 'Operational queue fulfillment',
      icon: <ClipboardList className="h-4 w-4 text-purple-600" />,
      colorClasses: 'border-purple-200 hover:border-purple-400 bg-purple-50/50 text-purple-800'
    },
    {
      type: 'END',
      label: 'End Node',
      description: 'Terminal outcome',
      icon: <Flag className="h-4 w-4 text-rose-600" />,
      colorClasses: 'border-rose-200 hover:border-rose-400 bg-rose-50/50 text-rose-800'
    }
  ];

  const onDragStart = (event: React.DragEvent, nodeType: WorkflowNodeType) => {
    if (isReadOnly) return;
    event.dataTransfer.setData('application/reactflow', nodeType);
    event.dataTransfer.effectAllowed = 'move';
  };

  return (
    <div className="bg-white/95 backdrop-blur-md rounded-2xl border border-slate-200/80 shadow-lg p-3">
      <div className="flex items-center justify-between mb-2 px-1">
        <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500">
          Node Palette
        </span>
        {isReadOnly && (
          <span className="inline-flex items-center gap-1 text-[10px] text-amber-700 bg-amber-50 px-2 py-0.5 rounded-full border border-amber-200 font-medium">
            <Lock className="h-2.5 w-2.5" /> Read-Only
          </span>
        )}
      </div>

      <div className="flex flex-col gap-1.5">
        {paletteItems.map((item) => (
          <button
            key={item.type}
            type="button"
            disabled={isReadOnly}
            draggable={!isReadOnly}
            onDragStart={(e) => onDragStart(e, item.type)}
            onClick={() => onAddNode(item.type)}
            className={`flex items-center justify-between p-2 rounded-xl border text-left transition-all ${
              item.colorClasses
            } ${
              isReadOnly
                ? 'opacity-50 cursor-not-allowed'
                : 'hover:shadow-sm cursor-grab active:cursor-grabbing hover:scale-[1.02]'
            }`}
          >
            <div className="flex items-center gap-2.5">
              <div className="p-1.5 bg-white rounded-lg shadow-2xs">
                {item.icon}
              </div>
              <div>
                <div className="text-xs font-semibold leading-tight">{item.label}</div>
                <div className="text-[10px] text-slate-500 leading-tight">{item.description}</div>
              </div>
            </div>
            {!isReadOnly && <Plus className="h-3.5 w-3.5 opacity-40 hover:opacity-100" />}
          </button>
        ))}
      </div>

      <div className="mt-2.5 pt-2 border-t border-slate-100 text-[10px] text-slate-400 text-center">
        {isReadOnly ? 'Version is published and immutable' : 'Drag or click to place node on canvas'}
      </div>
    </div>
  );
};

