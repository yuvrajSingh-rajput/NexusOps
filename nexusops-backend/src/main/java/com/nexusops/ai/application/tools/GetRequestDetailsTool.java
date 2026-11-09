package com.nexusops.ai.application.tools;

import com.nexusops.request.application.RequestService;
import com.nexusops.request.application.dto.RequestResponseDto;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class GetRequestDetailsTool implements NexusAiTool {

    private final RequestService requestService;

    public GetRequestDetailsTool(RequestService requestService) {
        this.requestService = requestService;
    }

    @Override
    public String getName() {
        return "getRequestDetails";
    }

    @Override
    public String getDescription() {
        return "Retrieve complete details, status, and dynamic payload for a specific request ID.";
    }

    @Override
    public String getRequiredPermission() {
        return "REQUEST_VIEW_OWN";
    }

    @Override
    public boolean isWriteAction() {
        return false;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ToolExecutionContext context) {
        Object idObj = arguments.get("requestId");
        if (idObj == null) {
            return ToolExecutionResult.failure("Missing required parameter: requestId");
        }

        try {
            UUID requestId = UUID.fromString(idObj.toString());
            RequestResponseDto request = requestService.getRequest(context.organizationId(), requestId);

            String summary = String.format("Request %s: \"%s\"\nCategory: %s\nStatus: %s\nSubmitted At: %s",
                    request.requestNumber(), request.title(), request.categoryName(),
                    request.status(), request.submittedAt());

            return ToolExecutionResult.success(request, summary);
        } catch (Exception e) {
            return ToolExecutionResult.failure("Could not find request details: " + e.getMessage());
        }
    }
}

