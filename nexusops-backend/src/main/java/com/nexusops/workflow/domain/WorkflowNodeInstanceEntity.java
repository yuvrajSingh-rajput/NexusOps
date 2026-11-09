package com.nexusops.workflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "workflow_node_instances")
public class WorkflowNodeInstanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_instance_id", nullable = false)
    private UUID workflowInstanceId;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private NodeInstanceStatus status = NodeInstanceStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "entered_at", nullable = false, updatable = false)
    private Instant enteredAt = Instant.now();

    @Column(name = "exited_at")
    private Instant exitedAt;

    public WorkflowNodeInstanceEntity() {}

    public WorkflowNodeInstanceEntity(UUID workflowInstanceId, UUID nodeId, NodeInstanceStatus status) {
        this.workflowInstanceId = workflowInstanceId;
        this.nodeId = nodeId;
        this.status = status;
        this.enteredAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public void setWorkflowInstanceId(UUID workflowInstanceId) {
        this.workflowInstanceId = workflowInstanceId;
    }

    public UUID getNodeId() {
        return nodeId;
    }

    public void setNodeId(UUID nodeId) {
        this.nodeId = nodeId;
    }

    public NodeInstanceStatus getStatus() {
        return status;
    }

    public void setStatus(NodeInstanceStatus status) {
        this.status = status;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public Instant getEnteredAt() {
        return enteredAt;
    }

    public Instant getExitedAt() {
        return exitedAt;
    }

    public void setExitedAt(Instant exitedAt) {
        this.exitedAt = exitedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowNodeInstanceEntity that = (WorkflowNodeInstanceEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
