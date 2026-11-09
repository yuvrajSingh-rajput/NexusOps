package com.nexusops.workflow.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "workflow_nodes")
public class WorkflowNodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_version_id", nullable = false)
    private UUID workflowVersionId;

    @Column(name = "node_key", nullable = false, length = 50)
    private String nodeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 30)
    private NodeType nodeType;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> configuration = new HashMap<>();

    @Column(name = "position_x", nullable = false)
    private double positionX = 0.0;

    @Column(name = "position_y", nullable = false)
    private double positionY = 0.0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public WorkflowNodeEntity() {}

    public WorkflowNodeEntity(UUID workflowVersionId, String nodeKey, NodeType nodeType, String title, Map<String, Object> configuration) {
        this.workflowVersionId = workflowVersionId;
        this.nodeKey = nodeKey;
        this.nodeType = nodeType;
        this.title = title;
        this.configuration = configuration != null ? configuration : new HashMap<>();
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

    public String getNodeKey() {
        return nodeKey;
    }

    public void setNodeKey(String nodeKey) {
        this.nodeKey = nodeKey;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public void setNodeType(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration;
    }

    public double getPositionX() {
        return positionX;
    }

    public void setPositionX(double positionX) {
        this.positionX = positionX;
    }

    public double getPositionY() {
        return positionY;
    }

    public void setPositionY(double positionY) {
        this.positionY = positionY;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowNodeEntity that = (WorkflowNodeEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

