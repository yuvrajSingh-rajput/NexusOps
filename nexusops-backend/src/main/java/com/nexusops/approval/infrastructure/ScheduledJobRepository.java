package com.nexusops.approval.infrastructure;

import com.nexusops.approval.domain.ScheduledJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScheduledJobRepository extends JpaRepository<ScheduledJobEntity, UUID> {
    Optional<ScheduledJobEntity> findByTargetId(UUID targetId);

    @Query("SELECT j FROM ScheduledJobEntity j WHERE j.status = 'PENDING' AND j.scheduledAt <= :cutoff ORDER BY j.scheduledAt ASC")
    List<ScheduledJobEntity> findPendingJobsDue(@Param("cutoff") Instant cutoff);
}

