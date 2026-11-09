package com.nexusops.shared.events;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query("SELECT o FROM OutboxEventEntity o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC")
    List<OutboxEventEntity> findPendingEvents(Pageable pageable);

    List<OutboxEventEntity> findByOrganizationIdAndAggregateId(UUID organizationId, UUID aggregateId);

    List<OutboxEventEntity> findByOrganizationIdAndEventType(UUID organizationId, String eventType);
}

