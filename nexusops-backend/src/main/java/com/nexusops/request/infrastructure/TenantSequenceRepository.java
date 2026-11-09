package com.nexusops.request.infrastructure;

import com.nexusops.request.domain.TenantSequenceEntity;
import com.nexusops.request.domain.TenantSequenceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantSequenceRepository extends JpaRepository<TenantSequenceEntity, TenantSequenceId> {
}

