package com.nexusops.ai.application.tools;

import com.nexusops.request.application.RequestService;
import com.nexusops.request.application.dto.RequestResponseDto;
import com.nexusops.shared.api.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SearchMyRequestsTool implements NexusAiTool {

    private final RequestService requestService;

    public SearchMyRequestsTool(RequestService requestService) {
        this.requestService = requestService;
    }

    @Override
    public String getName() {
        return "searchMyRequests";
    }

    @Override
    public String getDescription() {
        return "Retrieve the authenticated user's submitted requests, including request numbers, titles, and status.";
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
        try {
            PageResponse<RequestResponseDto> page = requestService.getMyRequests(
                    PageRequest.of(0, 5),
                    context.principal()
            );

            if (page.content().isEmpty()) {
                if (context.hasPermission("REQUEST_VIEW_ALL")) {
                    PageResponse<RequestResponseDto> allPage = requestService.getAllRequests(
                            PageRequest.of(0, 5),
                            context.principal()
                    );
                    if (!allPage.content().isEmpty()) {
                        String allSummary = allPage.content().stream()
                                .map(r -> String.format("- %s: %s (Status: %s, Priority: %s, Requester: %s)",
                                        r.requestNumber(), r.title(), r.status(), r.priority(), r.requesterEmail()))
                                .collect(Collectors.joining("\n"));
                        return ToolExecutionResult.success(allPage.content(),
                                "You have not submitted any personal requests yet, but as an administrator, here are recent organization requests:\n" + allSummary);
                    }
                }
                return ToolExecutionResult.success(page.content(), "You have no active or submitted requests.");
            }

            String summary = page.content().stream()
                    .map(r -> String.format("- %s: %s (Status: %s, Priority: %s)",
                            r.requestNumber(), r.title(), r.status(), r.priority()))
                    .collect(Collectors.joining("\n"));

            return ToolExecutionResult.success(page.content(), "Here are your recent requests:\n" + summary);
        } catch (Exception e) {
            return ToolExecutionResult.failure("Failed to retrieve your requests: " + e.getMessage());
        }
    }
}
