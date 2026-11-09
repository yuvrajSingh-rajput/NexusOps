# NexusOps – Changelog

All notable changes to the NexusOps platform will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-11-09 - Commercial Release & Final Acceptance Hardening

### Added
- **Automated Demo Data Seeder (`DemoDataSeeder.java` & `ByteArrayMultipartFile.java`):**
  - Boot-time automated provisioner populating realistic commercial multi-tenant environments when the database is empty.
  - **Tenant 1 (Acme Corporation - `acme-corp`):**
    - Seeded personas (password: `Password123!`): `eve@acme.com` (Org Admin), `bob@acme.com` (Engineering Manager & Head), `alice@acme.com` (Software Engineer), `carol@acme.com` (Finance Manager & Head), and `dave@acme.com` (IT Operator & Head).
    - Seeded department hierarchy: Engineering (`ENG`), Finance (`FIN`), IT Operations (`IT`), and Human Resources (`HR`).
    - Seeded categories: Developer Laptop (`DEVELOPER_LAPTOP`), Software License (`SOFTWARE_ACCESS`), and Expense Reimbursement (`EXPENSE_REIMBURSEMENT`).
    - Published reference workflow: `WF_DEVELOPER_LAPTOP` (Start $\to$ Manager Review $\to$ Condition $> \$10,000$ $\to$ Finance Review $\to$ IT Task Fulfillment $\to$ End).
    - Seeded policy document: "Acme Corporation - Global IT Hardware & Procurement Policy (2026)" chunked and embedded into pgvector with 768-dimensional embeddings.
    - Seeded baseline request for instant live operational analytics.
  - **Tenant 2 (Globex Corporation - `globex-corp`):**
    - Isolated tenant with `homer@globex.com` and `frank@globex.com` for cross-tenant isolation testing.
- **Master End-to-End Acceptance Test Suite (`EndToEndDemoIntegrationTest.java`):**
  - `executeFullCommercialLifecycleAcceptanceDemo`: Verifies employee laptop submission (\$25k) $\to$ manager approval $\to$ conditional branch $\to$ finance approval $\to$ operator task claim & completion $\to$ request `COMPLETED` $\to$ org admin audit ledger & analytics review.
  - `executeNexusAiAcceptanceCapabilities`: Verifies grounded policy Q&A with citations and governed department report generation.
  - `verifyCrossTenantIsolationStrictness`: Verifies cross-tenant IDOR protection (HTTP 404), approval/task queue isolation, and RAG vector search filtering.
  - Full backend test suite passing: 46/46 tests, 0 failures, 0 errors, 100% clean Spring Modulith boundaries.
- **Frontend 1-Click Persona Quick Login (`LoginPage.tsx`):**
  - Interactive Evaluator Personas grid with visual role badges and one-click instant credentials population and login.
  - Production build passing: `tsc && vite build` in 9.49s; Vitest 2/2 tests passed.
- **Comprehensive Documentation:**
  - `docs/operations/demo-acceptance-walkthrough.md`: Complete evaluator script and step-by-step verification guide.

---

## [0.11.0-analytics] - 2026-11-09

### Added
- **Backend Analytics Engine (`com.nexusops.analytics`):**
  - `AnalyticsRepository`: Fast read-only ANSI SQL aggregations using `NamedParameterJdbcTemplate` strictly bounded by `organization_id`. Computes live request status counts, pending approval loads, open fulfillment tasks, and SLA compliance.
  - In-memory `java.time.Duration` cycle time calculations avoiding database dialect discrepancies across PostgreSQL and H2.
  - `AnalyticsService`: Generates operational KPIs, turnaround cycle times, category throughput, department queues, and executive summaries.
  - `AnalyticsController` (`/api/v1/analytics`): REST endpoints for `/overview`, `/by-status`, `/by-category`, `/by-department`, and `/department-report`.
- **Nexus AI Governed Analytics Integration:**
  - `GenerateDepartmentReportTool`: AI tool registered in `ToolRegistry`. Calls pre-computed SQL analytics to synthesize grounded executive briefing cards without executing raw SQL.
  - `IntentClassifier`: Added `DEPARTMENT_REPORT` intent classification.
