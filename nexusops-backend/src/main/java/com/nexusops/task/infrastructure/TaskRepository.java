package com.nexusops.task.infrastructure;

import com.nexusops.task.domain.TaskEntity;
import com.nexusops.task.domain.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    Optional<TaskEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<TaskEntity> findAllByOrganizationIdAndAssigneeMembershipIdOrderByCreatedAtDesc(
            UUID organizationId, UUID assigneeMembershipId
    );

    List<TaskEntity> findAllByOrganizationIdAndRequestIdOrderByCreatedAtDesc(
            UUID organizationId, UUID requestId
    );

    @Query("SELECT t FROM TaskEntity t WHERE t.organizationId = :orgId AND " +
           "(t.assigneeMembershipId IS NULL OR t.status = 'OPEN') ORDER BY t.createdAt DESC")
    List<TaskEntity> findUnassignedQueueTasks(@Param("orgId") UUID orgId);

    Page<TaskEntity> findAllByOrganizationId(UUID organizationId, Pageable pageable);

    Page<TaskEntity> findAllByOrganizationIdAndStatus(UUID organizationId, TaskStatus status, Pageable pageable);
}

