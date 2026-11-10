import React from 'react';
import {
  BaseEdge,
  EdgeLabelRenderer,
  EdgeProps,
  getSmoothStepPath
} from '@xyflow/react';
import { WorkflowEdgeData } from '../types/workflowCanvas';

interface EdgeVisualConfig {
  labelText: string;
  category: 'approved' | 'rejected' | 'conditional' | 'default' | 'neutral';
  strokeColor: string;
  badgeStyle: string;
  dotColor: string;
  isDashed: boolean;
}

function parseConditionConfig(cond: Record<string, any> | undefined, isDefault: boolean): EdgeVisualConfig {
  if (isDefault) {
    return {
      labelText: 'Default Route',
      category: 'default',
      strokeColor: '#94a3b8', // slate-400
      badgeStyle: 'bg-white text-slate-700 border-slate-300 hover:border-slate-400',
      dotColor: 'bg-slate-400',
      isDashed: true
    };
  }

  if (!cond) {
    return {
      labelText: '',
      category: 'neutral',
      strokeColor: '#94a3b8',
      badgeStyle: 'bg-white text-slate-600 border-slate-200',
      dotColor: 'bg-slate-400',
      isDashed: false
    };
  }

  // 1. Approval Decisions (Approved / Rejected)
  if (cond.field === 'context.signal' || cond.field === 'signal') {
    if (cond.value === 'APPROVED') {
      return {
        labelText: 'Approved',
        category: 'approved',
        strokeColor: '#10b981', // emerald-500
        badgeStyle: 'bg-white text-emerald-800 border-emerald-300 shadow-xs hover:border-emerald-400',
        dotColor: 'bg-emerald-500',
        isDashed: false
      };
    }
    if (cond.value === 'REJECTED') {
      return {
        labelText: 'Rejected',
        category: 'rejected',
        strokeColor: '#ef4444', // rose-500
        badgeStyle: 'bg-white text-rose-800 border-rose-300 shadow-xs hover:border-rose-400',
        dotColor: 'bg-rose-500',
        isDashed: false
      };
    }
    return {
      labelText: String(cond.value),
      category: 'conditional',
      strokeColor: '#f59e0b', // amber-500
      badgeStyle: 'bg-white text-amber-800 border-amber-300 shadow-xs hover:border-amber-400',
      dotColor: 'bg-amber-500',
      isDashed: false
    };
  }

  // 2. Compound AND range (e.g. > $10,000 AND <= $50,000)
  if (cond.logicalOperator === 'AND' && Array.isArray(cond.conditions) && cond.conditions.length === 2) {
    const c1 = cond.conditions[0];
    const c2 = cond.conditions[1];
    const minVal = c1.operator === 'GREATER_THAN' || c1.operator === 'GREATER_THAN_OR_EQUAL' ? c1.value : c2.value;
    const maxVal = c1.operator === 'LESS_THAN' || c1.operator === 'LESS_THAN_OR_EQUAL' ? c1.value : c2.value;

    const minFmt = typeof minVal === 'number' ? `$${minVal.toLocaleString()}` : minVal;
    const maxFmt = typeof maxVal === 'number' ? `$${maxVal.toLocaleString()}` : maxVal;

    return {
      labelText: `Amount > ${minFmt} & ≤ ${maxFmt}`,
      category: 'conditional',
      strokeColor: '#3b82f6', // blue-500
      badgeStyle: 'bg-white text-blue-800 border-blue-300 shadow-xs hover:border-blue-400',
      dotColor: 'bg-blue-500',
      isDashed: false
    };
  }

  // 3. Single field condition (e.g. Amount <= $10,000)
  if (cond.field && cond.operator) {
    const rawField = String(cond.field);
    const cleanField = rawField
      .replace(/^payload\./, '')
      .replace(/^context\./, '')
      .replace(/([A-Z])/g, ' $1')
      .replace(/^./, (str) => str.toUpperCase())
      .replace(/Estimated Amount/i, 'Amount');

    let opSymbol = cond.operator;
    switch (cond.operator) {
      case 'LESS_THAN_OR_EQUAL':
        opSymbol = '≤';
        break;
      case 'LESS_THAN':
        opSymbol = '<';
        break;
      case 'GREATER_THAN_OR_EQUAL':
        opSymbol = '≥';
        break;
      case 'GREATER_THAN':
        opSymbol = '>';
        break;
      case 'EQUALS':
        opSymbol = '=';
        break;
      case 'NOT_EQUALS':
        opSymbol = '≠';
        break;
      case 'IN':
        opSymbol = 'in';
        break;
      case 'BETWEEN':
        opSymbol = 'between';
        break;
      case 'IS_EMPTY':
        return {
          labelText: `${cleanField} is empty`,
          category: 'conditional',
          strokeColor: '#3b82f6',
          badgeStyle: 'bg-white text-blue-800 border-blue-300 shadow-xs',
          dotColor: 'bg-blue-500',
          isDashed: false
        };
      default:
        break;
    }

    let valFmt = cond.value;
    if (typeof cond.value === 'number') {
      valFmt = cleanField.toLowerCase().includes('amount') ? `$${cond.value.toLocaleString()}` : cond.value;
    }

    return {
      labelText: `${cleanField} ${opSymbol} ${valFmt}`,
      category: 'conditional',
      strokeColor: '#3b82f6', // blue-500
      badgeStyle: 'bg-white text-blue-800 border-blue-300 shadow-xs hover:border-blue-400',
      dotColor: 'bg-blue-500',
      isDashed: false
    };
  }

  return {
    labelText: 'Conditional',
    category: 'conditional',
    strokeColor: '#3b82f6',
    badgeStyle: 'bg-white text-blue-800 border-blue-300 shadow-xs',
    dotColor: 'bg-blue-500',
    isDashed: false
  };
}