- **Frontend Features & Screens:**
  - `src/types/api.ts`: Added Analytics DTOs (`AnalyticsOverviewDto`, `StatusCountDto`, `CategoryMetricDto`, `DepartmentMetricDto`, `DepartmentReportDto`).
  - `DashboardPage.tsx`: Connected to live `/api/v1/analytics/overview` with real-time stats for Active Requests, Pending Approvals, Open Tasks, and SLA Adherence.
  - `AnalyticsPage.tsx`: Dedicated operational studio at `/analytics` featuring KPI summary cards, segmented status distribution meter, category throughput bars, department queue matrix, and interactive Department Report Generator card with clipboard copy button.
  - Navigation: Wired `/analytics` route in `routes.tsx` and sidebar link in `Sidebar.tsx`.
- **Automated Verification:**
  - `AnalyticsIntegrationTest`: 5 integration tests verifying overview KPI calculation, status/category breakdowns, department metrics, multi-tenant isolation, and Nexus AI tool execution.
  - Full backend test suite: 43/43 tests passing (`BUILD SUCCESS`), with Spring Modulith architectural verification.
  - Frontend Vitest (2/2 passed) and production bundle build (`tsc && vite build` in 10.72s).

---

## [0.10.0-outbox-kafka] - 2026-11-09

### Added
- **Database Migrations:**
  - `V10__outbox_and_events_schema.sql`: Schema for `outbox_events` (atomic in-transaction persistence, `PENDING/PUBLISHED/FAILED` statuses, retry counts), `processed_events` (idempotent consumer deduplication with composite PK `(consumer_name, event_id)`), `notifications` (multi-tenant notifications fan-out, composite FK to `organization_memberships`), and `audit_logs` (append-only ledger, before/after JSONB state snapshots, correlation IDs). RLS policies on all event tables.
- **Backend Architecture & Core Outbox Framework (`com.nexusops.shared.events`):**
  - Standardized `EventEnvelope<T>` record with strict event metadata and correlation ID tracing.
  - `OutboxService`: In-transaction writer with `Propagation.MANDATORY` guaranteeing zero phantom events on database rollback.
  - `EventPublisher` abstraction and `KafkaEventPublisher` integrating Spring Kafka with offline graceful degradation.
  - `OutboxRelayPublisher`: Background worker polling pending events, delivering to Kafka broker and Spring ApplicationContext, updating status to `PUBLISHED` or `FAILED`.
- **Notification & Audit Modules (`com.nexusops.notification` & `com.nexusops.audit`):**
  - `NotificationService`: Idempotent listener for domain events (`REQUEST_SUBMITTED`, `APPROVAL_CREATED`, `APPROVAL_COMPLETED`, `TASK_CREATED`, `DOCUMENT_INDEXED`, `WORKFLOW_COMPLETED`), creating in-app notifications and async Mailpit SMTP emails.
  - `NotificationController` (`/api/v1/notifications`): Endpoints for `/my`, `/unread-count`, `/{id}/read`, and `/read-all`.
  - `AuditLogService`: Idempotent listener persisting append-only audit trail records with actor and state snapshots.
  - `AuditLogController` (`/api/v1/audit`): Endpoint `/logs` with filtering by aggregate type, aggregate ID, and pagination.
- **Outbox In-Transaction Wiring Across Domain Modules:**
  - Integrated `OutboxService` into `RequestService`, `ApprovalService`, `TaskService`, `KnowledgeService`, and `WorkflowEngineService`.
- **Frontend Features & Screens:**
  - `src/types/api.ts`: Added `NotificationResponseDto` and `AuditLogResponseDto`.
  - `NotificationPopover.tsx`: Integrated bell popover in `Topbar.tsx` with dynamic unread badge, real-time 15s polling, event-specific icons, relative timestamps, single mark-as-read, and mark-all-read controls.
  - `AuditTrailPage.tsx`: Dedicated compliance audit explorer at `/audit` with aggregate type filter pills, search, pagination, and JSON state snapshot inspector modal.
  - Navigation: Added `/audit` in `routes.tsx` and sidebar link in `Sidebar.tsx`.
