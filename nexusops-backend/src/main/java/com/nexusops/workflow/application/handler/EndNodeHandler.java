package com.nexusops.workflow.application.handler;

import com.nexusops.workflow.domain.NodeType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EndNodeHandler implements NodeHandler {

    @Override
    public NodeType getNodeType() {
        return NodeType.END;
    }

    @Override
    public NodeExecutionResult execute(WorkflowExecutionContext context) {
        Map<String, Object> config = context.getCurrentNode().getConfiguration();
        boolean isRejection = false;

        if (config != null) {
            Object outcome = config.get("outcome");
            if ("REJECTED".equalsIgnoreCase(String.valueOf(outcome))) {
                isRejection = true;
            }
        }

        if (context.getCurrentNode().getNodeKey().toLowerCase().contains("rej")) {
            isRejection = true;
        }

        if (isRejection) {
            return NodeExecutionResult.terminated("Workflow terminated at rejection node: " + context.getCurrentNode().getNodeKey());
        }

        return NodeExecutionResult.completed();
    }
}

