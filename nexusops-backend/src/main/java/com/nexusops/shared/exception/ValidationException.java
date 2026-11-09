package com.nexusops.shared.exception;

import com.nexusops.shared.api.ErrorDetail;

import java.util.List;

public class ValidationException extends NexusOpsException {
    private final List<ErrorDetail> details;

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
        this.details = List.of();
    }

    public ValidationException(String message, List<ErrorDetail> details) {
        super("VALIDATION_ERROR", message);
        this.details = details;
    }

    public List<ErrorDetail> getDetails() {
        return details;
    }
}

