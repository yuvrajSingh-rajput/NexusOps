package com.nexusops.request.api;

import com.nexusops.request.application.RequestService;
import com.nexusops.request.application.dto.*;
import com.nexusops.shared.api.ApiResponse;
import com.nexusops.shared.api.PageResponse;
import com.nexusops.shared.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests")
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('REQUEST_CREATE')")
    public ResponseEntity<ApiResponse<RequestResponseDto>> createRequest(
            @Valid @RequestBody CreateRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        RequestResponseDto created = requestService.createRequest(dto, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAuthority('REQUEST_VIEW_OWN')")
    public ResponseEntity<ApiResponse<PageResponse<RequestResponseDto>>> getMyRequests(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        PageResponse<RequestResponseDto> response = requestService.getMyRequests(pageable, principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('REQUEST_VIEW_ALL', 'REQUEST_VIEW_DEPT', 'ORG_ADMIN_MANAGE')")
    public ResponseEntity<ApiResponse<PageResponse<RequestResponseDto>>> getAllRequests(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        PageResponse<RequestResponseDto> response = requestService.getAllRequests(pageable, principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RequestResponseDto>> getRequestById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        RequestResponseDto request = requestService.getRequestById(id, principal);
        return ResponseEntity.ok(ApiResponse.success(request));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('REQUEST_CREATE')")
    public ResponseEntity<ApiResponse<RequestResponseDto>> submitRequest(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        RequestResponseDto updated = requestService.submitRequest(id, principal);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<RequestResponseDto>> cancelRequest(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserPrincipal principal) {
        String reason = body != null ? body.get("reason") : null;
        RequestResponseDto updated = requestService.cancelRequest(id, reason, principal);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<List<RequestCommentResponseDto>>> getComments(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<RequestCommentResponseDto> comments = requestService.getComments(id, principal);
        return ResponseEntity.ok(ApiResponse.success(comments));
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('REQUEST_COMMENT')")
    public ResponseEntity<ApiResponse<RequestCommentResponseDto>> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody AddCommentDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        RequestCommentResponseDto created = requestService.addComment(id, dto, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }
}

