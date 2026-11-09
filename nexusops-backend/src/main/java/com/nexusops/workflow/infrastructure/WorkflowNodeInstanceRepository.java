package com.nexusops.workflow.infrastructure;

import com.nexusops.workflow.domain.WorkflowNodeInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowNodeInstanceRepository extends JpaRepository<WorkflowNodeInstanceEntity, UUID> {
    List<WorkflowNodeInstanceEntity> findAllByWorkflowInstanceIdOrderByEnteredAtAsc(UUID workflowInstanceId);
    Optional<WorkflowNodeInstanceEntity> findFirstByWorkflowInstanceIdAndNodeIdOrderByEnteredAtDesc(UUID workflowInstanceId, UUID nodeId);
}

