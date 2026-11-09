package com.nexusops.workflow.application.handler;

import java.util.HashMap;
import java.util.Map;

public class NodeExecutionResult {

    public enum Status {
        COMPLETED,
        WAITING,
        TERMINATED,
        FAILED
    }

    private final Status status;
    private final Map<String, Object> resultData;
    private final String terminationReason;

    public NodeExecutionResult(Status status, Map<String, Object> resultData, String terminationReason) {
        this.status = status;
        this.resultData = resultData != null ? resultData : new HashMap<>();
        this.terminationReason = terminationReason;
    }

    public static NodeExecutionResult completed() {
        return new NodeExecutionResult(Status.COMPLETED, new HashMap<>(), null);
    }

    public static NodeExecutionResult completed(Map<String, Object> resultData) {
        return new NodeExecutionResult(Status.COMPLETED, resultData, null);
    }

    public static NodeExecutionResult waiting() {
        return new NodeExecutionResult(Status.WAITING, new HashMap<>(), null);
    }

    public static NodeExecutionResult waiting(Map<String, Object> resultData) {
        return new NodeExecutionResult(Status.WAITING, resultData, null);
    }

    public static NodeExecutionResult terminated(String reason) {
        return new NodeExecutionResult(Status.TERMINATED, new HashMap<>(), reason);
    }

    public static NodeExecutionResult failed(String reason) {
        return new NodeExecutionResult(Status.FAILED, new HashMap<>(), reason);
    }

    public Status getStatus() {
        return status;
    }

    public Map<String, Object> getResultData() {
        return resultData;
    }

    public String getTerminationReason() {
        return terminationReason;
    }
}
