package com.nexusops.organization.application;

import com.nexusops.organization.application.dto.CreateDepartmentDto;
import com.nexusops.organization.application.dto.DepartmentResponseDto;
import com.nexusops.organization.domain.DepartmentEntity;
import com.nexusops.organization.infrastructure.DepartmentRepository;
import com.nexusops.shared.exception.ConflictException;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponseDto> getDepartments() {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        return departmentRepository.findAllByOrganizationId(tenantId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DepartmentResponseDto getDepartmentById(UUID id) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        DepartmentEntity entity = departmentRepository.findByOrganizationIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));
        return toDto(entity);
    }

    @Transactional
    public DepartmentResponseDto createDepartment(CreateDepartmentDto dto) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        String code = dto.code().toUpperCase().trim();

        if (departmentRepository.existsByOrganizationIdAndCode(tenantId, code)) {
            throw new ConflictException("DEPARTMENT_CODE_EXISTS",
                    "Department code '" + code + "' already exists in this organization.");
        }

        DepartmentEntity parent = null;
        if (dto.parentDepartmentId() != null) {
            parent = departmentRepository.findByOrganizationIdAndId(tenantId, dto.parentDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent Department", dto.parentDepartmentId()));
        }

        DepartmentEntity entity = new DepartmentEntity(tenantId, code, dto.name(), parent);
        entity.setHeadMembershipId(dto.headMembershipId());

        DepartmentEntity saved = departmentRepository.save(entity);
        return toDto(saved);
    }

    private DepartmentResponseDto toDto(DepartmentEntity entity) {
        UUID parentId = entity.getParentDepartment() != null ? entity.getParentDepartment().getId() : null;
        String parentName = entity.getParentDepartment() != null ? entity.getParentDepartment().getName() : null;

        return new DepartmentResponseDto(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getCode(),
                entity.getName(),
                parentId,
                parentName,
                entity.getHeadMembershipId()
        );
    }
}

