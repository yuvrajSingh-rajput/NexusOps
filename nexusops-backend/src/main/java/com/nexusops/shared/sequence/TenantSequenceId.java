package com.nexusops.shared.sequence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class TenantSequenceId implements Serializable {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;

    public TenantSequenceId() {}

    public TenantSequenceId(UUID organizationId, String entityType) {
        this.organizationId = organizationId;
        this.entityType = entityType;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TenantSequenceId that)) return false;
        return Objects.equals(organizationId, that.organizationId) &&
               Objects.equals(entityType, that.entityType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(organizationId, entityType);
    }
}

