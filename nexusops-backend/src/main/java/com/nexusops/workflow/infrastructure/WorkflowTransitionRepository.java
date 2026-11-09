package com.nexusops.workflow.infrastructure;

import com.nexusops.workflow.domain.WorkflowTransitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowTransitionRepository extends JpaRepository<WorkflowTransitionEntity, UUID> {
    List<WorkflowTransitionEntity> findAllByWorkflowVersionId(UUID workflowVersionId);
    List<WorkflowTransitionEntity> findAllBySourceNodeIdOrderByPriorityAsc(UUID sourceNodeId);
    void deleteAllByWorkflowVersionId(UUID workflowVersionId);
}