- **Automated Verification:**
  - `TransactionalOutboxIntegrationTest`: 6 integration tests verifying atomic outbox write, transaction rollback discarding outbox rows, relay publishing, idempotent deduplication, Kafka broker outage resilience, and cross-tenant isolation.
  - Full backend test suite: 38/38 tests passing (`BUILD SUCCESS`), with Spring Modulith architectural verification.
  - Frontend Vitest (2/2 passed) and production bundle build (`tsc && vite build` in 10.72s).

---

## [0.9.0-nexus-ai] - 2026-11-09

### Added
- **Database Migrations:**
  - `V9__ai_schema.sql`: Schema for `ai_conversations` (scoped by organization and user), `ai_messages` (append-only role log, grounded citations JSONB, tool calls JSONB), and `ai_tool_executions` (two-phase action proposal registry with 15-minute durable TTL, arguments JSONB, result summary JSONB, error codes, and confirmation tracking). Row-Level Security (RLS) policies and composite foreign keys on all AI tables.
- **Backend Architecture & Governed AI Engine (`com.nexusops.ai`):**
  - `ToolRegistry` & Governed Tools: `SearchMyRequestsTool`, `GetRequestDetailsTool`, `SearchPendingApprovalsTool`, `SearchMyTasksTool`, `SearchKnowledgeBaseTool`, and `CreateRequestTool`. Identity is strictly server-derived from `SecurityContextHolder`; AI arguments never override tenant or user IDs.
  - Two-Phase Confirmation Engine: Write actions (e.g. creating requests) produce a structured `ActionProposalDto` card in `PROPOSED` state. Mutations only execute via `/api/v1/ai/actions/{id}/confirm` invoked by the authenticated user.
  - Prompt-Injection Defense (`RagContextBuilder`): Retrieved knowledge context is isolated within untrusted XML tags (`<untrusted_document_context>`), ensuring the LLM treats document content as reference data, never operational instructions.
  - Zero-Cloud Degradation (`AiModelProvider`): Integrates with local Ollama or external APIs, with automatic fallback to deterministic rule routing and template synthesis when no LLM is configured.
  - `AiController` (`/api/v1/ai`): Endpoints for `/chat` (synchronous), `/chat/stream` (SSE), `/conversations`, `/conversations/{id}`, `/actions/{id}/confirm`, and `/actions/{id}/cancel`.
- **Frontend Features & Screens:**
  - `src/types/api.ts`: Added AI types (`AiMessageRole`, `ToolExecutionStatus`, `ActionProposalDto`, `ChatCitationDto`, `ChatResponseDto`, `ChatRequestDto`, `ConfirmActionDto`, `ConversationSummaryDto`, `ChatMessageDto`).
  - `AiAssistantDrawer.tsx`: Persistent slide-over co-pilot drawer accessible from anywhere in the application via Topbar sparkle icon. Supports streaming chat, grounded citation badges with excerpt inspection modal, and two-phase action proposal cards with Confirm/Dismiss controls.
  - `AiAssistantPage.tsx`: Dedicated full-workspace studio with 3-column layout (conversation history, interactive chat canvas with suggested query cards, and AI Governance & Security Inspector panel).
  - Navigation: Added `/ai-assistant` route in `routes.tsx` and sidebar link in `Sidebar.tsx`.
- **Automated Verification:**
  - `AiAssistantIntegrationTest`: 4 integration tests verifying grounded policy lookup with citations, read tools for personal requests, two-phase confirmation creating requests in the database and triggering workflows, and cross-tenant isolation (HTTP 404).
  - Full backend test suite: 32/32 tests passing (`BUILD SUCCESS`), including Spring Modulith architectural boundary verification.
  - Frontend Vitest (2/2 passed) and production bundle build (`tsc && vite build` in 9.57s).

---

## [0.8.0-knowledge-base] - 2026-11-09

### Added
- **Database Migrations:**
  - `V8__knowledge_schema.sql`: Schema for `documents`, `document_versions` (checksums, file sizes, storage keys, processing status), `document_chunks` (text slices, page numbers, `VECTOR(768)` pgvector column, HNSW cosine distance index), implicit string-to-vector casts, and RLS policies.
