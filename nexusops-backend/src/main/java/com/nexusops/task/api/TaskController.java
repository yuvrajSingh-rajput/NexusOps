package com.nexusops.task.api;

import com.nexusops.shared.api.ApiResponse;
import com.nexusops.shared.security.UserPrincipal;
import com.nexusops.task.application.TaskService;
import com.nexusops.task.application.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<TaskResponseDto>>> getMyTasks(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<TaskResponseDto> tasks = taskService.getMyTasks(principal.getOrganizationId(), principal);
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<List<TaskResponseDto>>> getQueueTasks(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<TaskResponseDto> queue = taskService.getQueueTasks(principal.getOrganizationId());
        return ResponseEntity.ok(ApiResponse.success(queue));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponseDto>> getTaskById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        TaskResponseDto task = taskService.getTaskById(principal.getOrganizationId(), id);
        return ResponseEntity.ok(ApiResponse.success(task));
    }

    @GetMapping("/request/{requestId}")
    public ResponseEntity<ApiResponse<List<TaskResponseDto>>> getTasksForRequest(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<TaskResponseDto> tasks = taskService.getTasksForRequest(principal.getOrganizationId(), requestId);
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<ApiResponse<TaskResponseDto>> claimTask(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        TaskResponseDto task = taskService.claimTask(principal.getOrganizationId(), id, principal);
        return ResponseEntity.ok(ApiResponse.success(task));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<TaskResponseDto>> assignTask(
            @PathVariable UUID id,
            @RequestParam UUID assigneeMembershipId,
            @AuthenticationPrincipal UserPrincipal principal) {
        TaskResponseDto task = taskService.assignTask(principal.getOrganizationId(), id, assigneeMembershipId, principal);
        return ResponseEntity.ok(ApiResponse.success(task));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TaskResponseDto>> updateTaskStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskStatusDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        TaskResponseDto task = taskService.updateTaskStatus(principal.getOrganizationId(), id, dto, principal);
        return ResponseEntity.ok(ApiResponse.success(task));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<TaskResponseDto>> completeTask(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteTaskDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        TaskResponseDto task = taskService.completeTask(principal.getOrganizationId(), id, dto, principal);
        return ResponseEntity.ok(ApiResponse.success(task));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<TaskCommentResponseDto>> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody AddTaskCommentDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        TaskCommentResponseDto comment = taskService.addComment(principal.getOrganizationId(), id, dto, principal);
        return ResponseEntity.ok(ApiResponse.success(comment));
    }
}

