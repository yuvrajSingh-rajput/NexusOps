package com.nexusops.identity.application.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UserMeResponseDto(
        UUID userId,
        UUID organizationId,
        String email,
        String firstName,
        String lastName,
        String jobTitle,
        Set<String> roles,
        Set<String> permissions,
        List<UserOrgSummaryDto> availableOrganizations
) {
}

