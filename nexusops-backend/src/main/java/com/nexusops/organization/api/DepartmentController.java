package com.nexusops.organization.api;

import com.nexusops.organization.application.DepartmentService;
import com.nexusops.organization.application.dto.CreateDepartmentDto;
import com.nexusops.organization.application.dto.DepartmentResponseDto;
import com.nexusops.shared.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DepartmentResponseDto>>> getDepartments() {
        List<DepartmentResponseDto> departments = departmentService.getDepartments();
        return ResponseEntity.ok(ApiResponse.success(departments));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentResponseDto>> getDepartmentById(@PathVariable UUID id) {
        DepartmentResponseDto department = departmentService.getDepartmentById(id);
        return ResponseEntity.ok(ApiResponse.success(department));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORG_DEPT_MANAGE')")
    public ResponseEntity<ApiResponse<DepartmentResponseDto>> createDepartment(
            @Valid @RequestBody CreateDepartmentDto dto) {
        DepartmentResponseDto created = departmentService.createDepartment(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }
}
