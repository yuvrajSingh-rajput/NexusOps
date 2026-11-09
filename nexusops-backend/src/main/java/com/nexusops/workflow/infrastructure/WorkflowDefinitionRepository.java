package com.nexusops.workflow.infrastructure;

import com.nexusops.workflow.domain.WorkflowDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinitionEntity, UUID> {
    Optional<WorkflowDefinitionEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);
    List<WorkflowDefinitionEntity> findAllByOrganizationId(UUID organizationId);
    Optional<WorkflowDefinitionEntity> findByOrganizationIdAndCategoryIdAndActiveTrue(UUID organizationId, UUID categoryId);
    Optional<WorkflowDefinitionEntity> findByOrganizationIdAndCode(UUID organizationId, String code);
    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
}
