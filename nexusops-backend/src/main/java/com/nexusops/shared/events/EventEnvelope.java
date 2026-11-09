package com.nexusops.shared.events;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        UUID organizationId,
        String aggregateType,
        UUID aggregateId,
        Instant occurredAt,
        String correlationId,
        T payload
) {
    public static <T> EventEnvelope<T> create(
            String eventType,
            UUID organizationId,
            String aggregateType,
            UUID aggregateId,
            String correlationId,
            T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                1,
                organizationId,
                aggregateType,
                aggregateId,
                Instant.now(),
                correlationId != null ? correlationId : UUID.randomUUID().toString(),
                payload
        );
    }
}

