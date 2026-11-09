package com.nexusops.identity.application.dto;

import java.util.UUID;

public record UserOrgSummaryDto(
        UUID organizationId,
        String organizationName,
        String organizationSlug,
        boolean isCurrent
) {
}
