# NexusOps – Master Implementation Plan & Phase Roadmaps

**Project:** NexusOps – Intelligent Enterprise Operations Platform  
**Target Architecture:** Java 21 / Spring Boot 3.3.x Modular Monolith + React 18 / Vite / TS  
**Current Phase:** Phase 0 (Design Only – Under Review)  

---

## Phase Overview & Progress Tracker

| Phase | Title | Focus Area | Status |
| :---: | :--- | :--- | :---: |
| **0** | **Design & Architecture** | PRD, Architecture, ERD, Schema, State Machines, API, Events | **COMPLETED (Pending Review)** |
| **1** | **Foundation** | Docker Compose, Backend skeleton, Modulith test, Frontend shell | Up Next |
| **2** | **Identity & Organization** | Auth, JWT, Org/Dept hierarchy, Composite FKs, RLS, RBAC | Scheduled |
| **3** | **Request Management** | Dynamic schemas, Request intake, Attachments, Comments, MinIO | Scheduled |
| **4** | **Workflow Engine** | Node handlers, Typed condition evaluator, Durable execution | Scheduled |
| **5** | **Approvals** | Approver resolution, Inbox, Optimistic locking (409), Escalations | Scheduled |
| **6** | **Tasks** | Queue dispatching, Operator fulfillment, Complete lifecycle E2E | Scheduled |
| **7** | **Events & Notifications** | Transactional Outbox, Kafka KRaft, Mailpit SMTP, In-app alerts | Scheduled |
| **8** | **Knowledge & RAG** | MinIO upload, Async Tika chunking, pgvector index, Scoped search | Scheduled |
| **9** | **Nexus AI** | Spring AI, Governed tools, Two-phase confirm flow, SSE streaming | Scheduled |
| **10**| **Analytics & Audit** | SQL KPIs, Role dashboards, Append-only audit trail with diffs | Scheduled |
| **11**| **Visual Workflow Designer** | React Flow canvas, Backend graph validation, Version publish | Scheduled |
| **12**| **Hardening & Demo** | E2E Playwright tests, Seed demo scenarios, Final documentation | Scheduled |

---

## Detailed Phase Breakdown & Acceptance Gates

### Phase 0: Design Only (No Application Code)
- **Deliverables:**
  - `AGENTS.md` at repository root.
  - `docs/product/prd-and-user-stories.md`
  - `docs/architecture/system-architecture.md`
  - `docs/architecture/architecture-decisions.md`
  - `docs/database/er-diagram-and-schema.md`
  - `docs/architecture/workflow-engine-design.md`
  - `docs/architecture/ai-rag-and-tools.md`
  - `docs/architecture/security-architecture.md`
  - `docs/api/api-contracts.md`
  - `docs/architecture/event-catalogue.md`
  - Folder skeleton, `.gitignore`, `.env.example`, `IMPLEMENTATION_PLAN.md`, `CURRENT_STATUS.md`.
- **Acceptance Criteria:**
  - Architecture has zero unaddressed contradictions across Request vs Approval vs Task.
  - Tenant isolation strategy covers API, service, database composite FKs, RLS, vector store, and cache.
  - Review gate completed and approved by Project Owner.

---

### Phase 1: Project Foundation
- **Deliverables:**
  - `infrastructure/docker-compose.yml`: PostgreSQL + pgvector, Redis, Kafka KRaft, MinIO, Mailpit with health checks and named volumes.
  - `nexusops-backend`: Spring Boot 3.3.x with module package structure, Spring Modulith verification test, Flyway migration baseline, global exception handler, standard API envelope, structured logging with correlation IDs.
  - `nexusops-frontend`: Vite + React + TypeScript + Tailwind CSS skeleton with routing, design tokens, Axios/typed client, and base layout shell.
  - `docs/development/local-setup.md`: Step-by-step developer guide.
- **Verification Commands:**
  - `docker compose -f infrastructure/docker-compose.yml up -d`
  - `cd nexusops-backend && ./mvnw clean verify` (or `mvnw.cmd`)
  - `cd nexusops-frontend && npm test && npm run build`

---

### Phase 2: Identity & Organization Management
- **Deliverables:**
  - Migrations for `users`, `organizations`, `organization_memberships`, `roles`, `permissions`, `membership_roles`, `role_permissions`, `departments`, `teams`.
  - Auth services: registration, login, JWT issuance, rotating refresh tokens in HttpOnly cookies, logout, `/auth/me`, `/auth/switch-org`.
  - Organization & department management APIs.
  - Frontend: Auth pages (login, registration), Organization settings, Department tree, Employee directory.
- **Acceptance Criteria:**
  - Tenant context derived exclusively from JWT.
  - Automated tests verify cross-tenant data access is strictly forbidden at API and database layers.

---

