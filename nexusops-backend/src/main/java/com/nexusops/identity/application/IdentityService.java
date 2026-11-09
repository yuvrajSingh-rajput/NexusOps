package com.nexusops.identity.application;

import com.nexusops.identity.domain.*;
import com.nexusops.identity.infrastructure.OrganizationMembershipRepository;
import com.nexusops.identity.infrastructure.RoleRepository;
import com.nexusops.identity.infrastructure.UserRepository;
import com.nexusops.organization.application.DepartmentService;
import com.nexusops.organization.application.dto.DepartmentResponseDto;
import com.nexusops.identity.application.dto.EmployeeResponseDto;
import com.nexusops.identity.application.dto.InviteEmployeeDto;
import com.nexusops.shared.api.PageResponse;
import com.nexusops.shared.exception.ConflictException;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.tenant.TenantContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class IdentityService {

    private final UserRepository userRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final DepartmentService departmentService;
    private final PasswordEncoder passwordEncoder;

    public IdentityService(UserRepository userRepository,
                           OrganizationMembershipRepository membershipRepository,
                           RoleRepository roleRepository,
                           DepartmentService departmentService,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.roleRepository = roleRepository;
        this.departmentService = departmentService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponseDto> getEmployees(Pageable pageable) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        Page<OrganizationMembershipEntity> page = membershipRepository.findAllByOrganizationId(tenantId, pageable);

        List<EmployeeResponseDto> dtoList = page.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return PageResponse.of(dtoList, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    @Transactional(readOnly = true)
    public EmployeeResponseDto getEmployeeByMembershipId(UUID membershipId) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        OrganizationMembershipEntity entity = membershipRepository.findByOrganizationIdAndId(tenantId, membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee Membership", membershipId));
        return toDto(entity);
    }

    @Transactional
    public EmployeeResponseDto inviteEmployee(InviteEmployeeDto dto) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        String email = dto.email().toLowerCase().trim();

        UserEntity user = userRepository.findByEmail(email).orElseGet(() -> {
            // Generate temporary random password for invited user
            String tempPassword = UUID.randomUUID().toString().substring(0, 12);
            UserEntity newUser = new UserEntity(email, passwordEncoder.encode(tempPassword), dto.firstName(), dto.lastName());
            newUser.setStatus(UserStatus.INVITED);
            return userRepository.save(newUser);
        });

        // Check if membership already exists in this tenant
        if (membershipRepository.findByOrganizationIdAndUserId(tenantId, user.getId()).isPresent()) {
            throw new ConflictException("MEMBERSHIP_EXISTS", "User '" + email + "' is already a member of this organization.");
        }

        // Validate department if provided
        if (dto.departmentId() != null) {
            departmentService.getDepartmentById(dto.departmentId());
        }

        // Validate manager if provided
        if (dto.managerMembershipId() != null) {
            membershipRepository.findByOrganizationIdAndId(tenantId, dto.managerMembershipId())
                    .orElseThrow(() -> new ResourceNotFoundException("Manager Membership", dto.managerMembershipId()));
        }

        OrganizationMembershipEntity membership = new OrganizationMembershipEntity(tenantId, user, dto.jobTitle());
        membership.setDepartmentId(dto.departmentId());
        membership.setManagerMembershipId(dto.managerMembershipId());
        membership.setStatus(MembershipStatus.INVITED);

        // Assign Roles
        if (dto.roleCodes() != null && !dto.roleCodes().isEmpty()) {
            for (String roleCode : dto.roleCodes()) {
                roleRepository.findByOrganizationIdAndCode(tenantId, roleCode)
                        .ifPresent(membership::addRole);
            }
        } else {
            // Default to EMPLOYEE role
            roleRepository.findByOrganizationIdAndCode(tenantId, "EMPLOYEE")
                    .ifPresent(membership::addRole);
        }

        OrganizationMembershipEntity saved = membershipRepository.save(membership);
        return toDto(saved);
    }

    private EmployeeResponseDto toDto(OrganizationMembershipEntity entity) {
        Set<String> roleCodes = entity.getRoles().stream()
                .map(RoleEntity::getCode)
                .collect(Collectors.toSet());

        String departmentName = null;
        if (entity.getDepartmentId() != null) {
            try {
                DepartmentResponseDto dept = departmentService.getDepartmentById(entity.getDepartmentId());
                departmentName = dept.name();
            } catch (Exception ignored) {
            }
        }

        return new EmployeeResponseDto(
                entity.getId(),
                entity.getUser().getId(),
                entity.getUser().getEmail(),
                entity.getUser().getFirstName(),
                entity.getUser().getLastName(),
                entity.getUser().getFullName(),
                entity.getJobTitle(),
                entity.getDepartmentId(),
                departmentName,
                entity.getManagerMembershipId(),
                entity.getStatus().name(),
                roleCodes
        );
    }
}
