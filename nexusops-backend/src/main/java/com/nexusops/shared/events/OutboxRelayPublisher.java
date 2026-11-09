package com.nexusops.shared.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxRelayPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final EventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    public OutboxRelayPublisher(
            OutboxEventRepository outboxEventRepository,
            EventPublisher eventPublisher,
            ApplicationEventPublisher applicationEventPublisher,
            ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventPublisher = eventPublisher;
        this.applicationEventPublisher = applicationEventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Polls and relays PENDING outbox events to the event bus.
     */
    @Scheduled(
            fixedDelayString = "${nexusops.outbox.relay-interval-ms:3000}",
            initialDelayString = "${nexusops.outbox.relay-initial-delay-ms:1000}"
    )
    public int relayPendingEvents() {
        List<OutboxEventEntity> pending = outboxEventRepository.findPendingEvents(PageRequest.of(0, 50));
        if (pending.isEmpty()) {
            return 0;
        }

        int publishedCount = 0;
        for (OutboxEventEntity event : pending) {
            try {
                Object payloadObj;
                try {
                    payloadObj = objectMapper.readValue(event.getPayload(), Object.class);
                } catch (Exception e) {
                    payloadObj = event.getPayload();
                }

                EventEnvelope<Object> envelope = new EventEnvelope<>(
                        event.getId(),
                        event.getEventType(),
                        event.getEventVersion(),
                        event.getOrganizationId(),
                        event.getAggregateType(),
                        event.getAggregateId(),
                        event.getCreatedAt(),
                        event.getCorrelationId(),
                        payloadObj
                );

                // 1. Publish to Kafka
                eventPublisher.publish(envelope);

                // 2. Publish to internal Spring ApplicationContext for in-process subscribers
                applicationEventPublisher.publishEvent(envelope);

                // 3. Mark outbox entry as PUBLISHED
                event.markPublished();
                outboxEventRepository.save(event);
                publishedCount++;
            } catch (Exception e) {
                log.error("Failed to relay outbox event {}: {}", event.getId(), e.getMessage());
                event.markFailed(e.getMessage());
                outboxEventRepository.save(event);
            }
        }

        if (publishedCount > 0) {
            log.info("Relayed {} outbox events to event bus", publishedCount);
        }
        return publishedCount;
    }
}

