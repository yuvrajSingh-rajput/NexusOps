package com.nexusops.approval.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "approval_actions")
public class ApprovalActionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "approval_instance_id", nullable = false)
    private UUID approvalInstanceId;

    @Column(name = "actor_membership_id", nullable = false)
    private UUID actorMembershipId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private ApprovalActionType action;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ApprovalActionEntity() {}

    public ApprovalActionEntity(UUID organizationId, UUID approvalInstanceId, UUID actorMembershipId,
                                ApprovalActionType action, String comments) {
        this.organizationId = organizationId;
        this.approvalInstanceId = approvalInstanceId;
        this.actorMembershipId = actorMembershipId;
        this.action = action;
        this.comments = comments;
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

    public UUID getApprovalInstanceId() {
        return approvalInstanceId;
    }

    public void setApprovalInstanceId(UUID approvalInstanceId) {
        this.approvalInstanceId = approvalInstanceId;
    }

    public UUID getActorMembershipId() {
        return actorMembershipId;
    }

    public void setActorMembershipId(UUID actorMembershipId) {
        this.actorMembershipId = actorMembershipId;
    }

    public ApprovalActionType getAction() {
        return action;
    }

    public void setAction(ApprovalActionType action) {
        this.action = action;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApprovalActionEntity that = (ApprovalActionEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

