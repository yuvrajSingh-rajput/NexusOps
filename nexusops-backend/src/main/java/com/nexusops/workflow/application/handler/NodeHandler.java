package com.nexusops.workflow.application.handler;

import com.nexusops.workflow.domain.NodeType;

public interface NodeHandler {

    NodeType getNodeType();

    /**
     * Executes when the workflow enters this node.
     */
    NodeExecutionResult execute(WorkflowExecutionContext context);

    /**
     * Executes when an external signal (approval decision, task completion) is delivered to this node.
     */
    default NodeExecutionResult handleSignal(WorkflowExecutionContext context) {
        return NodeExecutionResult.completed();
    }
}