- **Backend Architecture & Storage Services (`com.nexusops.knowledge` & `com.nexusops.shared.storage`):**
  - `StorageService` interface with `LocalStorageService` (local filesystem with path traversal protection) and `MinioStorageService` (MinIO S3 on port 9000).
  - `TextExtractionService`: Structured document parser for Markdown, plain text, JSON, and PDF streams.
  - `DocumentChunkerService`: Sliding-window chunker with 10% overlap (~500 tokens / 1800 chars) preserving natural paragraph and sentence boundaries.
  - `VectorEmbeddingService`: 768-dimensional embedding engine supporting Ollama `nomic-embed-text` with deterministic local semantic hash projection fallback for offline test environments.
  - `KnowledgeService`: Document upload, version management, re-indexing, cascading deletion, and tenant-scoped semantic similarity search with cosine scoring and formatted citations.
  - `KnowledgeController` (`/api/v1/knowledge`): Endpoints for upload, list, get, download version binary, reindex, delete, and semantic search.
- **Frontend Features & Screens:**
  - `src/types/api.ts`: Added types for `DocumentAccessScope`, `DocumentProcessingStatus`, `DocumentResponseDto`, `DocumentVersionResponseDto`, `SearchResultDto`, `SemanticSearchRequestDto`.
  - `KnowledgePage.tsx`: Interactive Knowledge Base dashboard with metrics banner, category filter pills, document cards grid, upload modal with drag-and-drop, and real-time Semantic Search Lab drawer with relevance threshold slider and ranked citation excerpts.
- **Automated Verification:**
  - `KnowledgeEngineIntegrationTest`: 3 multi-tenant integration tests verifying document upload, version indexing, binary download, semantic search with relevance scoring, cross-tenant isolation (HTTP 404 and 0 search results), re-indexing, and cascading deletion.
  - Full backend test suite: 28/28 tests passing (`BUILD SUCCESS`), including Spring Modulith architectural verification.
  - Frontend Vitest (2/2 passed) and production bundle build (`tsc && vite build`).

---

## [0.7.0-task-management] - 2026-11-09

### Added
- **Database Migrations:**
  - `V7__task_schema.sql`: Schema for `tasks` (`@Version` optimistic locking, SLA `due_at`, `payload` JSONB, fulfillment `output_data` JSONB), `task_comments` (operational notes with internal privacy flags), and `task_history` (append-only audit trail of state transitions).
- **Backend Architecture & Domain Services (`com.nexusops.task` & `com.nexusops.shared`):**
  - `TenantSequenceEntity` & `SequenceGeneratorService` (`com.nexusops.shared.sequence`): Reusable thread-safe sequence generator providing unique human-readable numbers (`TASK-1001`, `REQ-1001`) with Spring Modulith boundary compliance.
  - Event Decoupling via `TaskRequestedEvent`: Workflow engine's `TaskNodeHandler` publishes event; `TaskService` listens via `@EventListener`, auto-creating fulfillment tasks.
  - `TaskService`: Manages task lifecycle (`createTask`, `claimTask`, `assignTask`, `updateTaskStatus`, `completeTask`, `addComment`), output data persistence, workflow resumption signal, and request completion closure.
  - `TaskController` (`/api/v1/tasks`): Queue endpoints (`/queue`, `/my`), task retrieval (`/{id}`, `/request/{requestId}`), state transitions (`/claim`, `/assign`, `/status`, `/complete`), and comments (`/comments`).
- **Frontend Features & Screens:**
  - `src/types/api.ts`: Added task types (`TaskStatus`, `TaskPriority`, `TaskHistoryAction`, `TaskResponseDto`, `TaskCommentResponseDto`, `TaskHistoryResponseDto`, `CompleteTaskDto`, `UpdateTaskStatusDto`, `AddTaskCommentDto`).
  - `TasksPage.tsx`: Interactive fulfillment portal with Unassigned Queue and My Tasks views, status filtering, quick-claim action, fulfillment drawer with dynamic payload viewer, complete task modal with artifact capture (serial number, carrier, tracking), and activity audit timeline.
