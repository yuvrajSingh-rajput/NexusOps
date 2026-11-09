package com.nexusops.request.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "request_categories", uniqueConstraints = {
        @UniqueConstraint(name = "uq_cat_org_code", columnNames = {"organization_id", "code"}),
        @UniqueConstraint(name = "uq_cat_org_id", columnNames = {"organization_id", "id"})
})
public class RequestCategoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String icon = "file-text";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_schema", nullable = false, columnDefinition = "jsonb")
    private String payloadSchema = "{}";

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    @Column(name = "requires_workflow", nullable = false)
    private boolean requiresWorkflow = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public RequestCategoryEntity() {
    }

    public RequestCategoryEntity(UUID organizationId, String code, String name, String description,
                                 String icon, String payloadSchema, boolean requiresWorkflow) {
        this.organizationId = organizationId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.icon = icon != null ? icon : "file-text";
        this.payloadSchema = payloadSchema != null ? payloadSchema : "{}";
        this.requiresWorkflow = requiresWorkflow;
        this.schemaVersion = 1;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onPreUpdate() {
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getPayloadSchema() {
        return payloadSchema;
    }

    public void setPayloadSchema(String payloadSchema) {
        this.payloadSchema = payloadSchema;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public boolean isRequiresWorkflow() {
        return requiresWorkflow;
    }

    public void setRequiresWorkflow(boolean requiresWorkflow) {
        this.requiresWorkflow = requiresWorkflow;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
