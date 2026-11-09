package com.nexusops.organization.application.dto;

import java.util.UUID;

public record DepartmentResponseDto(
        UUID id,
        UUID organizationId,
        String code,
        String name,
        UUID parentDepartmentId,
        String parentDepartmentName,
        UUID headMembershipId
) {
}
