package com.nexusops.identity.application;

import com.nexusops.identity.application.dto.*;
import com.nexusops.identity.domain.*;
import com.nexusops.identity.infrastructure.*;
import com.nexusops.organization.application.OrganizationService;
import com.nexusops.organization.application.dto.OrganizationResponseDto;
import com.nexusops.shared.security.JwtTokenProvider;
import com.nexusops.shared.exception.AccessDeniedException;
import com.nexusops.shared.exception.ConflictException;
import com.nexusops.shared.exception.ResourceNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OrganizationService organizationService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       OrganizationMembershipRepository membershipRepository,
                       RoleRepository roleRepository,
                       PermissionRepository permissionRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       OrganizationService organizationService,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.organizationService = organizationService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponseDto registerOrganization(RegisterOrgRequestDto dto) {
        String email = dto.adminEmail().toLowerCase().trim();

        // 1. Create Organization via Organization Service
        OrganizationResponseDto org = organizationService.createOrganization(dto.organizationName(), dto.organizationSlug());
        UUID orgId = org.id();

        // 2. Find or Create User
        UserEntity user = userRepository.findByEmail(email).orElseGet(() -> {
            String hash = passwordEncoder.encode(dto.adminPassword());
            UserEntity newUser = new UserEntity(email, hash, dto.adminFirstName(), dto.adminLastName());
            return userRepository.save(newUser);
        });

        // 3. Seed Default System Roles for the Organization
        Map<String, RoleEntity> seededRoles = seedDefaultRolesForTenant(orgId);

        // 4. Create Organization Membership for Admin with ORG_ADMIN Role
        OrganizationMembershipEntity membership = new OrganizationMembershipEntity(orgId, user, "Organization Administrator");
        membership.addRole(seededRoles.get("ORG_ADMIN"));
        membershipRepository.save(membership);

        // 5. Generate Tokens
        return createAuthResponse(user, membership, orgId);
    }

    @Transactional
    public AuthResponseDto login(LoginRequestDto dto) {
        String email = dto.email().toLowerCase().trim();
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(dto.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        List<OrganizationMembershipEntity> memberships = membershipRepository.findAllByUserId(user.getId());
        if (memberships.isEmpty()) {
            throw new AccessDeniedException("User does not have an active membership in any organization.");
        }

        // Pick primary / first active membership
        OrganizationMembershipEntity activeMembership = memberships.stream()
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Your organization account is inactive or suspended."));

        return createAuthResponse(user, activeMembership, activeMembership.getOrganizationId());
    }

    @Transactional
    public AuthResponseDto refreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BadCredentialsException("Refresh token is missing");
        }

        String hash = jwtTokenProvider.hashToken(rawRefreshToken);
        RefreshTokenEntity tokenEntity = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (tokenEntity.isRevoked() || tokenEntity.isExpired()) {
            // Potential token reuse attack - invalidate all user tokens in tenant
            refreshTokenRepository.revokeAllForUserAndTenant(tokenEntity.getUserId(), tokenEntity.getOrganizationId());
            throw new BadCredentialsException("Refresh token is expired or has been revoked.");
        }

        // Token rotation: Revoke old token
        tokenEntity.setRevoked(true);
        refreshTokenRepository.save(tokenEntity);

        // Load user and membership
        UserEntity user = userRepository.findById(tokenEntity.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", tokenEntity.getUserId()));
        OrganizationMembershipEntity membership = membershipRepository
                .findByOrganizationIdAndUserId(tokenEntity.getOrganizationId(), user.getId())
                .orElseThrow(() -> new AccessDeniedException("User is not a member of this organization"));

        return createAuthResponse(user, membership, tokenEntity.getOrganizationId());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String hash = jwtTokenProvider.hashToken(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
        }
    }

    @Transactional(readOnly = true)
    public UserMeResponseDto getMe(UUID userId, UUID currentOrgId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        OrganizationMembershipEntity membership = membershipRepository
                .findByOrganizationIdAndUserId(currentOrgId, userId)
                .orElseThrow(() -> new AccessDeniedException("User has no membership in organization: " + currentOrgId));

        Set<String> roles = membership.getRoles().stream()
                .map(RoleEntity::getCode)
                .collect(Collectors.toSet());

        Set<String> permissions = membership.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(PermissionEntity::getCode)
                .collect(Collectors.toSet());

        List<OrganizationMembershipEntity> allMemberships = membershipRepository.findAllByUserId(userId);
        List<UserOrgSummaryDto> orgSummaries = allMemberships.stream().map(m -> {
            OrganizationResponseDto org = organizationService.getOrganizationById(m.getOrganizationId());
            return new UserOrgSummaryDto(
                    org.id(),
                    org.name(),
                    org.slug(),
                    org.id().equals(currentOrgId)
            );
        }).collect(Collectors.toList());

        return new UserMeResponseDto(
                user.getId(),
                currentOrgId,
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                membership.getJobTitle(),
                roles,
                permissions,
                orgSummaries
        );
    }

    @Transactional
    public AuthResponseDto switchOrganization(UUID userId, UUID targetOrgId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        OrganizationMembershipEntity targetMembership = membershipRepository
                .findByOrganizationIdAndUserId(targetOrgId, userId)
                .orElseThrow(() -> new AccessDeniedException("You are not a member of the requested organization."));

        if (targetMembership.getStatus() != MembershipStatus.ACTIVE) {
            throw new AccessDeniedException("Your membership in this organization is inactive.");
        }

        return createAuthResponse(user, targetMembership, targetOrgId);
    }

    private AuthResponseDto createAuthResponse(UserEntity user, OrganizationMembershipEntity membership, UUID organizationId) {
        Set<String> roleCodes = membership.getRoles().stream()
                .map(RoleEntity::getCode)
                .collect(Collectors.toSet());

        Set<String> permissionCodes = membership.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(PermissionEntity::getCode)
                .collect(Collectors.toSet());

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                organizationId,
                membership.getId(),
                user.getEmail(),
                roleCodes,
                permissionCodes
        );

        String rawRefreshToken = jwtTokenProvider.generateRawRefreshToken();
        String tokenHash = jwtTokenProvider.hashToken(rawRefreshToken);
        Instant expiry = Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenExpirationMs());

        RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity(user.getId(), organizationId, tokenHash, expiry);
        refreshTokenRepository.save(refreshTokenEntity);

        return new AuthResponseDto(
                accessToken,
                rawRefreshToken,
                user.getId(),
                organizationId,
                user.getEmail(),
                user.getFullName(),
                roleCodes,
                permissionCodes
        );
    }

    private void seedGlobalPermissionsIfEmpty() {
        if (permissionRepository.count() > 0) {
            return;
        }
        List<PermissionEntity> perms = List.of(
                new PermissionEntity("REQUEST_CREATE", "REQUEST", "Create new operational requests"),
                new PermissionEntity("REQUEST_VIEW_OWN", "REQUEST", "View requests created by oneself"),
                new PermissionEntity("REQUEST_VIEW_DEPT", "REQUEST", "View requests within own department"),
                new PermissionEntity("REQUEST_VIEW_ALL", "REQUEST", "View all requests within organization"),
                new PermissionEntity("REQUEST_COMMENT", "REQUEST", "Post comments on requests"),
                new PermissionEntity("REQUEST_VIEW_INTERNAL_COMMENTS", "REQUEST", "View internal operator-only comments"),
                new PermissionEntity("REQUEST_ATTACH", "REQUEST", "Upload and attach documents to requests"),
                new PermissionEntity("REQUEST_CANCEL", "REQUEST", "Cancel in-flight operational requests"),
                new PermissionEntity("APPROVAL_VIEW_PENDING", "APPROVAL", "View pending approvals assigned to self or role"),
                new PermissionEntity("APPROVAL_EXECUTE", "APPROVAL", "Approve, reject, or request information on approvals"),
                new PermissionEntity("APPROVAL_DELEGATE", "APPROVAL", "Delegate approval authority to another member"),
                new PermissionEntity("TASK_VIEW_ASSIGNED", "TASK", "View tasks assigned to oneself"),
                new PermissionEntity("TASK_QUEUE_VIEW", "TASK", "View unassigned department task queues"),
                new PermissionEntity("TASK_CLAIM", "TASK", "Claim an unassigned fulfillment task"),
                new PermissionEntity("TASK_UPDATE", "TASK", "Update task progress and operational details"),
                new PermissionEntity("TASK_COMPLETE", "TASK", "Mark fulfillment tasks as completed"),
                new PermissionEntity("WORKFLOW_VIEW", "WORKFLOW", "View workflow definitions and versions"),
                new PermissionEntity("WORKFLOW_MANAGE", "WORKFLOW", "Create, edit, validate, and publish workflow graphs"),
                new PermissionEntity("KNOWLEDGE_VIEW", "KNOWLEDGE", "Search and read knowledge base documents"),
                new PermissionEntity("KNOWLEDGE_MANAGE", "KNOWLEDGE", "Upload, delete, and re-index policy documents"),
                new PermissionEntity("AI_CHAT_ACCESS", "AI", "Access Nexus AI chat assistant"),
                new PermissionEntity("ANALYTICS_VIEW", "ANALYTICS", "Access KPI and operational bottleneck analytics"),
                new PermissionEntity("AUDIT_VIEW", "AUDIT", "View system-wide append-only audit trail"),
                new PermissionEntity("ORG_ADMIN_MANAGE", "ORGANIZATION", "Full organization administration and settings"),
                new PermissionEntity("ORG_DEPT_MANAGE", "ORGANIZATION", "Manage organization departments and teams"),
                new PermissionEntity("ORG_USER_INVITE", "ORGANIZATION", "Invite new employees and manage memberships")
        );
        permissionRepository.saveAll(perms);
    }

    private Map<String, RoleEntity> seedDefaultRolesForTenant(UUID orgId) {
        seedGlobalPermissionsIfEmpty();
        List<PermissionEntity> allPermissions = permissionRepository.findAll();
        Map<String, PermissionEntity> permMap = allPermissions.stream()
                .collect(Collectors.toMap(PermissionEntity::getCode, p -> p));

        Map<String, RoleEntity> roles = new HashMap<>();

        // ORG_ADMIN
        RoleEntity adminRole = new RoleEntity(orgId, "ORG_ADMIN", "Organization Administrator",
                "Full administration access to the organization", true);
        adminRole.setPermissions(new HashSet<>(allPermissions));
        roles.put("ORG_ADMIN", roleRepository.save(adminRole));

        // MANAGER
        RoleEntity managerRole = new RoleEntity(orgId, "MANAGER", "Manager",
                "Department manager with approval authorities", true);
        Set<String> mgrPerms = Set.of(
                "REQUEST_CREATE", "REQUEST_VIEW_OWN", "REQUEST_VIEW_DEPT", "REQUEST_COMMENT",
                "REQUEST_ATTACH", "REQUEST_CANCEL", "APPROVAL_VIEW_PENDING", "APPROVAL_EXECUTE",
                "APPROVAL_DELEGATE", "TASK_VIEW_ASSIGNED", "TASK_QUEUE_VIEW",
                "KNOWLEDGE_VIEW", "AI_CHAT_ACCESS", "ANALYTICS_VIEW"
        );
        mgrPerms.forEach(p -> { if (permMap.containsKey(p)) managerRole.addPermission(permMap.get(p)); });
        roles.put("MANAGER", roleRepository.save(managerRole));

        // EMPLOYEE
        RoleEntity empRole = new RoleEntity(orgId, "EMPLOYEE", "Employee",
                "Standard employee access for submitting requests", true);
        Set<String> empPerms = Set.of(
                "REQUEST_CREATE", "REQUEST_VIEW_OWN", "REQUEST_COMMENT", "REQUEST_ATTACH",
                "REQUEST_CANCEL", "KNOWLEDGE_VIEW", "AI_CHAT_ACCESS"
        );
        empPerms.forEach(p -> { if (permMap.containsKey(p)) empRole.addPermission(permMap.get(p)); });
        roles.put("EMPLOYEE", roleRepository.save(empRole));

        // OPERATOR
        RoleEntity opRole = new RoleEntity(orgId, "OPERATOR", "Fulfillment Operator",
                "IT and Procurement operator fulfilling tasks", true);
        Set<String> opPerms = Set.of(
                "REQUEST_CREATE", "REQUEST_VIEW_OWN", "REQUEST_COMMENT", "REQUEST_ATTACH",
                "REQUEST_CANCEL", "REQUEST_VIEW_ALL", "REQUEST_VIEW_INTERNAL_COMMENTS",
                "TASK_VIEW_ASSIGNED", "TASK_QUEUE_VIEW", "TASK_CLAIM", "TASK_UPDATE",
                "TASK_COMPLETE", "KNOWLEDGE_VIEW", "AI_CHAT_ACCESS", "ANALYTICS_VIEW"
        );
        opPerms.forEach(p -> { if (permMap.containsKey(p)) opRole.addPermission(permMap.get(p)); });
        roles.put("OPERATOR", roleRepository.save(opRole));

        // FINANCE
        RoleEntity finRole = new RoleEntity(orgId, "FINANCE", "Finance Officer",
                "Finance department approver for budget reviews", true);
        Set<String> finPerms = Set.of(
                "REQUEST_CREATE", "REQUEST_VIEW_OWN", "REQUEST_COMMENT", "REQUEST_ATTACH",
                "REQUEST_CANCEL", "REQUEST_VIEW_ALL", "REQUEST_VIEW_INTERNAL_COMMENTS",
                "APPROVAL_VIEW_PENDING", "APPROVAL_EXECUTE", "APPROVAL_DELEGATE",
                "KNOWLEDGE_VIEW", "ANALYTICS_VIEW", "AI_CHAT_ACCESS"
        );
        finPerms.forEach(p -> { if (permMap.containsKey(p)) finRole.addPermission(permMap.get(p)); });
        roles.put("FINANCE", roleRepository.save(finRole));

        return roles;
    }
}