- **Automated Verification:**
  - `TaskEngineIntegrationTest`: 4 integration tests verifying end-to-end procurement fulfillment journey, zero tasks created on rejection, status transitions (`IN_PROGRESS` <-> `BLOCKED`) with audit logging, and cross-tenant isolation.
  - Full backend test suite: 25/25 tests passing (`BUILD SUCCESS`), including Spring Modulith architectural verification.
  - Frontend Vitest (2/2 passed) and production bundle build (`tsc && vite build`).

---

## [0.6.0-approval-engine] - 2026-11-09

### Added
- **Database Migrations:**
  - `V6__approval_schema.sql`: Schema for `approval_instances` (`@Version` optimistic locking, SLA `due_at`), `approval_actions` (append-only audit log of decisions), and `scheduled_jobs` (durable job store for approval timeouts and SLA reminders).
- **Backend Architecture & Domain Services (`com.nexusops.approval`):**
  - `ApproverResolutionService`: Resolves approvers dynamically based on node config (`MANAGER`, `DEPT_HEAD`, `ROLE`, or direct membership).
  - `ApprovalService`: Manages approval lifecycle, list queries for user/role inboxes, append-only decision audit logging, decision routing (`APPROVE`, `REJECT`, `REQUEST_INFORMATION`), and workflow resumption.
  - Event Decoupling via `ApprovalRequestedEvent`: Workflow engine publishes event on reaching `APPROVAL` nodes; `ApprovalService` listens via `@EventListener`, maintaining strict one-way dependency (`approval` -> `workflow`).
  - `ApprovalController` (`/api/v1/approvals`): Endpoints for pending approvals (`/pending`, `/my-pending`), single approval detail, request approvals (`/request/{requestId}`), and decision execution (`/{id}/action`).
  - Core `FINANCE` system role added to organization registration.
- **Frontend Features & Screens:**
  - `src/types/api.ts`: Added types for `ApprovalStatus`, `ApprovalActionType`, `ApproverType`, `ApprovalInstanceResponseDto`, `ApprovalActionResponseDto`, `ApprovalDecisionDto`.
  - `ApprovalsPage.tsx`: Interactive Approval Inbox with live pending counter badge, filter tabs, dynamic request attributes viewer, decision modal with mandatory rejection comments, and concurrency conflict handling.
- **Automated Verification:**
  - `ApprovalEngineIntegrationTest`: 5 integration tests verifying end-to-end Manager approval flow, Rejection termination with zero tasks, Finance role queue escalation, optimistic locking conflict detection (`409 Conflict`), and cross-tenant isolation.
  - Full backend test suite: 21/21 tests passing (`BUILD SUCCESS`), including Spring Modulith architectural verification.
  - Frontend Vitest (2/2 passed) and production bundle build (`tsc && vite build`).

---

## [0.5.0-workflow-engine] - 2026-11-09

### Added
- **Database Migrations:**
  - `V5__workflow_schema.sql`: Full schema for graph workflow definitions, immutable version snapshots, nodes with UI canvas positions, directed transitions with typed condition expressions, durable workflow instances (`@Version` optimistic locking), and node execution audit records.
- **Backend Architecture & Domain Services (`com.nexusops.workflow`):**
  - `TypedConditionEvaluator`: Security-hardened JSON predicate evaluator with whitelisted operators (`EQUALS`, `NOT_EQUALS`, `GREATER_THAN`, `LESS_THAN`, `IN`, `CONTAINS`), forbidding SpEL, JavaScript, or arbitrary code execution.
  - `WorkflowGraphValidator`: BFS reachability validation, single `START` node check, `END` node reachability, dead-end detection, and node key uniqueness.
  - `NodeHandler` & `NodeHandlerRegistry`: Polymorphic node handlers for `START`, `END`, `APPROVAL`, `CONDITION`, and `TASK` nodes.
  - `WorkflowEngineService`: Durable lifecycle management (`startWorkflow`, `resumeWorkflowWithSignal`, `advanceWorkflow`), state transitions, and node instance auditing.
  - `WorkflowDefinitionService`: Tenant workflow catalogue, graph compilation, and automated reference Laptop Purchase workflow seeding ($10k and $50k thresholds).
  - `WorkflowDefinitionController` (`/api/v1/workflows`) and `WorkflowInstanceController` (`/api/v1/workflow-instances`).
