package com.nexusops.identity.api;

import com.nexusops.identity.application.IdentityService;
import com.nexusops.identity.application.dto.EmployeeResponseDto;
import com.nexusops.identity.application.dto.InviteEmployeeDto;
import com.nexusops.shared.api.ApiResponse;
import com.nexusops.shared.api.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memberships")
public class MembershipController {

    private final IdentityService identityService;

    public MembershipController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<EmployeeResponseDto>>> getEmployees(
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<EmployeeResponseDto> response = identityService.getEmployees(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> getEmployeeById(@PathVariable UUID id) {
        EmployeeResponseDto response = identityService.getEmployeeByMembershipId(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/invite")
    @PreAuthorize("hasAuthority('ORG_USER_INVITE')")
    public ResponseEntity<ApiResponse<EmployeeResponseDto>> inviteEmployee(
            @Valid @RequestBody InviteEmployeeDto dto) {
        EmployeeResponseDto response = identityService.inviteEmployee(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
