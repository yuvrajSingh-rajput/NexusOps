package com.nexusops.audit.api;

import com.nexusops.audit.application.AuditLogService;
import com.nexusops.audit.application.dto.AuditLogResponseDto;
import com.nexusops.shared.api.ApiResponse;
import com.nexusops.shared.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<Page<AuditLogResponseDto>>> getAuditLogs(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "aggregateType", required = false) String aggregateType,
            @RequestParam(value = "aggregateId", required = false) UUID aggregateId,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<AuditLogResponseDto> page = auditLogService.getAuditLogs(principal, aggregateType, aggregateId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }
}

