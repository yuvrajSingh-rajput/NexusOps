package com.nexusops.workflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "workflow_transitions")
public class WorkflowTransitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_version_id", nullable = false)
    private UUID workflowVersionId;

    @Column(name = "source_node_id", nullable = false)
    private UUID sourceNodeId;

    @Column(name = "target_node_id", nullable = false)
    private UUID targetNodeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_expression", columnDefinition = "jsonb")
    private Map<String, Object> conditionExpression;

    @Column(name = "priority", nullable = false)
    private int priority = 0;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public WorkflowTransitionEntity() {}

    public WorkflowTransitionEntity(UUID workflowVersionId, UUID sourceNodeId, UUID targetNodeId,
                                    Map<String, Object> conditionExpression, int priority, boolean isDefault) {
        this.workflowVersionId = workflowVersionId;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.conditionExpression = conditionExpression;
        this.priority = priority;
        this.isDefault = isDefault;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWorkflowVersionId() {
        return workflowVersionId;
    }

    public void setWorkflowVersionId(UUID workflowVersionId) {
        this.workflowVersionId = workflowVersionId;
    }

    public UUID getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(UUID sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public UUID getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(UUID targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public Map<String, Object> getConditionExpression() {
        return conditionExpression;
    }

    public void setConditionExpression(Map<String, Object> conditionExpression) {
        this.conditionExpression = conditionExpression;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowTransitionEntity that = (WorkflowTransitionEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