### Phase 3: Request Intake & Management
- **Deliverables:**
  - `request_categories` with versioned `payload_schema` JSONB.
  - Request creation, draft, submission with idempotency header support.
  - Human-readable numbers (`REQ-1001`).
  - MinIO file attachment upload and streaming download.
  - Internal vs requester-visible comments.
  - Frontend: Dynamic category form renderer, My Requests list, Request detail view with timeline.
- **Acceptance Criteria:**
  - Dynamic JSON payload strictly validated against category JSON schema.
  - IDOR protection tests verify users cannot read other tenants' or other employees' private requests.

---

### Phase 4: Workflow Engine
- **Deliverables:**
  - Graph definition and runtime tables (`workflow_definitions`, `workflow_versions`, `workflow_nodes`, `workflow_transitions`, `workflow_instances`, `workflow_node_instances`).
  - Node handler registry: `START`, `END`, `APPROVAL`, `CONDITION`, `TASK`.
  - Typed condition evaluator with mathematical/logical operators.
  - Graph validation service.
  - Seeding reference laptop purchase workflow ($10k / $50k thresholds).
- **Acceptance Criteria:**
  - Engine tests prove all branches, rejection termination, restart durability, and version isolation pass without failure.

---

### Phase 5: Approval Engine
- **Deliverables:**
  - `approval_instances` and `approval_actions`.
  - Approver resolution: direct manager, department head, role queue.
  - Approval inbox: approve, reject, request-info, delegate.
  - Deadlines and reminders via `scheduled_jobs` (`FOR UPDATE SKIP LOCKED`).
  - Frontend: Approver inbox, decision modal, information-request loop.
- **Acceptance Criteria:**
  - Concurrent approval actions trigger HTTP 409 Conflict.
  - Rejection terminates workflow instance and transitions request to `REJECTED` (no tasks created).

---

### Phase 6: Task Management & End-to-End Operational Lifecycle
- **Deliverables:**
  - `tasks`, `task_comments`, `task_history`.
  - Task assignment to operator queues and individuals.
  - Task status transitions (`OPEN`, `IN_PROGRESS`, `BLOCKED`, `COMPLETED`).
  - Frontend: Operator queue views, task claim, task completion form.
- **Acceptance Criteria:**
  - Full end-to-end laptop purchase scenario executes successfully: Employee submits → Manager approves → Finance approves → IT fulfills → Request COMPLETED.

---

### Phase 7: Events, Outbox & Kafka
- **Deliverables:**
  - Transactional outbox table and background poller/relay.
  - Kafka KRaft producers and consumers for core domain events.
  - `processed_events` table for idempotent consumer delivery.
  - In-app SSE notification feed and Mailpit email delivery.
- **Acceptance Criteria:**
  - Kafka down scenario: Submissions succeed, outbox buffers events, relays upon broker recovery.

---

### Phase 8: Knowledge Base & Tenant RAG
- **Deliverables:**
  - Document upload to MinIO, version tracking.
  - Async text extraction (Apache Tika/PDFBox), chunking (500 tokens), pgvector embeddings.
  - Tenant-scoped cosine similarity vector search with citations.
  - Frontend: Document management UI with upload, indexing status, and deletion.
- **Acceptance Criteria:**
  - Cross-tenant vector search tests guarantee 0% data leakage across tenants.

---

### Phase 9: Nexus AI Operations Assistant
- **Deliverables:**
  - Provider abstraction (Ollama / OpenAI / none).
  - Intent classification (RAG vs Tool vs Chit-Chat).
  - Governed tools registry (calling application services only).
  - Two-phase confirmation flow for mutating actions (`ai_tool_executions`).
  - Frontend: Streaming chat drawer, citations, interactive proposal cards.
- **Acceptance Criteria:**
  - Write actions cannot execute autonomously without explicit user confirmation.
  - Application operates normally with full functionality when no LLM is configured.

---

### Phase 10: Operational Analytics, Audit & Administration
- **Deliverables:**
  - SQL-based aggregation queries for KPIs, cycle times, bottleneck heatmaps.
  - Append-only audit log browser with before/after JSON diffs.
  - Role and permission management screens.
  - Minimal platform admin area.
- **Acceptance Criteria:**
  - Zero raw SQL executed by AI; AI insights strictly narrate pre-computed SQL aggregations.

---

### Phase 11: Visual Workflow Designer
- **Deliverables:**
  - React Flow graph studio.
  - Node drag-and-drop, connection dragging, condition modal.
  - Real-time backend validation and version publishing.
- **Acceptance Criteria:**
  - An administrator creates a brand new workflow in the UI, publishes it, and runs a request through it with zero code changes.

---

### Phase 12: Hardening, Polish & Demo Readiness
- **Deliverables:**
  - Full Playwright E2E test suite.
  - Security review against the AGENTS.md checklist.
  - Seed demo data (2 tenants: Acme Corp & Globex, multiple departments, users, sample documents).
  - Scripted demo walkthrough and complete documentation.
- **Acceptance Criteria:**
  - Flawless execution of the acceptance demo test outlined in `AGENTS.md`.

