package com.nexusops.organization.infrastructure;

import com.nexusops.organization.domain.TeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<TeamEntity, UUID> {
    List<TeamEntity> findAllByOrganizationId(UUID organizationId);
    Optional<TeamEntity> findByOrganizationIdAndCode(UUID organizationId, String code);
    Optional<TeamEntity> findByOrganizationIdAndId(UUID organizationId, UUID id);
    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
}

