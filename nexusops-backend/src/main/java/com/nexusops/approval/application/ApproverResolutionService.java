package com.nexusops.approval.application;

import com.nexusops.approval.domain.ApproverType;
import com.nexusops.identity.application.IdentityService;
import com.nexusops.organization.application.DepartmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApproverResolutionService {

    private static final Logger log = LoggerFactory.getLogger(ApproverResolutionService.class);

    private final IdentityService identityService;
    private final DepartmentService departmentService;

    public ApproverResolutionService(IdentityService identityService, DepartmentService departmentService) {
        this.identityService = identityService;
        this.departmentService = departmentService;
    }

    public record ResolvedApprover(
            ApproverType approverType,
            UUID assignedMembershipId,
            UUID assignedRoleId
    ) {}

    @Transactional(readOnly = true)
    public ResolvedApprover resolveApprover(UUID organizationId, UUID requesterMembershipId,
                                            UUID departmentId, Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return new ResolvedApprover(ApproverType.DIRECT_MEMBERSHIP, requesterMembershipId, null);
        }

        String approverTypeStr = String.valueOf(config.getOrDefault("approverType", ""));
        String roleCode = (String) config.get("approverRole");

        // 1. Direct Manager
        if ("MANAGER".equalsIgnoreCase(approverTypeStr)) {
            Optional<UUID> managerId = identityService.getManagerMembershipId(organizationId, requesterMembershipId);
            if (managerId.isPresent()) {
                log.info("Resolved direct manager {} for requester {}", managerId.get(), requesterMembershipId);
                return new ResolvedApprover(ApproverType.MANAGER, managerId.get(), null);
            }
            log.warn("Requester {} has no manager configured, defaulting to self/admin", requesterMembershipId);
            return new ResolvedApprover(ApproverType.MANAGER, requesterMembershipId, null);
        }

        // 2. Department Head
        if ("DEPT_HEAD".equalsIgnoreCase(approverTypeStr)) {
            if (departmentId != null) {
                Optional<UUID> headId = departmentService.getDepartmentHeadMembershipId(organizationId, departmentId);
                if (headId.isPresent()) {
                    log.info("Resolved department head {} for department {}", headId.get(), departmentId);
                    return new ResolvedApprover(ApproverType.DEPT_HEAD, headId.get(), null);
                }
            }
            return new ResolvedApprover(ApproverType.DEPT_HEAD, requesterMembershipId, null);
        }

        // 3. Role-based Queue (e.g., FINANCE)
        if (roleCode != null && !roleCode.isBlank()) {
            Optional<UUID> roleId = identityService.getRoleIdByCode(organizationId, roleCode.toUpperCase());
            if (roleId.isEmpty() && !roleCode.startsWith("ROLE_")) {
                roleId = identityService.getRoleIdByCode(organizationId, "ROLE_" + roleCode.toUpperCase());
            }
            if (roleId.isPresent()) {
                log.info("Resolved role queue {} ({}) for approval", roleCode, roleId.get());
                return new ResolvedApprover(ApproverType.ROLE, null, roleId.get());
            }
            log.warn("Role code {} not found in organization {}, falling back to direct", roleCode, organizationId);
        }

        // 4. Fallback explicit membership
        Object explicitMemberObj = config.get("approverMembershipId");
        if (explicitMemberObj != null) {
            UUID memberId = UUID.fromString(explicitMemberObj.toString());
            return new ResolvedApprover(ApproverType.DIRECT_MEMBERSHIP, memberId, null);
        }

        return new ResolvedApprover(ApproverType.DIRECT_MEMBERSHIP, requesterMembershipId, null);
    }
}

