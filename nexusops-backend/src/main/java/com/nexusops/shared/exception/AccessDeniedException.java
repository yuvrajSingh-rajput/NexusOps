package com.nexusops.shared.exception;

public class AccessDeniedException extends NexusOpsException {
    public AccessDeniedException(String message) {
        super("FORBIDDEN", message);
    }
}

