package com.nexusops.shared.exception;

public class ConflictException extends NexusOpsException {
    public ConflictException(String code, String message) {
        super(code, message);
    }

    public ConflictException(String message) {
        super("CONFLICT", message);
    }
}
