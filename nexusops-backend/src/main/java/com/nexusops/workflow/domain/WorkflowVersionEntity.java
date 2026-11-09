package com.nexusops.workflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "workflow_versions")
public class WorkflowVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_definition_id", nullable = false)
    private UUID workflowDefinitionId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WorkflowVersionStatus status = WorkflowVersionStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> definitionSnapshot;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public WorkflowVersionEntity() {}

    public WorkflowVersionEntity(UUID workflowDefinitionId, int versionNumber) {
        this.workflowDefinitionId = workflowDefinitionId;
        this.versionNumber = versionNumber;
        this.status = WorkflowVersionStatus.DRAFT;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWorkflowDefinitionId() {
        return workflowDefinitionId;
    }

    public void setWorkflowDefinitionId(UUID workflowDefinitionId) {
        this.workflowDefinitionId = workflowDefinitionId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public WorkflowVersionStatus getStatus() {
        return status;
    }

    public void setStatus(WorkflowVersionStatus status) {
        this.status = status;
    }

    public Map<String, Object> getDefinitionSnapshot() {
        return definitionSnapshot;
    }

    public void setDefinitionSnapshot(Map<String, Object> definitionSnapshot) {
        this.definitionSnapshot = definitionSnapshot;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowVersionEntity that = (WorkflowVersionEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