export const CustomEdge: React.FC<EdgeProps> = ({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  style = {},
  data,
  selected
}) => {
  // Use smoothstep (orthogonal with rounded corners) to avoid messy line crossings
  const [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
    borderRadius: 14
  });

  const edgeData = (data || {}) as WorkflowEdgeData;
  const isDefault = edgeData.isDefault ?? false;
  const cond = edgeData.conditionExpression;
  const isActivePath = edgeData.isActivePath ?? false;

  const config = parseConditionConfig(cond, isDefault);

  // Compute final stroke & styling
  const finalStroke = isActivePath ? '#2563eb' : selected ? '#4f46e5' : config.strokeColor;
  const strokeWidth = isActivePath ? 3 : selected ? 2.5 : 1.85;

  return (
    <>
      {/* SVG Marker Definitions for this Edge */}
      <svg style={{ position: 'absolute', top: 0, left: 0, pointerEvents: 'none', width: 0, height: 0 }}>
        <defs>
          <marker
            id={`marker-${id}`}
            viewBox="0 0 10 10"
            refX="6"
            refY="5"
            markerWidth="6"
            markerHeight="6"
            orient="auto-start-reverse"
          >
            <path d="M 0 1 L 8 5 L 0 9 z" fill={finalStroke} />
          </marker>
        </defs>
      </svg>

      <BaseEdge
        id={id}
        path={edgePath}
        markerEnd={`url(#marker-${id})`}
        style={{
          ...style,
          strokeWidth,
          stroke: finalStroke,
          strokeDasharray: config.isDashed ? '6 4' : undefined,
          transition: 'stroke 0.2s, stroke-width 0.2s'
        }}
      />

      {/* Readable Pill Badge Label */}
      {config.labelText && (
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
              className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-[11px] font-semibold tracking-wide border shadow-xs transition-all duration-150 hover:scale-105 select-none ${
                config.badgeStyle
              } ${selected ? 'ring-2 ring-indigo-500 shadow-md scale-105' : ''} ${
                isActivePath ? 'ring-2 ring-blue-500 shadow-md' : ''
              }`}
            >
              <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${config.dotColor}`} />
              <span className="whitespace-nowrap">{config.labelText}</span>
            </div>
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
};
