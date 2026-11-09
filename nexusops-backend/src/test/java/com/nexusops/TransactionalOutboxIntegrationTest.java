package com.nexusops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusops.audit.application.AuditLogService;
import com.nexusops.audit.application.dto.AuditLogResponseDto;
import com.nexusops.identity.application.AuthService;
import com.nexusops.identity.application.dto.AuthResponseDto;
import com.nexusops.identity.application.dto.RegisterOrgRequestDto;
import com.nexusops.notification.application.NotificationService;
import com.nexusops.notification.application.dto.NotificationResponseDto;
import com.nexusops.organization.application.DepartmentService;
import com.nexusops.organization.application.dto.CreateDepartmentDto;
import com.nexusops.request.application.RequestCategoryService;
import com.nexusops.request.application.RequestService;
import com.nexusops.request.application.dto.CreateRequestCategoryDto;
import com.nexusops.request.application.dto.CreateRequestDto;
import com.nexusops.request.application.dto.RequestResponseDto;
import com.nexusops.request.domain.RequestPriority;
import com.nexusops.shared.events.EventEnvelope;
import com.nexusops.shared.events.EventPublisher;
import com.nexusops.shared.events.OutboxEventEntity;
import com.nexusops.shared.events.OutboxEventRepository;
import com.nexusops.shared.events.OutboxRelayPublisher;
import com.nexusops.shared.events.OutboxStatus;
import com.nexusops.shared.events.ProcessedEventRepository;
import com.nexusops.shared.exception.ValidationException;
import com.nexusops.shared.security.JwtTokenProvider;
import com.nexusops.shared.security.UserPrincipal;
import com.nexusops.shared.tenant.TenantContextHolder;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TransactionalOutboxIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private RequestCategoryService categoryService;

    @Autowired
    private RequestService requestService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private OutboxRelayPublisher outboxRelayPublisher;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private EventPublisher eventPublisher;

    private String tokenOrgA;
    private UUID orgIdA;
    private UserPrincipal principalA;
    private UUID categoryIdA;

    private String tokenOrgB;
    private UUID orgIdB;
    private UserPrincipal principalB;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(eventPublisher);

        // Setup Tenant A
        String slugA = "outbox-a-" + UUID.randomUUID().toString().substring(0, 8);
        RegisterOrgRequestDto regDtoA = new RegisterOrgRequestDto(
                "Outbox Corp A", slugA, "alice." + slugA + "@outbox.com", "Password123!", "Alice", "Admin");
        AuthResponseDto authA = authService.registerOrganization(regDtoA);
        tokenOrgA = authA.accessToken();
        orgIdA = authA.organizationId();

        TenantContextHolder.setTenantId(orgIdA);

        Claims claimsA = jwtTokenProvider.getClaims(tokenOrgA);
        UUID membershipIdA = UUID.fromString(claimsA.get("membership_id", String.class));

        List<SimpleGrantedAuthority> authoritiesA = List.of(
                new SimpleGrantedAuthority("ROLE_ORG_ADMIN"),
                new SimpleGrantedAuthority("ORG_ADMIN_MANAGE"),
                new SimpleGrantedAuthority("REQUEST_CREATE"),
                new SimpleGrantedAuthority("REQUEST_VIEW_OWN")
        );
        principalA = new UserPrincipal(authA.userId(), authA.organizationId(), membershipIdA, authA.email(), authoritiesA);

        departmentService.createDepartment(new CreateDepartmentDto("Engineering", "ENG", null, null));

        String schema = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "properties": {
                    "item": { "type": "string" },
                    "budget": { "type": "number" }
                  },
                  "required": ["item"]
                }
                """;
        var catA = categoryService.createCategory(new CreateRequestCategoryDto(
                "HARDWARE", "Hardware Request", "Procurement requests", "laptop", schema, true));
        categoryIdA = catA.id();

        // Setup Tenant B
        String slugB = "outbox-b-" + UUID.randomUUID().toString().substring(0, 8);
        RegisterOrgRequestDto regDtoB = new RegisterOrgRequestDto(
                "Outbox Corp B", slugB, "bob." + slugB + "@outbox.com", "Password123!", "Bob", "Admin");
        AuthResponseDto authB = authService.registerOrganization(regDtoB);
        tokenOrgB = authB.accessToken();
        orgIdB = authB.organizationId();

        Claims claimsB = jwtTokenProvider.getClaims(tokenOrgB);
        UUID membershipIdB = UUID.fromString(claimsB.get("membership_id", String.class));

        principalB = new UserPrincipal(authB.userId(), authB.organizationId(), membershipIdB, authB.email(),
                List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN")));
    }

    @Test
    @DisplayName("1. Mutation writes business record and outbox event in the same atomic database transaction")
    void shouldPersistBusinessRecordAndOutboxEventInSameTransaction() {
        TenantContextHolder.setTenantId(orgIdA);

        CreateRequestDto dto = new CreateRequestDto(
                categoryIdA,
                null,
                "Developer Workstation",
                "High performance Mac Studio",
                RequestPriority.HIGH,
                Map.of("item", "Mac Studio M2 Ultra", "budget", 4000.00),
                true
        );

        RequestResponseDto created = requestService.createRequest(dto, principalA);
        assertThat(created).isNotNull();
        assertThat(created.requestNumber()).startsWith("REQ-");

        // Verify outbox_events record created atomically
        List<OutboxEventEntity> outboxEvents = outboxEventRepository.findByOrganizationIdAndAggregateId(orgIdA, created.id());
        assertThat(outboxEvents).isNotEmpty();

        OutboxEventEntity event = outboxEvents.get(0);
        assertThat(event.getEventType()).isEqualTo("REQUEST_SUBMITTED");
        assertThat(event.getAggregateType()).isEqualTo("REQUEST");
        assertThat(event.getAggregateId()).isEqualTo(created.id());
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getPayload()).contains("Mac Studio M2 Ultra");
        assertThat(event.getPayload()).contains(created.requestNumber());
    }

    @Test
    @DisplayName("2. Transaction rollback prevents outbox event generation (Zero Phantom Events)")
    void shouldRollbackOutboxEventsWhenBusinessTransactionFails() {
        TenantContextHolder.setTenantId(orgIdA);

        // Intentionally provide invalid dynamic payload violating category JSON schema (missing required 'item' field)
        CreateRequestDto invalidDto = new CreateRequestDto(
                categoryIdA,
                null,
                "Invalid Request",
                "Missing required field in payload",
                RequestPriority.LOW,
                Map.of("wrongField", "Invalid Value"),
                true
        );

        assertThatThrownBy(() -> requestService.createRequest(invalidDto, principalA))
                .isInstanceOf(ValidationException.class);

        // Verify 0 outbox events exist for invalid request
        List<OutboxEventEntity> events = outboxEventRepository.findByOrganizationIdAndEventType(orgIdA, "REQUEST_SUBMITTED");
        assertThat(events).noneMatch(e -> e.getPayload().contains("Invalid Request"));
    }

    @Test
    @DisplayName("3. Outbox relay worker publishes PENDING events and marks them PUBLISHED")
    void shouldRelayPendingOutboxEventsAndMarkPublished() {
        TenantContextHolder.setTenantId(orgIdA);

        CreateRequestDto dto = new CreateRequestDto(
                categoryIdA,
                null,
                "4K Monitor",
                "32-inch monitor for designer",
                RequestPriority.MEDIUM,
                Map.of("item", "Dell UltraSharp 32 4K", "budget", 850.00),
                true
        );

        RequestResponseDto created = requestService.createRequest(dto, principalA);

        // Run outbox relay worker
        int relayed = outboxRelayPublisher.relayPendingEvents();
        assertThat(relayed).isGreaterThanOrEqualTo(1);

        // Verify outbox record transitioned to PUBLISHED
        List<OutboxEventEntity> events = outboxEventRepository.findByOrganizationIdAndAggregateId(orgIdA, created.id());
        assertThat(events).isNotEmpty();
        assertThat(events.get(0).getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(events.get(0).getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("4. Guarantee idempotent consumer deduplication via processed_events table")
    void shouldGuaranteeIdempotentConsumerProcessing() {
        TenantContextHolder.setTenantId(orgIdA);

        CreateRequestDto dto = new CreateRequestDto(
                categoryIdA,
                null,
                "Mechanical Keyboard",
                "Ergonomic keyboard",
                RequestPriority.LOW,
                Map.of("item", "Keychron Q3 Pro", "budget", 220.00),
                true
        );

        RequestResponseDto created = requestService.createRequest(dto, principalA);

        // Relay event once
        outboxRelayPublisher.relayPendingEvents();

        // Check Notification and Audit records created
        Page<NotificationResponseDto> notifs1 = notificationService.getMyNotifications(principalA, PageRequest.of(0, 10));
        long notifCount1 = notifs1.getTotalElements();
        assertThat(notifCount1).isGreaterThanOrEqualTo(1);

        Page<AuditLogResponseDto> audit1 = auditLogService.getAuditLogs(principalA, "REQUEST", created.id(), PageRequest.of(0, 10));
        long auditCount1 = audit1.getTotalElements();
        assertThat(auditCount1).isEqualTo(1);

        // Manually trigger the consumer with the exact same EventEnvelope a second time (Simulate Kafka replay)
        List<OutboxEventEntity> outbox = outboxEventRepository.findByOrganizationIdAndAggregateId(orgIdA, created.id());
        OutboxEventEntity entity = outbox.get(0);

        EventEnvelope<Object> duplicateEnvelope = new EventEnvelope<>(
                entity.getId(),
                entity.getEventType(),
                entity.getEventVersion(),
                entity.getOrganizationId(),
                entity.getAggregateType(),
                entity.getAggregateId(),
                entity.getCreatedAt(),
                entity.getCorrelationId(),
                entity.getPayload()
        );

        notificationService.onEvent(duplicateEnvelope);
        auditLogService.onEvent(duplicateEnvelope);

        // Verify counts did NOT duplicate (Idempotency confirmed!)
        Page<NotificationResponseDto> notifs2 = notificationService.getMyNotifications(principalA, PageRequest.of(0, 10));
        assertThat(notifs2.getTotalElements()).isEqualTo(notifCount1);

        Page<AuditLogResponseDto> audit2 = auditLogService.getAuditLogs(principalA, "REQUEST", created.id(), PageRequest.of(0, 10));
        assertThat(audit2.getTotalElements()).isEqualTo(auditCount1);
    }

    @Test
    @DisplayName("5. Kafka broker outage does NOT fail committed business requests; records FAILED in outbox")
    void shouldSurviveKafkaOutageWithoutLosingCommittedBusinessRequests() {
        TenantContextHolder.setTenantId(orgIdA);

        // Configure mock Kafka publisher to throw network exception
        doThrow(new RuntimeException("Simulated Kafka Broker Down")).when(eventPublisher).publish(any());

        CreateRequestDto dto = new CreateRequestDto(
                categoryIdA,
                null,
                "Cloud Sandbox Server",
                "Local test node",
                RequestPriority.HIGH,
                Map.of("item", "Intel NUC Extreme", "budget", 1500.00),
                true
        );

        // 1. Business request succeeds and commits despite broker outage!
        RequestResponseDto created = requestService.createRequest(dto, principalA);
        assertThat(created).isNotNull();

        // 2. Relay publisher attempts delivery, catches broker exception and marks FAILED without crashing
        outboxRelayPublisher.relayPendingEvents();

        List<OutboxEventEntity> events = outboxEventRepository.findByOrganizationIdAndAggregateId(orgIdA, created.id());
        assertThat(events).isNotEmpty();
        OutboxEventEntity event = events.get(0);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getRetryCount()).isGreaterThanOrEqualTo(1);
        assertThat(event.getErrorMessage()).contains("Simulated Kafka Broker Down");

        // 3. The business request remains completely committed and accessible
        RequestResponseDto fetched = requestService.getRequest(orgIdA, created.id());
        assertThat(fetched.title()).isEqualTo("Cloud Sandbox Server");
    }

    @Test
    @DisplayName("6. Multi-tenant isolation enforced on notifications and audit logs REST APIs")
    void shouldEnforceTenantIsolationOnNotificationsAndAuditLogs() throws Exception {
        TenantContextHolder.setTenantId(orgIdA);

        // User A creates request -> outbox -> notification + audit
        CreateRequestDto dto = new CreateRequestDto(
                categoryIdA,
                null,
                "Noise Cancelling Headphones",
                "For focus time",
                RequestPriority.MEDIUM,
                Map.of("item", "Sony WH-1000XM5", "budget", 399.00),
                true
        );
        requestService.createRequest(dto, principalA);
        outboxRelayPublisher.relayPendingEvents();

        // User A inspects their notifications -> Returns 200 with notification
        mockMvc.perform(get("/api/v1/notifications/my")
                        .header("Authorization", "Bearer " + tokenOrgA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].title").value(org.hamcrest.Matchers.containsString("Request Submitted")))
                .andExpect(jsonPath("$.data.content[0].message").value(org.hamcrest.Matchers.containsString("Noise Cancelling Headphones")));

        // User B (Tenant B) inspects notifications -> Expect 0 notifications (Isolated!)
        mockMvc.perform(get("/api/v1/notifications/my")
                        .header("Authorization", "Bearer " + tokenOrgB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isEmpty());

        // User B (Tenant B) inspects audit logs -> Expect 0 audit entries from Tenant A
        mockMvc.perform(get("/api/v1/audit/logs")
                        .header("Authorization", "Bearer " + tokenOrgB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isEmpty());
    }
}

