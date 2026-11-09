package com.nexusops.identity.application.dto;

import java.util.Set;
import java.util.UUID;

public record EmployeeResponseDto(
        UUID membershipId,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String jobTitle,
        UUID departmentId,
        String departmentName,
        UUID managerMembershipId,
        String status,
        Set<String> roles
) {
}

