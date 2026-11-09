package com.nexusops.identity.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import java.util.UUID;

public record InviteEmployeeDto(
        @NotBlank(message = "Email is required")
        @Email(message = "Valid email is required")
        String email,

        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        String jobTitle,
        UUID departmentId,
        UUID managerMembershipId,
        Set<String> roleCodes
) {
}

