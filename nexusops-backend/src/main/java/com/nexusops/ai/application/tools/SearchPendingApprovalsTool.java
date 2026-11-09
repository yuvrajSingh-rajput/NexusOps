package com.nexusops.ai.application.tools;

import com.nexusops.approval.application.ApprovalService;
import com.nexusops.approval.application.dto.ApprovalInstanceResponseDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SearchPendingApprovalsTool implements NexusAiTool {

    private final ApprovalService approvalService;

    public SearchPendingApprovalsTool(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Override
    public String getName() {
        return "searchPendingApprovals";
    }

    @Override
    public String getDescription() {
        return "Search all pending approval requests currently awaiting your decision or your role queues.";
    }

    @Override
    public String getRequiredPermission() {
        return "APPROVAL_EXECUTE";
    }

    @Override
    public boolean isWriteAction() {
        return false;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ToolExecutionContext context) {
        try {
            List<ApprovalInstanceResponseDto> list = approvalService.getMyPendingApprovals(
                    context.organizationId(),
                    context.principal()
            );

            if (list.isEmpty()) {
                return ToolExecutionResult.success(List.of(), "You have no pending approval items in your inbox.");
            }

            String summary = list.stream()
                    .map(a -> String.format("- Request %s: \"%s\" (Requester: %s, Queue: %s)",
                            a.requestNumber(), a.requestTitle(), a.requesterName(), a.approverRoleName()))
                    .collect(Collectors.joining("\n"));

            return ToolExecutionResult.success(list,
                    String.format("You have %d pending approvals:\n%s", list.size(), summary));
        } catch (Exception e) {
            return ToolExecutionResult.failure("Failed to retrieve pending approvals: " + e.getMessage());
        }
    }
}
