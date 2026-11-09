package com.nexusops.ai.application.tools;

public record ToolExecutionResult(
        boolean success,
        Object data,
        String summary,
        String errorMessage
) {
    public static ToolExecutionResult success(Object data, String summary) {
        return new ToolExecutionResult(true, data, summary, null);
    }

    public static ToolExecutionResult failure(String errorMessage) {
        return new ToolExecutionResult(false, null, null, errorMessage);
    }
}

