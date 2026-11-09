package com.nexusops.organization.api;

import com.nexusops.organization.application.OrganizationService;
import com.nexusops.organization.application.dto.OrganizationResponseDto;
import com.nexusops.shared.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<OrganizationResponseDto>> getCurrentOrganization() {
        OrganizationResponseDto response = organizationService.getCurrentOrganization();
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
