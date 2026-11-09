package com.nexusops.ai.application.tools;

import com.nexusops.shared.security.UserPrincipal;

import java.util.UUID;

public record ToolExecutionContext(
        UUID organizationId,
        UUID userId,
        UUID membershipId,
        UserPrincipal principal
) {
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        if (principal == null) {
            return false;
        }
        return principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(permission)
                        || a.getAuthority().equals("ROLE_ORG_ADMIN")
                        || a.getAuthority().equals("ORG_ADMIN_MANAGE"));
    }
}
