package com.nexusops.ai.infrastructure;

import com.nexusops.ai.domain.AiConversationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiConversationRepository extends JpaRepository<AiConversationEntity, UUID> {

    Page<AiConversationEntity> findByOrganizationIdAndUserIdOrderByUpdatedAtDesc(
            UUID organizationId, UUID userId, Pageable pageable);

    Optional<AiConversationEntity> findByOrganizationIdAndId(UUID organizationId, UUID id);

    void deleteByOrganizationIdAndId(UUID organizationId, UUID id);
}

