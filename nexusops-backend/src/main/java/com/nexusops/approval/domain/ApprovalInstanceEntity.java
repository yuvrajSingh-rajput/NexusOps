package com.nexusops.approval.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "approval_instances")
public class ApprovalInstanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "node_instance_id", nullable = false)
    private UUID nodeInstanceId;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "assigned_membership_id")
    private UUID assignedMembershipId;

    @Column(name = "assigned_role_id")
    private UUID assignedRoleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "approver_type", nullable = false, length = 50)
    private ApproverType approverType = ApproverType.DIRECT_MEMBERSHIP;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "due_at")
    private Instant dueAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ApprovalInstanceEntity() {}

    public ApprovalInstanceEntity(UUID organizationId, UUID nodeInstanceId, UUID requestId,
                                  UUID assignedMembershipId, UUID assignedRoleId,
                                  ApproverType approverType, Instant dueAt) {
        this.organizationId = organizationId;
        this.nodeInstanceId = nodeInstanceId;
        this.requestId = requestId;
        this.assignedMembershipId = assignedMembershipId;
        this.assignedRoleId = assignedRoleId;
        this.approverType = approverType;
        this.status = ApprovalStatus.PENDING;
        this.dueAt = dueAt;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
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

    public UUID getNodeInstanceId() {
        return nodeInstanceId;
    }

    public void setNodeInstanceId(UUID nodeInstanceId) {
        this.nodeInstanceId = nodeInstanceId;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public UUID getAssignedMembershipId() {
        return assignedMembershipId;
    }

    public void setAssignedMembershipId(UUID assignedMembershipId) {
        this.assignedMembershipId = assignedMembershipId;
    }

    public UUID getAssignedRoleId() {
        return assignedRoleId;
    }

    public void setAssignedRoleId(UUID assignedRoleId) {
        this.assignedRoleId = assignedRoleId;
    }

    public ApproverType getApproverType() {
        return approverType;
    }

    public void setApproverType(ApproverType approverType) {
        this.approverType = approverType;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public void setStatus(ApprovalStatus status) {
        this.status = status;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApprovalInstanceEntity that = (ApprovalInstanceEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

