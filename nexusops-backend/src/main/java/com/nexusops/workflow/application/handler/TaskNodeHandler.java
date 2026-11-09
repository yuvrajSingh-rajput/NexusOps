package com.nexusops.workflow.application.handler;

import com.nexusops.workflow.domain.NodeType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TaskNodeHandler implements NodeHandler {

    @Override
    public NodeType getNodeType() {
        return NodeType.TASK;
    }

    @Override
    public NodeExecutionResult execute(WorkflowExecutionContext context) {
        // Enters WAITING state until fulfillment operator claims and completes the task
        Map<String, Object> config = context.getCurrentNode().getConfiguration();
        return NodeExecutionResult.waiting(Map.of(
                "nodeKey", context.getCurrentNode().getNodeKey(),
                "taskConfig", config != null ? config : Map.of()
        ));
    }

    @Override
    public NodeExecutionResult handleSignal(WorkflowExecutionContext context) {
        String signal = context.getSignal();
        if ("COMPLETED".equalsIgnoreCase(signal)) {
            return NodeExecutionResult.completed(context.getSignalData());
        }
        if ("CANCELLED".equalsIgnoreCase(signal)) {
            return NodeExecutionResult.terminated("Fulfillment task was cancelled");
        }
        return NodeExecutionResult.waiting(context.getSignalData());
    }
}

