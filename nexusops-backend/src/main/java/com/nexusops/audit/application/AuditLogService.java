package com.nexusops.audit.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.audit.application.dto.AuditLogResponseDto;
import com.nexusops.audit.domain.AuditLogEntity;
import com.nexusops.audit.infrastructure.AuditLogRepository;
import com.nexusops.shared.events.EventEnvelope;
import com.nexusops.shared.events.ProcessedEventEntity;
import com.nexusops.shared.events.ProcessedEventRepository;
import com.nexusops.shared.security.UserPrincipal;
import com.nexusops.identity.application.IdentityService;
import com.nexusops.identity.application.dto.EmployeeResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final String CONSUMER_NAME = "audit_consumer";

    private final AuditLogRepository auditLogRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final IdentityService identityService;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            ProcessedEventRepository processedEventRepository,
            ObjectMapper objectMapper,
            IdentityService identityService) {
        this.auditLogRepository = auditLogRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
        this.identityService = identityService;
    }

    /**
     * Idempotent event consumer recording immutable audit logs from outbox events.
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEvent(EventEnvelope<?> envelope) {
        UUID eventId = envelope.eventId();

        // 1. Idempotency Check
        if (processedEventRepository.existsByIdConsumerNameAndIdEventId(CONSUMER_NAME, eventId)) {
            log.debug("Audit consumer skipping duplicate event {}", eventId);
            return;
        }

        // 2. Mark event processed defensively against concurrent consumer deliveries
        try {
            processedEventRepository.save(new ProcessedEventEntity(CONSUMER_NAME, eventId));
        } catch (DataIntegrityViolationException ex) {
            log.debug("Event {} was concurrently processed by another instance of {}", eventId, CONSUMER_NAME);
            return;
        }

        // 3. Extract after-state and actor
        try {
            String afterStateJson;
            UUID actorMembershipId = null;

            if (envelope.payload() instanceof String str) {
                afterStateJson = str;
                JsonNode node = objectMapper.readTree(str);
                if (node.has("actorMembershipId")) {
                    actorMembershipId = UUID.fromString(node.get("actorMembershipId").asText());
                } else if (node.has("requesterMembershipId")) {
                    actorMembershipId = UUID.fromString(node.get("requesterMembershipId").asText());
                }
            } else {
                afterStateJson = objectMapper.writeValueAsString(envelope.payload());
                JsonNode node = objectMapper.valueToTree(envelope.payload());
                if (node.has("actorMembershipId")) {
                    actorMembershipId = UUID.fromString(node.get("actorMembershipId").asText());
                } else if (node.has("requesterMembershipId")) {
                    actorMembershipId = UUID.fromString(node.get("requesterMembershipId").asText());
                }
            }

            AuditLogEntity logEntity = new AuditLogEntity(
                    envelope.organizationId(),
                    actorMembershipId,
                    envelope.eventType(),
                    envelope.aggregateType(),
                    envelope.aggregateId(),
                    envelope.correlationId(),
                    null,
                    afterStateJson
            );

            auditLogRepository.save(logEntity);
            log.info("Recorded audit entry {} for aggregate {}:{}", logEntity.getId(), envelope.aggregateType(), envelope.aggregateId());

        } catch (Exception e) {
            log.error("Failed to record audit log for event {}: {}", eventId, e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponseDto> getAuditLogs(
            UserPrincipal principal,
            String aggregateType,
            UUID aggregateId,
            Pageable pageable) {

        UUID orgId = principal.getOrganizationId();

        if (aggregateId != null) {
            return auditLogRepository.findByOrganizationIdAndAggregateIdOrderByCreatedAtDesc(orgId, aggregateId, pageable)
                    .map(this::toDto);
        } else if (aggregateType != null && !aggregateType.isBlank()) {
            return auditLogRepository.findByOrganizationIdAndAggregateTypeOrderByCreatedAtDesc(orgId, aggregateType, pageable)
                    .map(this::toDto);
        } else {
            return auditLogRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId, pageable)
                    .map(this::toDto);
        }
    }

    private AuditLogResponseDto toDto(AuditLogEntity entity) {
        String actorName = null;
        String actorEmail = null;
        if (entity.getActorMembershipId() != null) {
            Optional<EmployeeResponseDto> empOpt = identityService.findEmployeeByMembershipId(entity.getOrganizationId(), entity.getActorMembershipId());
            if (empOpt.isPresent()) {
                EmployeeResponseDto emp = empOpt.get();
                actorName = emp.firstName() + " " + emp.lastName();
                actorEmail = emp.email();
            } else {
                actorName = "User (" + entity.getActorMembershipId().toString().substring(0, 8) + ")";
            }
        } else {
            actorName = "System Event";
        }

        return new AuditLogResponseDto(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getActorMembershipId(),
                actorName,
                actorEmail,
                entity.getEventType(),
                entity.getAggregateType(),
                entity.getAggregateId(),
                entity.getCorrelationId(),
                entity.getBeforeState(),
                entity.getAfterState(),
                entity.getCreatedAt()
        );
    }
}

