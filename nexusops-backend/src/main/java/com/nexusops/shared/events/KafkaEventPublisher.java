package com.nexusops.shared.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper;
    private final boolean kafkaEnabled;

    public KafkaEventPublisher(
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
            ObjectMapper objectMapper,
            @Value("${nexusops.kafka.enabled:false}") boolean kafkaEnabled) {
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        this.objectMapper = objectMapper;
        this.kafkaEnabled = kafkaEnabled;
    }

    @Override
    public void publish(EventEnvelope<?> envelope) {
        if (!kafkaEnabled) {
            log.debug("Kafka publishing is disabled. Skipping broker delivery for event {}", envelope.eventId());
            return;
        }

        KafkaTemplate<String, String> template = kafkaTemplateProvider.getIfAvailable();
        if (template == null) {
            log.warn("KafkaTemplate unavailable. Event {} will remain in local outbox relay", envelope.eventId());
            return;
        }

        try {
            String topic = "nexusops." + envelope.aggregateType().toLowerCase() + ".events";
            String key = envelope.organizationId().toString();
            String payloadJson = objectMapper.writeValueAsString(envelope);

            template.send(topic, key, payloadJson);
            log.info("Dispatched event {} ({}) to Kafka topic {}", envelope.eventId(), envelope.eventType(), topic);
        } catch (Exception e) {
            log.error("Failed to send event {} to Kafka: {}", envelope.eventId(), e.getMessage(), e);
            throw new RuntimeException("Kafka dispatch failed: " + e.getMessage(), e);
        }
    }
}

