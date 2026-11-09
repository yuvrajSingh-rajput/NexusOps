package com.nexusops.request.infrastructure;

import com.nexusops.request.domain.CommentVisibility;
import com.nexusops.request.domain.RequestCommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RequestCommentRepository extends JpaRepository<RequestCommentEntity, UUID> {

    List<RequestCommentEntity> findAllByOrganizationIdAndRequestIdOrderByCreatedAtAsc(UUID organizationId, UUID requestId);

    List<RequestCommentEntity> findAllByOrganizationIdAndRequestIdAndVisibilityOrderByCreatedAtAsc(
            UUID organizationId, UUID requestId, CommentVisibility visibility);
}

