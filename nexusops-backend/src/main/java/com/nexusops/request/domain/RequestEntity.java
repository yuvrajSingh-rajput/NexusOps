package com.nexusops.request.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "requests", uniqueConstraints = {
        @UniqueConstraint(name = "uq_req_org_num", columnNames = {"organization_id", "request_number"}),
        @UniqueConstraint(name = "uq_req_org_id", columnNames = {"organization_id", "id"})
})
public class RequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "request_number", nullable = false, length = 30)
    private String requestNumber;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "requester_membership_id", nullable = false)
    private UUID requesterMembershipId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestPriority priority = RequestPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RequestStatus status = RequestStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_snapshot", nullable = false, columnDefinition = "jsonb")
    private String schemaSnapshot = "{}";

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public RequestEntity() {
    }

    public RequestEntity(UUID organizationId, String requestNumber, UUID categoryId,
                         UUID requesterMembershipId, UUID departmentId, String title,
                         String description, RequestPriority priority, String payload,
                         String schemaSnapshot) {
        this.organizationId = organizationId;
        this.requestNumber = requestNumber;
        this.categoryId = categoryId;
        this.requesterMembershipId = requesterMembershipId;
        this.departmentId = departmentId;
        this.title = title;
        this.description = description;
        this.priority = priority != null ? priority : RequestPriority.MEDIUM;
        this.payload = payload != null ? payload : "{}";
        this.schemaSnapshot = schemaSnapshot != null ? schemaSnapshot : "{}";
        this.status = RequestStatus.DRAFT;
        this.version = 0L;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onPreUpdate() {
        this.updatedAt = Instant.now();
    }

    public void submit() {
        if (this.status != RequestStatus.DRAFT) {
            throw new IllegalStateException("Only requests in DRAFT state can be submitted.");
        }
        this.status = RequestStatus.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    public void cancel() {
        if (!this.status.canBeCancelled()) {
            throw new IllegalStateException("Request in status " + this.status + " cannot be cancelled.");
        }
        this.status = RequestStatus.CANCELLED;
    }

    public void complete() {
        this.status = RequestStatus.COMPLETED;
        this.completedAt = Instant.now();
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

    public String getRequestNumber() {
        return requestNumber;
    }

    public void setRequestNumber(String requestNumber) {
        this.requestNumber = requestNumber;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public UUID getRequesterMembershipId() {
        return requesterMembershipId;
    }

    public void setRequesterMembershipId(UUID requesterMembershipId) {
        this.requesterMembershipId = requesterMembershipId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public RequestPriority getPriority() {
        return priority;
    }

    public void setPriority(RequestPriority priority) {
        this.priority = priority;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getSchemaSnapshot() {
        return schemaSnapshot;
    }

    public void setSchemaSnapshot(String schemaSnapshot) {
        this.schemaSnapshot = schemaSnapshot;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

