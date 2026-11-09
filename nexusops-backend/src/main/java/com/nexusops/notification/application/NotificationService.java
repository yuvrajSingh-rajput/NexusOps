package com.nexusops.notification.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.notification.application.dto.NotificationResponseDto;
import com.nexusops.notification.domain.NotificationEntity;
import com.nexusops.notification.infrastructure.NotificationRepository;
import com.nexusops.shared.events.EventEnvelope;
import com.nexusops.shared.events.ProcessedEventEntity;
import com.nexusops.shared.events.ProcessedEventRepository;
import com.nexusops.shared.exception.ResourceNotFoundException;
import com.nexusops.shared.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final String CONSUMER_NAME = "notification_consumer";

    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public NotificationService(
            NotificationRepository notificationRepository,
            ProcessedEventRepository processedEventRepository,
            ObjectMapper objectMapper,
            ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.notificationRepository = notificationRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
        this.mailSenderProvider = mailSenderProvider;
    }

    /**
     * Idempotent event consumer handling published outbox events.
     */
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEvent(EventEnvelope<?> envelope) {
        UUID eventId = envelope.eventId();

        // 1. Idempotency Check
        if (processedEventRepository.existsByIdConsumerNameAndIdEventId(CONSUMER_NAME, eventId)) {
            log.debug("Notification consumer skipping duplicate event {}", eventId);
            return;
        }

        // 2. Mark event processed defensively against concurrent consumer deliveries
        try {
            processedEventRepository.save(new ProcessedEventEntity(CONSUMER_NAME, eventId));
        } catch (DataIntegrityViolationException ex) {
            log.debug("Event {} was concurrently processed by another instance of {}", eventId, CONSUMER_NAME);
            return;
        }

        // 3. Extract notification payload details
        try {
            JsonNode payloadNode;
            if (envelope.payload() instanceof String str) {
                payloadNode = objectMapper.readTree(str);
            } else {
                payloadNode = objectMapper.valueToTree(envelope.payload());
            }

            UUID recipientMembershipId = null;
            if (payloadNode.has("requesterMembershipId")) {
                recipientMembershipId = UUID.fromString(payloadNode.get("requesterMembershipId").asText());
            } else if (payloadNode.has("membershipId")) {
                recipientMembershipId = UUID.fromString(payloadNode.get("membershipId").asText());
            } else if (payloadNode.has("assigneeMembershipId")) {
                recipientMembershipId = UUID.fromString(payloadNode.get("assigneeMembershipId").asText());
            }

            if (recipientMembershipId == null) {
                log.debug("Event {} has no direct recipient membership ID, skipping in-app notification dispatch", envelope.eventType());
                return;
            }

            String title = generateTitle(envelope.eventType(), payloadNode);
            String message = generateMessage(envelope.eventType(), payloadNode);
            String linkUrl = generateLinkUrl(envelope.aggregateType(), envelope.aggregateId());

            NotificationEntity notification = new NotificationEntity(
                    envelope.organizationId(),
                    recipientMembershipId,
                    envelope.eventType(),
                    title,
                    message,
                    linkUrl
            );

            notificationRepository.save(notification);
            log.info("Created in-app notification {} for membership {}", notification.getId(), recipientMembershipId);

            // Optional: Dispatch asynchronous email via Mailpit (port 1025)
            dispatchEmailAsync(notification, payloadNode);

        } catch (Exception e) {
            log.error("Failed to process notification for event {}: {}", eventId, e.getMessage(), e);
        }
    }

    private String generateTitle(String eventType, JsonNode payload) {
        String num = payload.has("requestNumber") ? payload.get("requestNumber").asText()
                : (payload.has("taskNumber") ? payload.get("taskNumber").asText() : "");

        return switch (eventType) {
            case "REQUEST_SUBMITTED" -> "Request Submitted (" + num + ")";
            case "REQUEST_CANCELLED" -> "Request Cancelled (" + num + ")";
            case "APPROVAL_CREATED" -> "Approval Required for " + num;
            case "APPROVAL_COMPLETED" -> "Request Approved (" + num + ")";
            case "APPROVAL_REJECTED" -> "Request Rejected (" + num + ")";
            case "TASK_CREATED" -> "New Task Assigned (" + num + ")";
            case "TASK_COMPLETED" -> "Fulfillment Completed (" + num + ")";
            case "DOCUMENT_INDEXED" -> "Document Indexing Completed";
            case "WORKFLOW_COMPLETED" -> "Workflow Completed (" + num + ")";
            default -> "System Notification: " + eventType;
        };
    }

    private String generateMessage(String eventType, JsonNode payload) {
        String title = payload.has("title") ? payload.get("title").asText() : "Operation";
        return switch (eventType) {
            case "REQUEST_SUBMITTED" -> "Your request \"" + title + "\" was successfully submitted and routed to workflow.";
            case "REQUEST_CANCELLED" -> "Your request \"" + title + "\" has been cancelled.";
            case "APPROVAL_CREATED" -> "An approval action is pending your review for \"" + title + "\".";
            case "APPROVAL_COMPLETED" -> "Your request \"" + title + "\" has been approved by authorized reviewers.";
            case "APPROVAL_REJECTED" -> "Your request \"" + title + "\" was rejected during approval review.";
            case "TASK_CREATED" -> "A new operations task was created for \"" + title + "\".";
            case "TASK_COMPLETED" -> "Fulfillment task for \"" + title + "\" has been marked completed.";
            case "DOCUMENT_INDEXED" -> "Knowledge document was indexed into pgvector search.";
            case "WORKFLOW_COMPLETED" -> "All workflow steps for \"" + title + "\" have completed successfully.";
            default -> "Update recorded for " + title;
        };
    }

    private String generateLinkUrl(String aggregateType, UUID aggregateId) {
        return switch (aggregateType.toUpperCase()) {
            case "REQUEST" -> "/requests";
            case "APPROVAL" -> "/approvals";
            case "TASK" -> "/tasks";
            case "DOCUMENT" -> "/knowledge";
            default -> "/";
        };
    }

    private void dispatchEmailAsync(NotificationEntity notification, JsonNode payload) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return;
        }

        try {
            String recipientEmail = payload.has("email") ? payload.get("email").asText() : null;
            if (recipientEmail != null && !recipientEmail.isBlank()) {
                SimpleMailMessage mail = new SimpleMailMessage();
                mail.setFrom("notifications@nexusops.internal");
                mail.setTo(recipientEmail);
                mail.setSubject("[NexusOps] " + notification.getTitle());
                mail.setText(notification.getMessage() + "\n\nView details: " + notification.getLinkUrl());
                mailSender.send(mail);
                log.debug("Sent notification email to {}", recipientEmail);
            }
        } catch (Exception e) {
            log.warn("Failed to dispatch email via Mailpit (non-fatal): {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponseDto> getMyNotifications(UserPrincipal principal, Pageable pageable) {
        return notificationRepository.findByOrganizationIdAndRecipientMembershipIdOrderByCreatedAtDesc(
                principal.getOrganizationId(),
                principal.getMembershipId(),
                pageable
        ).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UserPrincipal principal) {
        return notificationRepository.countByOrganizationIdAndRecipientMembershipIdAndReadFalse(
                principal.getOrganizationId(),
                principal.getMembershipId()
        );
    }

    public void markAsRead(UserPrincipal principal, UUID notificationId) {
        NotificationEntity notification = notificationRepository.findByOrganizationIdAndRecipientMembershipIdAndId(
                principal.getOrganizationId(),
                principal.getMembershipId(),
                notificationId
        ).orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    public void markAllAsRead(UserPrincipal principal) {
        notificationRepository.markAllAsRead(principal.getOrganizationId(), principal.getMembershipId());
    }

    private NotificationResponseDto toDto(NotificationEntity entity) {
        return new NotificationResponseDto(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getRecipientMembershipId(),
                entity.getEventType(),
                entity.getTitle(),
                entity.getMessage(),
                entity.getLinkUrl(),
                entity.isRead(),
                entity.getCreatedAt()
        );
    }
}

