package com.nexusops.shared.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a business event into the transactional outbox.
     * MUST execute within the same database transaction as the business state mutation.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEventEntity publish(UUID organizationId, String eventType, String aggregateType,
                                     UUID aggregateId, Object payload) {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event payload for event {}: {}", eventType, e.getMessage());
            payloadJson = "{}";
        }

        OutboxEventEntity entity = new OutboxEventEntity(
                organizationId,
                eventType,
                1,
                aggregateType,
                aggregateId,
                correlationId,
                payloadJson
        );

        entity = outboxEventRepository.save(entity);
        log.debug("Enqueued outbox event {} of type {} for aggregate {}", entity.getId(), eventType, aggregateId);
        return entity;
    }

    /**
     * Overload supporting explicit correlation ID.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEventEntity publish(UUID organizationId, String eventType, String aggregateType,
                                     UUID aggregateId, Object payload, String correlationId) {
        String resolvedCorrelationId = (correlationId != null && !correlationId.isBlank())
                ? correlationId
                : UUID.randomUUID().toString();

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            payloadJson = "{}";
        }

        OutboxEventEntity entity = new OutboxEventEntity(
                organizationId,
                eventType,
                1,
                aggregateType,
                aggregateId,
                resolvedCorrelationId,
                payloadJson
        );

        return outboxEventRepository.save(entity);
    }
}

