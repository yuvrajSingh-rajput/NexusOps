package com.nexusops.task.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "task_history")
public class TaskHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "actor_membership_id", nullable = false)
    private UUID actorMembershipId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_state", columnDefinition = "jsonb")
    private Map<String, Object> oldState = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_state", columnDefinition = "jsonb")
    private Map<String, Object> newState = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public TaskHistoryEntity() {}

    public TaskHistoryEntity(UUID organizationId, UUID taskId, UUID actorMembershipId, String action,
                             Map<String, Object> oldState, Map<String, Object> newState) {
        this.organizationId = organizationId;
        this.taskId = taskId;
        this.actorMembershipId = actorMembershipId;
        this.action = action;
        this.oldState = oldState != null ? oldState : new HashMap<>();
        this.newState = newState != null ? newState : new HashMap<>();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public UUID getActorMembershipId() {
        return actorMembershipId;
    }

    public void setActorMembershipId(UUID actorMembershipId) {
        this.actorMembershipId = actorMembershipId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Map<String, Object> getOldState() {
        return oldState;
    }

    public void setOldState(Map<String, Object> oldState) {
        this.oldState = oldState;
    }

    public Map<String, Object> getNewState() {
        return newState;
    }

    public void setNewState(Map<String, Object> newState) {
        this.newState = newState;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskHistoryEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

