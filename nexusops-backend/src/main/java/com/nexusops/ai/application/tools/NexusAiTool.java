package com.nexusops.ai.application.tools;

import java.util.Map;

/**
 * Governed AI Tool interface.
 * All tools must be registered as Spring Beans and strictly adhere to server-side authorization.
 */
public interface NexusAiTool {

    String getName();

    String getDescription();

    String getRequiredPermission();

    boolean isWriteAction();

    ToolExecutionResult execute(Map<String, Object> arguments, ToolExecutionContext context);
}

