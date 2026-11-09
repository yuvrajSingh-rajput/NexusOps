package com.nexusops.workflow.application.handler;

import com.nexusops.workflow.domain.NodeType;
import org.springframework.stereotype.Component;

@Component
public class StartNodeHandler implements NodeHandler {

    @Override
    public NodeType getNodeType() {
        return NodeType.START;
    }

    @Override
    public NodeExecutionResult execute(WorkflowExecutionContext context) {
        return NodeExecutionResult.completed();
    }
}

