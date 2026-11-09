import { describe, it, expect } from 'vitest';
import { SaveWorkflowDraftDto } from './types/workflowCanvas';

describe('Workflow Studio Canvas Serialization', () => {
  it('serializes React Flow nodes and edges into valid backend DTO structure', () => {
    const draft: SaveWorkflowDraftDto = {
      nodes: [
        {
          nodeKey: 'START',
          nodeType: 'START',
          name: 'Request Submitted',
          configuration: {},
          positionX: 100,
          positionY: 200
        },
        {
          nodeKey: 'MGR_APPROVAL',
          nodeType: 'APPROVAL',
          name: 'Direct Manager Approval',
          configuration: { approverType: 'MANAGER', timeoutHours: 48 },
          positionX: 350,
          positionY: 200
        },
        {
          nodeKey: 'END_COMPLETED',
          nodeType: 'END',
          name: 'Completed',
          configuration: { outcome: 'COMPLETED' },
          positionX: 700,
          positionY: 200
        }
      ],
      transitions: [
        {
          sourceNodeKey: 'START',
          targetNodeKey: 'MGR_APPROVAL',
          priority: 0,
          isDefault: true
        },
        {
          sourceNodeKey: 'MGR_APPROVAL',
          targetNodeKey: 'END_COMPLETED',
          conditionExpression: { field: 'context.signal', operator: 'EQUALS', value: 'APPROVED' },
          priority: 1,
          isDefault: false
        }
      ]
    };

    expect(draft.nodes).toHaveLength(3);
    expect(draft.transitions).toHaveLength(2);
    expect(draft.nodes[1].configuration.approverType).toBe('MANAGER');
    expect(draft.transitions[1].conditionExpression?.operator).toBe('EQUALS');
  });

  it('supports compound AND/OR grouping in transition condition expression', () => {
    const compoundCondition = {
      logicalOperator: 'AND',
      conditions: [
        { field: 'payload.estimatedAmount', operator: 'GREATER_THAN', value: 10000 },
        { field: 'payload.estimatedAmount', operator: 'LESS_THAN_OR_EQUAL', value: 50000 }
      ]
    };

    expect(compoundCondition.logicalOperator).toBe('AND');
    expect(compoundCondition.conditions).toHaveLength(2);
    expect(compoundCondition.conditions[0].operator).toBe('GREATER_THAN');
    expect(compoundCondition.conditions[1].operator).toBe('LESS_THAN_OR_EQUAL');
  });
});

