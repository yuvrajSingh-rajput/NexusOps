package com.nexusops.knowledge.infrastructure;

import com.nexusops.knowledge.domain.DocumentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    Page<DocumentEntity> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<DocumentEntity> findByOrganizationIdAndCategory(UUID organizationId, String category, Pageable pageable);

    Optional<DocumentEntity> findByOrganizationIdAndId(UUID organizationId, UUID id);

    void deleteByOrganizationIdAndId(UUID organizationId, UUID id);
}

