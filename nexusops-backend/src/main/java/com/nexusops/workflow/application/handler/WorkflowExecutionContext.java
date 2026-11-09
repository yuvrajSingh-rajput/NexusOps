package com.nexusops.workflow.application.handler;

import com.nexusops.workflow.domain.WorkflowNodeEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WorkflowExecutionContext {

    private final UUID organizationId;
    private final UUID requestId;
    private final UUID workflowInstanceId;
    private final UUID nodeInstanceId;
    private final WorkflowNodeEntity currentNode;
    private final Map<String, Object> contextData;
    private String signal;
    private Map<String, Object> signalData;

    public WorkflowExecutionContext(UUID organizationId, UUID requestId, UUID workflowInstanceId,
                                  UUID nodeInstanceId, WorkflowNodeEntity currentNode,
                                  Map<String, Object> contextData) {
        this.organizationId = organizationId;
        this.requestId = requestId;
        this.workflowInstanceId = workflowInstanceId;
        this.nodeInstanceId = nodeInstanceId;
        this.currentNode = currentNode;
        this.contextData = contextData != null ? contextData : new HashMap<>();
        this.signalData = new HashMap<>();
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public UUID getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public UUID getNodeInstanceId() {
        return nodeInstanceId;
    }

    public WorkflowNodeEntity getCurrentNode() {
        return currentNode;
    }

    public Map<String, Object> getContextData() {
        return contextData;
    }

    public String getSignal() {
        return signal;
    }

    public void setSignal(String signal) {
        this.signal = signal;
    }

    public Map<String, Object> getSignalData() {
        return signalData;
    }

    public void setSignalData(Map<String, Object> signalData) {
        this.signalData = signalData != null ? signalData : new HashMap<>();
    }
}

