package com.nexusops.request.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "tenant_sequences")
public class TenantSequenceEntity {

    @EmbeddedId
    private TenantSequenceId id;

    @Column(name = "current_val", nullable = false)
    private Long currentVal = 1000L;

    public TenantSequenceEntity() {
    }

    public TenantSequenceEntity(TenantSequenceId id, Long currentVal) {
        this.id = id;
        this.currentVal = currentVal;
    }

    public TenantSequenceId getId() {
        return id;
    }

    public void setId(TenantSequenceId id) {
        this.id = id;
    }

    public Long getCurrentVal() {
        return currentVal;
    }

    public void setCurrentVal(Long currentVal) {
        this.currentVal = currentVal;
    }

    public Long incrementAndGet() {
        this.currentVal = this.currentVal + 1;
        return this.currentVal;
    }
}
