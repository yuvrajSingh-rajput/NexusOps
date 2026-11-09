package com.nexusops.workflow.infrastructure;

import com.nexusops.workflow.domain.WorkflowInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstanceEntity, UUID> {
    Optional<WorkflowInstanceEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<WorkflowInstanceEntity> findByOrganizationIdAndRequestId(UUID organizationId, UUID requestId);
    List<WorkflowInstanceEntity> findAllByOrganizationId(UUID organizationId);
}
