package com.nexusops.audit.infrastructure;

import com.nexusops.audit.domain.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    Page<AuditLogEntity> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<AuditLogEntity> findByOrganizationIdAndAggregateTypeOrderByCreatedAtDesc(
            UUID organizationId, String aggregateType, Pageable pageable);

    Page<AuditLogEntity> findByOrganizationIdAndAggregateIdOrderByCreatedAtDesc(
            UUID organizationId, UUID aggregateId, Pageable pageable);
}

