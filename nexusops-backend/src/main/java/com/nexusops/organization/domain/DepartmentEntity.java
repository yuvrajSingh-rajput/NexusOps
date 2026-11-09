package com.nexusops.organization.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "departments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_dept_org_code", columnNames = {"organization_id", "code"}),
        @UniqueConstraint(name = "uq_dept_org_id", columnNames = {"organization_id", "id"})
})
public class DepartmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_department_id")
    private DepartmentEntity parentDepartment;

    @Column(name = "head_membership_id")
    private UUID headMembershipId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public DepartmentEntity() {
    }

    public DepartmentEntity(UUID organizationId, String code, String name, DepartmentEntity parentDepartment) {
        this.organizationId = organizationId;
        this.code = code.toUpperCase().trim();
        this.name = name.trim();
        this.parentDepartment = parentDepartment;
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
        this.code = code != null ? code.toUpperCase().trim() : null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DepartmentEntity getParentDepartment() {
        return parentDepartment;
    }

    public void setParentDepartment(DepartmentEntity parentDepartment) {
        this.parentDepartment = parentDepartment;
    }

    public UUID getHeadMembershipId() {
        return headMembershipId;
    }

    public void setHeadMembershipId(UUID headMembershipId) {
        this.headMembershipId = headMembershipId;
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
        DepartmentEntity that = (DepartmentEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

