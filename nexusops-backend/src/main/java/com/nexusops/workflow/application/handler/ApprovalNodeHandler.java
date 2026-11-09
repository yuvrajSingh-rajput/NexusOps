package com.nexusops.workflow.application.handler;

import com.nexusops.shared.events.ApprovalRequestedEvent;
import com.nexusops.workflow.domain.NodeType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ApprovalNodeHandler implements NodeHandler {

    private final ApplicationEventPublisher eventPublisher;

    public ApprovalNodeHandler(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public NodeType getNodeType() {
        return NodeType.APPROVAL;
    }

    @Override
    public NodeExecutionResult execute(WorkflowExecutionContext context) {
        // Enters WAITING state for human interaction
        Map<String, Object> config = context.getCurrentNode().getConfiguration();
        int timeoutHours = 48;
        if (config != null && config.containsKey("timeoutHours")) {
            try {
                timeoutHours = Integer.parseInt(String.valueOf(config.get("timeoutHours")));
            } catch (Exception ignored) {}
        }

        eventPublisher.publishEvent(new ApprovalRequestedEvent(
                context.getOrganizationId(),
                context.getNodeInstanceId(),
                context.getRequestId(),
                config != null ? config : Map.of(),
                timeoutHours
        ));

        return NodeExecutionResult.waiting(Map.of(
                "nodeKey", context.getCurrentNode().getNodeKey(),
                "approverConfig", config != null ? config : Map.of()
        ));
    }

    @Override
    public NodeExecutionResult handleSignal(WorkflowExecutionContext context) {
        String signal = context.getSignal();
        if ("REJECTED".equalsIgnoreCase(signal) || "APPROVED".equalsIgnoreCase(signal)) {
            return NodeExecutionResult.completed(context.getSignalData());
        }
        // E.g. WAITING_FOR_INFORMATION
        return NodeExecutionResult.waiting(context.getSignalData());
    }
}

