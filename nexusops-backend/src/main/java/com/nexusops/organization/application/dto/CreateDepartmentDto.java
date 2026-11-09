package com.nexusops.organization.application.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateDepartmentDto(
        @NotBlank(message = "Department code is required")
        String code,

        @NotBlank(message = "Department name is required")
        String name,

        UUID parentDepartmentId,
        UUID headMembershipId
) {
}
