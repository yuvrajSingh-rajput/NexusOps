# NexusOps – Current Project Status

**Current Phase:** Phase 9 – Transactional Outbox, Event Bus, Notifications & Audit Trail Completed (Review Gate Active)  
**Last Updated:** 2026-11-09  
**Branch:** master  

---

## Completed in Phase 9 (Transactional Outbox, Kafka Event Bus, Notifications & Audit Trail)

1. **Database Schema & Migrations (Flyway):**
   - [`V10__outbox_and_events_schema.sql`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/resources/db/migration/V10__outbox_and_events_schema.sql):
     - `outbox_events`: Persisted in the exact same ACID database transaction as domain state mutations (`id`, `organization_id`, `event_type`, `event_version`, `aggregate_type`, `aggregate_id`, `correlation_id`, `payload JSONB`, `status: PENDING, PUBLISHED, FAILED`, `retry_count`, `error_message`, `created_at`, `published_at`).
     - `processed_events`: Composite primary key `(consumer_name, event_id)` guaranteeing at-least-once consumer idempotency and eliminating duplicate processing during event replay.
     - `notifications`: Multi-tenant notification fan-out (`id`, `organization_id`, `recipient_membership_id`, `event_type`, `title`, `message`, `link_url`, `read`, `created_at`) with composite foreign key referencing `organization_memberships(organization_id, id)`.
     - `audit_logs`: Append-only compliance log (`id`, `organization_id`, `actor_membership_id`, `event_type`, `aggregate_type`, `aggregate_id`, `correlation_id`, `before_state JSONB`, `after_state JSONB`, `created_at`).
     - Defense-in-depth Row-Level Security (RLS) policies on `outbox_events`, `notifications`, and `audit_logs`.

2. **Backend Architecture & Core Outbox Framework (`com.nexusops.shared.events`):**
   - **Domain & Event Records:**
     - `EventEnvelope<T>`: Standardized versioned event envelope with metadata (`eventId`, `eventType`, `eventVersion`, `organizationId`, `aggregateType`, `aggregateId`, `occurredAt`, `correlationId`, `payload`).
     - `OutboxEventEntity`, `OutboxStatus`, `ProcessedEventEntity`, `ProcessedEventId`.
     - Repositories: `OutboxEventRepository`, `ProcessedEventRepository`.
   - **Outbox Service (`OutboxService.java`):**
     - Guarantees zero phantom events via `@Transactional(propagation = Propagation.MANDATORY)`. Any database transaction rollback discards both business mutations and outbox records simultaneously.
   - **Event Publisher Abstraction (`EventPublisher.java` & `KafkaEventPublisher.java`):**
     - Integrates with Spring Kafka (`KafkaTemplate`) with fallback logging when Kafka is disabled or offline.
   - **Outbox Relay Publisher (`OutboxRelayPublisher.java`):**
     - Scheduled background worker polling pending outbox events, dispatching to Kafka and Spring ApplicationContext, marking events `PUBLISHED` or `FAILED` with retry tracking.

3. **Notifications Module (`com.nexusops.notification`):**
   - `NotificationEntity`, `NotificationRepository`, `NotificationResponseDto`.
   - `NotificationService`: Idempotent listener handling published outbox events (`REQUEST_SUBMITTED`, `APPROVAL_CREATED`, `APPROVAL_COMPLETED`, `TASK_CREATED`, `DOCUMENT_INDEXED`, `WORKFLOW_COMPLETED`), recording in-app notifications and dispatching asynchronous notification emails via Mailpit SMTP (`localhost:1025`).
   - `NotificationController` (`/api/v1/notifications`): Endpoints for `/my` (paginated), `/unread-count`, `/{id}/read` (mark single), and `/read-all`.

4. **Audit Module (`com.nexusops.audit`):**
   - `AuditLogEntity`, `AuditLogRepository`, `AuditLogResponseDto`.
   - `AuditLogService`: Idempotent listener recording append-only audit trail with actor membership, correlation ID, and state snapshots.
   - `AuditLogController` (`/api/v1/audit`): Endpoint `/logs` with filtering by aggregate type, aggregate ID, and pagination.

