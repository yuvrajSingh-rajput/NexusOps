package com.nexusops.ai.infrastructure;

import com.nexusops.ai.domain.AiToolExecutionEntity;
import com.nexusops.ai.domain.ToolExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiToolExecutionRepository extends JpaRepository<AiToolExecutionEntity, UUID> {

    Optional<AiToolExecutionEntity> findByOrganizationIdAndId(UUID organizationId, UUID id);

    List<AiToolExecutionEntity> findByOrganizationIdAndConversationIdOrderByCreatedAtDesc(
            UUID organizationId, UUID conversationId);

    List<AiToolExecutionEntity> findByOrganizationIdAndUserIdAndStatus(
            UUID organizationId, UUID userId, ToolExecutionStatus status);
}

