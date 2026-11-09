package com.nexusops.request.domain;

public enum RequestStatus {
    DRAFT,
    SUBMITTED,
    IN_REVIEW,
    WAITING_FOR_INFORMATION,
    APPROVED,
    IN_PROGRESS,
    ON_HOLD,
    COMPLETED,
    REJECTED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED;
    }

    public boolean canBeCancelled() {
        return this == DRAFT || this == SUBMITTED || this == IN_REVIEW || this == WAITING_FOR_INFORMATION;
    }
}