5. **Outbox In-Transaction Wiring Across Domain Modules:**
   - `RequestService`: Emits `REQUEST_SUBMITTED` on submit, `REQUEST_CANCELLED` on cancel.
   - `ApprovalService`: Emits `APPROVAL_CREATED` on pending review, `APPROVAL_COMPLETED` on approve, `APPROVAL_REJECTED` on reject.
   - `TaskService`: Emits `TASK_CREATED` on task creation, `TASK_ASSIGNED` on assign/claim, `TASK_UPDATED` on status change, `TASK_COMPLETED` on fulfillment.
   - `KnowledgeService`: Emits `DOCUMENT_INDEXED` on completion of embedding.
   - `WorkflowEngineService`: Emits `WORKFLOW_COMPLETED` on reaching `END` node.

6. **Backend Integration Testing ([`TransactionalOutboxIntegrationTest.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/test/java/com/nexusops/TransactionalOutboxIntegrationTest.java)):**
   - `shouldAtomicallySaveRequestAndOutboxEvent`: Verifies request creation and outbox row are saved in the same transaction.
   - `shouldRollbackOutboxEventsWhenBusinessTransactionFails`: Verifies transaction rollback results in zero phantom outbox events.
   - `shouldRelayPendingEventsToEventBus`: Verifies relay publisher delivers events to event bus and marks rows PUBLISHED.
   - `shouldDeduplicateEventsAtConsumerUsingProcessedEvents`: Verifies consumer idempotency ignores duplicate replay events.
   - `shouldSurviveKafkaOutageWithoutLosingCommittedBusinessRequests`: Verifies broker failure marks outbox row FAILED without losing committed business state.
   - `shouldEnforceTenantIsolationOnNotificationsAndAuditLogs`: Verifies Tenant B cannot view Tenant A's notifications or audit logs (returns empty).
   - **Full Backend Test Suite:**
     - **38 tests passed, 0 failures, 0 errors (`BUILD SUCCESS`)**.
     - Spring Modulith architectural boundaries test: 100% clean across all modules.

7. **Frontend Implementation (`nexusops-frontend`):**
   - **TypeScript Definitions (`src/types/api.ts`):** Added `NotificationResponseDto` and `AuditLogResponseDto`.
   - **Notification Popover (`NotificationPopover.tsx`):**
     - Bell icon in `Topbar.tsx` with live unread badge.
     - Dropdown popover displaying unread count, event-specific icons, relative timestamps, single "Mark as read", and "Mark all read" controls.
     - Click navigates directly to target resources (`/requests`, `/approvals`, `/tasks`).
   - **Audit Trail Dashboard (`AuditTrailPage.tsx`):**
     - Accessible via `/audit` and Sidebar navigation.
     - Real-time search, aggregate type filter pills (`REQUEST`, `APPROVAL`, `TASK`, `DOCUMENT`, `WORKFLOW`), pagination, and JSON state snapshot inspector modal.
   - **Verification:**
     - Vitest (2/2 passed) and production bundle build (`tsc && vite build` in 10.72s).

---

## Overall Project Milestone Status

| Phase | Description | Status |
|---|---|---|
| **Phase 0** | Workspace Scaffolding & Monorepo Foundation | **DONE** (`phase-0`) |
| **Phase 1** | Local Infrastructure & Docker Compose Stack | **DONE** (`phase-1`) |
| **Phase 2** | Database Foundation, Identity & Multi-Tenancy | **DONE** (`phase-2`) |
| **Phase 3** | Request Management & Dynamic Form Schemas | **DONE** (`phase-3`) |
| **Phase 4** | Visual Workflow Engine (Definition & React Flow Studio) | **DONE** (`phase-4`) |
| **Phase 5** | Durable Workflow Execution Engine | **DONE** (`phase-5`) |
| **Phase 6** | Approval Engine & Decision Auditing | **DONE** (`phase-6`) |
| **Phase 7** | Operations Task Management & Queues | **DONE** (`phase-7`) |
| **Phase 8** | Nexus AI Assistant & Governed Tool Registry | **DONE** (`phase-8`) |
| **Phase 9** | Transactional Outbox, Event Bus, Notifications & Audit | **DONE** (`phase-9`) |
| **Phase 10** | Analytics, SLA Monitoring, Hardening & End-to-End Demo | **NEXT** |
