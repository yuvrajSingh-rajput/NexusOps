package com.nexusops.shared.exception;

public class NexusOpsException extends RuntimeException {
    private final String code;

    public NexusOpsException(String code, String message) {
        super(message);
        this.code = code;
    }

    public NexusOpsException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
