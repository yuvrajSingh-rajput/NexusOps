package com.nexusops.identity.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterOrgRequestDto(
        @NotBlank(message = "Organization name is required")
        String organizationName,

        @NotBlank(message = "Organization slug is required")
        String organizationSlug,

        @NotBlank(message = "Admin email is required")
        @Email(message = "Admin email must be valid")
        String adminEmail,

        @NotBlank(message = "Admin password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String adminPassword,

        @NotBlank(message = "Admin first name is required")
        String adminFirstName,

        @NotBlank(message = "Admin last name is required")
        String adminLastName
) {
}
