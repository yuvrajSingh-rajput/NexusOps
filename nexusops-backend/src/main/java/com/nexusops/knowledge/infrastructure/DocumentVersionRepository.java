package com.nexusops.knowledge.infrastructure;

import com.nexusops.knowledge.domain.DocumentVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersionEntity, UUID> {

    List<DocumentVersionEntity> findByOrganizationIdAndDocumentId(UUID organizationId, UUID documentId);

    Optional<DocumentVersionEntity> findByOrganizationIdAndDocumentIdAndVersionNumber(
            UUID organizationId, UUID documentId, int versionNumber);

    Optional<DocumentVersionEntity> findTopByOrganizationIdAndDocumentIdOrderByVersionNumberDesc(
            UUID organizationId, UUID documentId);
}

