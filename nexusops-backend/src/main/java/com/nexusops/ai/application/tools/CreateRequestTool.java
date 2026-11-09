package com.nexusops.ai.application.tools;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CreateRequestTool implements NexusAiTool {

    @Override
    public String getName() {
        return "createRequest";
    }

    @Override
    public String getDescription() {
        return "Prepare a new employee service request (e.g. laptop, equipment, reimbursement) requiring user confirmation.";
    }

    @Override
    public String getRequiredPermission() {
        return "REQUEST_CREATE";
    }

    @Override
    public boolean isWriteAction() {
        return true;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ToolExecutionContext context) {
        String title = (String) arguments.get("title");
        if (title == null || title.isBlank()) {
            return ToolExecutionResult.failure("A request title is required.");
        }

        String summary = String.format("Request Proposal Prepared: \"%s\"", title);
        return ToolExecutionResult.success(arguments, summary);
    }
}

