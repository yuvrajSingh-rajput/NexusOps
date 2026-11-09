package com.nexusops.knowledge.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", nullable = false, length = 100)
    private String category = "GENERAL";

    @Enumerated(EnumType.STRING)
    @Column(name = "access_scope", nullable = false, length = 50)
    private DocumentAccessScope accessScope = DocumentAccessScope.ALL;

    @Column(name = "target_scope_id")
    private UUID targetScopeId;

    @Column(name = "created_by_membership_id")
    private UUID createdByMembershipId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "document", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy("versionNumber DESC")
    private List<DocumentVersionEntity> versions = new ArrayList<>();

    public DocumentEntity() {}

    public DocumentEntity(UUID organizationId, String title, String description, String category,
                          DocumentAccessScope accessScope, UUID targetScopeId, UUID createdByMembershipId) {
        this.organizationId = organizationId;
        this.title = title;
        this.description = description;
        this.category = category != null ? category : "GENERAL";
        this.accessScope = accessScope != null ? accessScope : DocumentAccessScope.ALL;
        this.targetScopeId = targetScopeId;
        this.createdByMembershipId = createdByMembershipId;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public DocumentAccessScope getAccessScope() {
        return accessScope;
    }

    public void setAccessScope(DocumentAccessScope accessScope) {
        this.accessScope = accessScope;
    }

    public UUID getTargetScopeId() {
        return targetScopeId;
    }

    public void setTargetScopeId(UUID targetScopeId) {
        this.targetScopeId = targetScopeId;
    }

    public UUID getCreatedByMembershipId() {
        return createdByMembershipId;
    }

    public void setCreatedByMembershipId(UUID createdByMembershipId) {
        this.createdByMembershipId = createdByMembershipId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<DocumentVersionEntity> getVersions() {
        return versions;
    }

    public void setVersions(List<DocumentVersionEntity> versions) {
        this.versions = versions;
    }

    public void addVersion(DocumentVersionEntity version) {
        versions.add(version);
        version.setDocument(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

