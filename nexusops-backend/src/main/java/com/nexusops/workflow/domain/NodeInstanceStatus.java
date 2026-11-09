package com.nexusops.workflow.domain;

public enum NodeInstanceStatus {
    PENDING,
    READY,
    RUNNING,
    WAITING,
    COMPLETED,
    FAILED,
    SKIPPED,
    CANCELLED
}
