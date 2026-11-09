package com.nexusops.identity.application.dto;

import java.util.Set;
import java.util.UUID;

public record AuthResponseDto(
        String accessToken,
        String refreshToken,
        UUID userId,
        UUID organizationId,
        String email,
        String fullName,
        Set<String> roles,
        Set<String> permissions
) {
}

