package com.nexusops.request.infrastructure;

import com.nexusops.request.domain.RequestCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequestCategoryRepository extends JpaRepository<RequestCategoryEntity, UUID> {

    Optional<RequestCategoryEntity> findByOrganizationIdAndId(UUID organizationId, UUID id);

    Optional<RequestCategoryEntity> findByOrganizationIdAndCode(UUID organizationId, String code);

    List<RequestCategoryEntity> findAllByOrganizationIdAndActiveTrue(UUID organizationId);

    List<RequestCategoryEntity> findAllByOrganizationId(UUID organizationId);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
}
