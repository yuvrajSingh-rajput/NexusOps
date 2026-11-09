package com.nexusops.workflow.application.handler;

import com.nexusops.workflow.domain.NodeType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ApprovalNodeHandler implements NodeHandler {

    @Override
    public NodeType getNodeType() {
        return NodeType.APPROVAL;
    }

    @Override
    public NodeExecutionResult execute(WorkflowExecutionContext context) {
        // Enters WAITING state for human interaction
        Map<String, Object> config = context.getCurrentNode().getConfiguration();
        return NodeExecutionResult.waiting(Map.of(
                "nodeKey", context.getCurrentNode().getNodeKey(),
                "approverConfig", config != null ? config : Map.of()
        ));
    }

    @Override
    public NodeExecutionResult handleSignal(WorkflowExecutionContext context) {
        String signal = context.getSignal();
        if ("REJECTED".equalsIgnoreCase(signal)) {
            return NodeExecutionResult.terminated("Approval was rejected");
        }
        if ("APPROVED".equalsIgnoreCase(signal)) {
            return NodeExecutionResult.completed(context.getSignalData());
        }
        // E.g. WAITING_FOR_INFORMATION
        return NodeExecutionResult.waiting(context.getSignalData());
    }
}
