package com.nexusops.request.api;

import com.nexusops.request.application.RequestCategoryService;
import com.nexusops.request.application.dto.CreateRequestCategoryDto;
import com.nexusops.request.application.dto.RequestCategoryResponseDto;
import com.nexusops.shared.api.ApiResponse;
import com.nexusops.shared.tenant.TenantContextHolder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/request-categories")
public class RequestCategoryController {

    private final RequestCategoryService categoryService;

    public RequestCategoryController(RequestCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RequestCategoryResponseDto>>> getCategories() {
        UUID orgId = TenantContextHolder.requireTenantId();
        List<RequestCategoryResponseDto> categories = categoryService.getActiveCategories();
        if (categories.isEmpty()) {
            categoryService.seedDefaultCategoriesForTenant(orgId);
            categories = categoryService.getActiveCategories();
        }
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RequestCategoryResponseDto>> getCategoryById(@PathVariable UUID id) {
        RequestCategoryResponseDto category = categoryService.getCategoryById(id);
        return ResponseEntity.ok(ApiResponse.success(category));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORG_ADMIN_MANAGE')")
    public ResponseEntity<ApiResponse<RequestCategoryResponseDto>> createCategory(
            @Valid @RequestBody CreateRequestCategoryDto dto) {
        RequestCategoryResponseDto created = categoryService.createCategory(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }
}
