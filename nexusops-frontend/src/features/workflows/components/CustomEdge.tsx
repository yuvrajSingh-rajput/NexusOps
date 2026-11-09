import React from 'react';
import {
  BaseEdge,
  EdgeLabelRenderer,
  EdgeProps,
  getBezierPath
} from '@xyflow/react';
import { WorkflowEdgeData } from '../types/workflowCanvas';

export const CustomEdge: React.FC<EdgeProps> = ({
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  style = {},
  markerEnd,
  data,
  selected
}) => {
  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition
  });

  const edgeData = (data || {}) as WorkflowEdgeData;
  const isDefault = edgeData.isDefault;
  const cond = edgeData.conditionExpression;

  // Format human-readable routing label
  let labelText = '';
  let labelBadgeColor = 'bg-slate-100 text-slate-700 border-slate-200';

  if (cond) {
    if (cond.logicalOperator === 'AND' && Array.isArray(cond.conditions)) {
      labelText = 'Amount > $10k & ≤ $50k';
      labelBadgeColor = 'bg-blue-50 text-blue-700 border-blue-200';
    } else if (cond.field === 'context.signal') {
      if (cond.value === 'APPROVED') {
        labelText = 'APPROVED';
        labelBadgeColor = 'bg-emerald-50 text-emerald-700 border-emerald-200';
      } else if (cond.value === 'REJECTED') {
        labelText = 'REJECTED';
        labelBadgeColor = 'bg-red-50 text-red-700 border-red-200';
      } else {
        labelText = `${cond.value}`;
        labelBadgeColor = 'bg-amber-50 text-amber-700 border-amber-200';
      }
    } else if (cond.field && cond.operator) {
      const op = cond.operator === 'LESS_THAN_OR_EQUAL' ? '≤' :
                 cond.operator === 'GREATER_THAN' ? '>' :
                 cond.operator === 'EQUALS' ? '=' : cond.operator;
      const val = typeof cond.value === 'number' ? `$${cond.value.toLocaleString()}` : cond.value;
      labelText = `${cond.field.replace('payload.', '')} ${op} ${val}`;
      labelBadgeColor = 'bg-indigo-50 text-indigo-700 border-indigo-200';
    }
  } else if (isDefault) {
    labelText = 'DEFAULT';
    labelBadgeColor = 'bg-slate-100 text-slate-600 border-slate-200';
  }

  return (
    <>
      <BaseEdge
        path={edgePath}
        markerEnd={markerEnd}
        style={{
          ...style,
          strokeWidth: selected ? 2.5 : 1.75,
          stroke: selected ? '#6366f1' : '#94a3b8'
        }}
      />
      {labelText && (
        <EdgeLabelRenderer>
          <div
            style={{
              position: 'absolute',
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
              pointerEvents: 'all'
            }}
            className="nodrag nopan"
          >
            <div
              className={`flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[10px] font-semibold tracking-wide border shadow-sm transition-transform hover:scale-105 cursor-pointer ${labelBadgeColor} ${
                selected ? 'ring-2 ring-indigo-400' : ''
              }`}
            >
              <span>{labelText}</span>
            </div>
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
};
