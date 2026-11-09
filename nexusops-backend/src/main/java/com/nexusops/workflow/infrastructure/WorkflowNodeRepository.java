package com.nexusops.workflow.infrastructure;

import com.nexusops.workflow.domain.WorkflowNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowNodeRepository extends JpaRepository<WorkflowNodeEntity, UUID> {
    List<WorkflowNodeEntity> findAllByWorkflowVersionId(UUID workflowVersionId);
    Optional<WorkflowNodeEntity> findByWorkflowVersionIdAndNodeKey(UUID workflowVersionId, String nodeKey);
}

