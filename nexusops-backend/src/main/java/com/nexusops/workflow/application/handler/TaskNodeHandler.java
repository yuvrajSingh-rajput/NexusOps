package com.nexusops.workflow.application.handler;

import com.nexusops.shared.events.TaskRequestedEvent;
import com.nexusops.workflow.domain.NodeType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TaskNodeHandler implements NodeHandler {

    private final ApplicationEventPublisher eventPublisher;

    public TaskNodeHandler(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.TASK;
    }

    @Override
    public NodeExecutionResult execute(WorkflowExecutionContext context) {
        // Enters WAITING state until fulfillment operator claims and completes the task
        Map<String, Object> config = context.getCurrentNode().getConfiguration();

        eventPublisher.publishEvent(new TaskRequestedEvent(
                context.getOrganizationId(),
                context.getNodeInstanceId(),
                context.getWorkflowInstanceId(),
                context.getRequestId(),
                context.getCurrentNode().getNodeKey(),
                context.getCurrentNode().getTitle(),
                config != null ? config : Map.of()
        ));

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

