package com.nexusops.shared.events;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, ProcessedEventId> {

    boolean existsByIdConsumerNameAndIdEventId(String consumerName, java.util.UUID eventId);
}

