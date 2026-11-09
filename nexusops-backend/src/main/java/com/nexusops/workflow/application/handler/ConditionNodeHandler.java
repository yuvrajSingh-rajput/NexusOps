package com.nexusops.workflow.application.handler;

import com.nexusops.workflow.domain.NodeType;
import org.springframework.stereotype.Component;

@Component
public class ConditionNodeHandler implements NodeHandler {

    @Override
    public NodeType getNodeType() {
        return NodeType.CONDITION;
    }

    @Override
    public NodeExecutionResult execute(WorkflowExecutionContext context) {
        // Condition nodes complete immediately, allowing the engine to evaluate outbound transition predicates
        return NodeExecutionResult.completed();
    }
}

