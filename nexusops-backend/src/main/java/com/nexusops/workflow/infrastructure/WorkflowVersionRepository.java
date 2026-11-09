package com.nexusops.workflow.infrastructure;

import com.nexusops.workflow.domain.WorkflowVersionEntity;
import com.nexusops.workflow.domain.WorkflowVersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersionEntity, UUID> {
    List<WorkflowVersionEntity> findAllByWorkflowDefinitionIdOrderByVersionNumberDesc(UUID workflowDefinitionId);
    Optional<WorkflowVersionEntity> findByWorkflowDefinitionIdAndVersionNumber(UUID workflowDefinitionId, int versionNumber);
    Optional<WorkflowVersionEntity> findByWorkflowDefinitionIdAndStatus(UUID workflowDefinitionId, WorkflowVersionStatus status);
}
