package com.nexusops.identity.application.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SwitchOrgRequestDto(
        @NotNull(message = "Target organization ID is required")
        UUID targetOrganizationId
) {
}

