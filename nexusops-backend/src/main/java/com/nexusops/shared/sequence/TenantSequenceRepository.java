package com.nexusops.shared.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantSequenceRepository extends JpaRepository<TenantSequenceEntity, TenantSequenceId> {
}