- **Frontend Features & Screens:**
  - `src/types/api.ts`: Phase 4 contracts for workflows, nodes, transitions, instances, and statuses.
  - `WorkflowsPage.tsx`: Workflow Studio with active version pills, interactive Graph Inspector modal displaying all nodes and transition predicates, and workflow creation modal.
  - `RequestsPage.tsx`: Live Workflow Orchestration Audit Trail integrated into request details drawer with active node indicators and step timestamps.
- **Automated Verification:**
  - `WorkflowEngineIntegrationTest`: 5/5 tests covering reference workflow bootstrap, low-amount branching (< $10k), medium-amount finance escalation ($25k), manager rejection termination, and cross-tenant workflow isolation.
  - Full backend test suite: 16/16 tests passing, including Spring Modulith architectural boundary verification.
  - Frontend Vitest and Vite production bundle (`tsc && vite build`) passing with zero errors.

---

## [0.4.0-request-engine] - 2026-11-09

### Added
- **Database Migrations:**
  - `V4__request_schema.sql`: Added `tenant_sequences` for thread-safe sequential numbering, `request_categories` with `payload_schema` JSONB, `requests` with composite foreign keys `(organization_id, requester_membership_id)` and optimistic locking (`@Version`), `request_comments` with visibility partitioning (`INTERNAL` vs `REQUESTER_VISIBLE`), and `request_attachments`.
- **Backend Architecture & Domain Services (`com.nexusops.request`):**
  - `TenantSequenceEntity` and `TenantSequenceRepository`: Atomic sequence increment per tenant to generate `REQ-1001`, `REQ-1002`, `TASK-1001` with zero concurrency conflicts.
  - `RequestCategoryEntity` and `RequestCategoryService`: Category management and dynamic JSON schema field validation. Auto-bootstraps standard categories (`DEVELOPER_LAPTOP`, `SOFTWARE_ACCESS`, `EXPENSE_REIMBURSEMENT`).
  - `RequestEntity`, `RequestCommentEntity`, and `RequestService`: Full request submission engine, schema snapshotting, lifecycle transitions, department snapshotting, cancellation guardrails, IDOR protection, and activity comments.
  - `RequestCategoryController` (`/api/v1/request-categories`) and `RequestController` (`/api/v1/requests`, `/api/v1/requests/{id}`, `/api/v1/requests/{id}/cancel`, `/api/v1/requests/{id}/comments`).
  - Module boundary exposure via `@NamedInterface` annotations for Modulith verification.
- **Frontend Features & Screens:**
  - `src/types/api.ts`: Phase 3 contracts for requests, categories, comments, priorities, and statuses.
  - `RequestsPage.tsx`: Interactive Operations Requests portal featuring status filter tabs, search, priority indicators, two-step dynamic form wizard powered by category JSON Schema, request detail drawer with payload attributes, cancellation workflow, and comments timeline.
- **Automated Verification:**
  - `RequestControllerIntegrationTest`: 4/4 integration tests verifying sequential numbers, category auto-seeding, dynamic payload validation, comment visibility, and multi-tenant isolation.
  - Modulith verification: 100% clean module dependencies.
  - Frontend Vitest (2/2 passed) and production bundle build (`tsc && vite build`).

---

## [0.3.0-identity-org] - 2026-11-09

### Added
- **Database Migrations:**
  - `V2__identity_and_organization_schema.sql`: Multi-tenant identity tables (`users`, `organizations`, `organization_memberships`, `departments`, `teams`, `roles`, `permissions`, `refresh_tokens`) with composite foreign keys `(organization_id, id)`.
  - `V3__seed_system_roles_and_permissions.sql`: Core system permission catalogue across operational domains.
- **Authentication & Security (`shared.security`):**
  - `JwtTokenProvider`: HS512 JWT access token generation and validation with tenant isolation claims, and cryptographic 256-bit refresh token hashing.
  - `JwtAuthenticationFilter`: Automatic Bearer token extraction, Spring Security principal population, and `TenantContextHolder` injection.
  - `SecurityConfig`: Stateless JWT security filter chain, method security enabled (`@EnableMethodSecurity`), and BCrypt password encryption.
