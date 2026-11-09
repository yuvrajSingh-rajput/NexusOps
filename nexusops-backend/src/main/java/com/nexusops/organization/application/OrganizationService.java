package com.nexusops.organization.application;

import com.nexusops.organization.application.dto.OrganizationResponseDto;
import com.nexusops.organization.domain.OrganizationEntity;
import com.nexusops.organization.infrastructure.OrganizationRepository;
import com.nexusops.shared.exception.ConflictException;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public OrganizationResponseDto createOrganization(String name, String slug) {
        String normalizedSlug = slug.toLowerCase().trim();
        if (organizationRepository.existsBySlug(normalizedSlug)) {
            throw new ConflictException("ORGANIZATION_SLUG_EXISTS", "Organization slug '" + normalizedSlug + "' is already registered.");
        }

        OrganizationEntity entity = new OrganizationEntity(name, normalizedSlug);
        OrganizationEntity saved = organizationRepository.save(entity);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public OrganizationResponseDto getOrganizationById(UUID id) {
        OrganizationEntity entity = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id));
        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public OrganizationResponseDto getCurrentOrganization() {
        UUID currentTenantId = TenantContextHolder.getRequiredTenantId();
        return getOrganizationById(currentTenantId);
    }

    private OrganizationResponseDto toDto(OrganizationEntity entity) {
        return new OrganizationResponseDto(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getStatus().name(),
                entity.getCreatedAt()
        );
    }
}

