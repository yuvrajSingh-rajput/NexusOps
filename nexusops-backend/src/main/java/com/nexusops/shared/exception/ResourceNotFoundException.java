package com.nexusops.shared.exception;

public class ResourceNotFoundException extends NexusOpsException {
    public ResourceNotFoundException(String resourceName, Object identifier) {
        super("RESOURCE_NOT_FOUND", String.format("%s not found with identifier: %s", resourceName, identifier));
    }

    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message);
    }
}