- **Backend Application Services & REST APIs:**
  - `AuthService` & `AuthController`: Self-service organization registration (`/api/v1/auth/register-org`), login with HttpOnly refresh cookies (`/api/v1/auth/login`), refresh token rotation with token reuse detection (`/api/v1/auth/refresh`), profile inspection (`/api/v1/auth/me`), and tenant context switching (`/api/v1/auth/switch-org`).
  - `DepartmentService` & `DepartmentController`: Department CRUD, tree hierarchy verification, and duplicate code prevention scoped to the authenticated tenant (`/api/v1/departments`).
  - `IdentityService` & `MembershipController`: Paginated employee directory queries and member invitations with department and role assignments (`/api/v1/memberships`).
- **Frontend Features & Screens:**
  - `authStore.ts`: Zustand store for token persistence, active organization context, and session refresh.
  - `ProtectedRoute.tsx`: Client-side route guard preventing unauthenticated access to workspace pages.
  - `LoginPage.tsx`: Enterprise Sign In screen with validation and error alerts.
  - `RegisterOrgPage.tsx`: Workspace onboarding screen with slug generation and admin credentials.
  - `DepartmentsPage.tsx`: Department hierarchy management with creation modal and code validation.
  - `EmployeesPage.tsx`: Employee directory table with search filtering and employee invitation modal.
  - `Topbar.tsx` & `Sidebar.tsx`: Multi-tenant organization switcher, user avatar with roles badge, and navigation integration.
- **Automated Verification:**
  - Cross-tenant IDOR defense, token rotation, and membership isolation tests in `AuthControllerIntegrationTest`.
  - Spring Modulith architectural verification in `NexusOpsModulithTest` (0 module violations).
  - Vitest routing and authenticated session tests in `App.test.tsx` (2/2 passed).

---

## [0.2.0-foundation] - 2026-11-09

### Added
- **Docker Compose Stack:** PostgreSQL 16 + pgvector, Redis 7.2, Kafka 3.8 (KRaft), MinIO, Mailpit, and optional Ollama profile in `infrastructure/docker-compose.yml`.
- **Database Init:** `infrastructure/postgres/init.sql` setting up `vector`, `uuid-ossp`, and `pgcrypto` extensions.
- **Spring Boot Backend Skeleton:**
  - Standard module packages under `com.nexusops`: `identity`, `organization`, `request`, `workflow`, `approval`, `task`, `knowledge`, `ai`, `notification`, `analytics`, `audit`, `platform`, `shared`.
  - Spring Modulith `@ApplicationModule` annotations and verification test (`NexusOpsModulithTest`).
  - Structured logging with MDC correlation IDs (`CorrelationIdFilter`, `logback-spring.xml`).
  - Multi-tenant context filter (`TenantContextFilter`, `TenantContextHolder`).
  - Global REST exception handler (`GlobalExceptionHandler`) and standardized API envelope (`ApiResponse<T>`, `ErrorResponse`).
  - Flyway migration baseline (`V1__init_baseline.sql`).
  - Security configuration (`SecurityConfig`) and actuator health endpoints.
- **React Frontend Skeleton:**
  - React 18, TypeScript, Vite, Tailwind CSS with enterprise styling tokens.
  - Core layout shell: Left navigation `Sidebar`, `Topbar`, slide-over `Nexus AI` assistant drawer, and feature page shells (Dashboard, Requests, Approvals, Tasks, Knowledge, Workflows).
  - Centralized Axios client (`api-client.ts`) with correlation ID and JWT interceptors.
  - Vitest test setup and App smoke test.
- **Documentation:**
  - Added `docs/development/local-setup.md` local development guide.

---

## [0.1.0-design] - 2026-11-09

### Added
- Initialized Git repository and root folder structure per Section 5 of `AGENTS.md`.
- Added `AGENTS.md` containing authoritative architectural directives and non-negotiable constraints.
- Added comprehensive Phase 0 technical design documentation under `docs/` (PRD, System Architecture, Database ERD, Workflow Engine Design, Nexus AI & RAG, Security, API Contracts, Event Catalogue).
- Added `IMPLEMENTATION_PLAN.md` with 12 phased roadmaps and concrete acceptance gates.
- Added `.env.example` and `.gitignore`.
