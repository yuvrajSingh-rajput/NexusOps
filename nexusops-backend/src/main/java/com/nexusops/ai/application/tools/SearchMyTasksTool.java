package com.nexusops.ai.application.tools;

import com.nexusops.task.application.TaskService;
import com.nexusops.task.application.dto.TaskResponseDto;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SearchMyTasksTool implements NexusAiTool {

    private final TaskService taskService;

    public SearchMyTasksTool(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public String getName() {
        return "searchMyTasks";
    }

    @Override
    public String getDescription() {
        return "Retrieve fulfillment tasks assigned to the authenticated operator.";
    }

    @Override
    public String getRequiredPermission() {
        return "TASK_OPERATE";
    }

    @Override
    public boolean isWriteAction() {
        return false;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ToolExecutionContext context) {
        try {
            List<TaskResponseDto> list = taskService.getMyTasks(
                    context.organizationId(),
                    context.principal()
            );

            if (list.isEmpty()) {
                return ToolExecutionResult.success(List.of(), "You have no tasks assigned to you.");
            }

            String summary = list.stream()
                    .map(t -> String.format("- %s: %s (Status: %s, Priority: %s)",
                            t.taskNumber(), t.title(), t.status(), t.priority()))
                    .collect(Collectors.joining("\n"));

            return ToolExecutionResult.success(list, "Here are your assigned fulfillment tasks:\n" + summary);
        } catch (Exception e) {
            return ToolExecutionResult.failure("Failed to retrieve assigned tasks: " + e.getMessage());
        }
    }
}
