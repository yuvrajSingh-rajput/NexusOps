package com.nexusops.ai.infrastructure;

import com.nexusops.ai.domain.AiMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiMessageRepository extends JpaRepository<AiMessageEntity, UUID> {

    List<AiMessageEntity> findByOrganizationIdAndConversationIdOrderByCreatedAtAsc(
            UUID organizationId, UUID conversationId);
}

