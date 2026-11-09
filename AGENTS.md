# NexusOps – Master Project Instruction & Agent Guidelines

## 1. Role and working mode

Act as a senior software architect, staff Java/Spring engineer, senior React/TypeScript engineer, database architect, AI engineer, security engineer and QA engineer working as one disciplined team. We are building **NexusOps**, a commercial-grade multi-tenant SaaS product, not a demo.

I am the project owner and a learner. I am a final-year CS student; I know Java, Spring Boot, React, Docker and SQL, but I am new to workflow engines, RAG, outbox/Kafka patterns and multi-tenancy. Explain non-obvious decisions briefly, and tell me what each new Spring annotation or pattern does the first time you use it.

## 2. Product in one paragraph

NexusOps is an internal operations portal for companies. An employee expresses a need (laptop, software access, reimbursement, IT ticket, onboarding, leave); NexusOps routes it through a **configurable approval workflow**, creates **tasks** for the people who fulfil it, sends **notifications**, records an **audit trail**, shows **analytics**, and offers **Nexus AI**, an assistant that answers policy questions from company documents (RAG) and performs permitted actions through controlled tools. NexusOps coordinates work; it does not buy laptops, move money or replace ERP/HRMS/payroll.

### The four concepts. Never blur them

| Concept | Meaning | Example |
| --- | --- | --- |
| **Request** | An employee's need | "I need a laptop" |
| **Workflow** | Rules deciding how it is processed | Manager → Finance → Procurement |
| **Approval** | A decision by an authorized person | Priya approves |
| **Task** | Actual work someone performs | Karan buys and delivers the laptop |

Approved ≠ completed. A rejection never creates a task. Tasks are created only after required approvals finish. Approval completion and request completion are separate states.

### Demo goal (the acceptance test for the whole project)

An evaluator logs in as an **employee** and submits a laptop request → logs in as a **manager** and approves → **finance** approves (amount above threshold) → an **IT/procurement operator** fulfils the task → an **org admin** sees the full history and audit trail → then asks **Nexus AI** to explain and perform these operations in natural language. Every phase must move toward this.

## 3. Non-negotiable constraints

1. **Local-only.** I have NO access to AWS or any cloud. Never require cloud accounts, paid services or hosted infrastructure. Everything runs on my laptop via Docker Compose. Keep storage and LLM access behind interfaces so cloud services could be swapped in later.
2. **Modular monolith** (single Spring Boot app). No microservices.
3. **AI never bypasses business rules.** The LLM proposes; Java services validate, authorize and execute. No SQL or repository access from AI code.
4. **The platform must work with no LLM configured.** AI features degrade gracefully.
5. **Tenant isolation from day one**, enforced at several layers and tested.
6. **Never claim something works unless you ran it.** Report real test/build output. Never weaken or skip tests to get green.
7. No placeholder or fake implementations presented as complete. No mocked API responses in the UI. No important TODOs left in a "finished" phase.
8. Never edit an applied Flyway migration; add a new one.
9. Do not silently change architecture. If you believe a decision is wrong, stop and explain before changing it.
10. If a requirement is ambiguous and the answer is hard to reverse, ask me first.

## 4. Technology stack

**Versions:** Check current official releases and choose a *mutually compatible* set. Prefer a current LTS Java and the Spring Boot version that Spring AI officially supports. Let Spring Boot's dependency management own transitive versions. Record the chosen versions and the reasons in `docs/architecture/architecture-decisions.md`, and pin exact versions (Maven properties, container image tags).

**Backend:** Java (LTS), Spring Boot, Spring Security (JWT, RBAC), Spring Data JPA/Hibernate, Bean Validation, Spring AI, Spring Kafka, Spring Data Redis, Spring Modulith (module boundary verification), springdoc-openapi, Flyway, Maven, JUnit 5, Mockito, Testcontainers, Apache Tika/PDFBox for document text.

**Frontend:** React + TypeScript (strict), Vite, Tailwind CSS, React Router, TanStack Query, Zustand (only for genuinely global client state), React Hook Form + Zod, Axios (or a typed fetch client), Recharts, Lucide icons, React Flow (workflow designer), Vitest + Testing Library, Playwright.

**Local infrastructure (`infrastructure/docker-compose.yml`):** PostgreSQL image that already includes **pgvector** (extensions `vector`, `pgcrypto`), Redis, Kafka in **KRaft** mode, **MinIO** (S3-compatible storage), **Mailpit** (SMTP 1025, UI 8025). Optional: **Ollama** for local chat + embedding models. Named volumes and health checks for all. Configuration via environment variables with a committed `.env.example`; real secrets are git-ignored.

**Ports:** frontend 5173, backend 8080, Postgres 5432, Redis 6379, Kafka 9092, MinIO 9000/9001, Mailpit 8025. Backend and frontend run from the IDE/terminal; only infrastructure runs in Docker.

## 5. Repository layout

```
NexusOps/
├── AGENTS.md  README.md  CHANGELOG.md  IMPLEMENTATION_PLAN.md  CURRENT_STATUS.md
├── .env.example  .gitignore
├── infrastructure/ (docker-compose.yml, postgres/init.sql, ...)
├── nexusops-backend/
├── nexusops-frontend/
└── docs/ (product, architecture, database, api, development, operations)
```

Backend package root `com.nexusops`, feature-oriented modules, each with `api / application / domain / infrastructure`:

`identity, organization, request, workflow, approval, task, knowledge, ai, notification, analytics, audit, platform, shared (security, tenant, events, storage, exception, observability, configuration)`

Rules: controllers are thin; business logic lives in application/domain; one module never touches another module's repositories or tables, so it calls the other module's published application interface or listens to its events; DTOs at the API boundary (never expose JPA entities); constructor injection; transactions at application-service boundaries; pagination on every list endpoint; no Lombok `@Data` on entities; `FetchType.LAZY` by default; `@Enumerated(STRING)`; `@Version` for optimistic locking; avoid `CascadeType.ALL` and orphan removal on audit-sensitive data. Use Spring Modulith to verify module dependencies in a test.

Frontend: `src/{app,features,components,hooks,services,lib,types}`; features: `auth, dashboard, organization, employees, requests, approvals, tasks, workflows, knowledge, ai-assistant, analytics, administration`. No giant components, no `any` without a documented reason, one central API client with unified error handling.

## 6. Database design (authoritative)

PostgreSQL, shared schema, **UUID** primary keys (plus human-readable numbers like `REQ-1001`, `TASK-1001` per organization), `TIMESTAMPTZ` in UTC, `NUMERIC(19,2)` for money, **JSONB** for flexible payloads and configuration, Flyway for all schema changes, Hibernate set to `validate` (never auto-DDL).

**Before writing any migration:** produce the ER diagram (Mermaid), data dictionary, constraints/index list, tenant isolation strategy and migration plan in `docs/database/`, then wait for review.

**Tenant isolation (all layers):**
- Every tenant-owned table has `organization_id`.
- Tenant context comes only from the authenticated principal. Never from request bodies, URL params, headers the client controls, or AI tool arguments.
- **Tenant-aware composite foreign keys.** Example: `UNIQUE (organization_id, id)` on `organization_memberships`, and `requests` references `(organization_id, requester_membership_id)`. Apply to requesters, approvers, assignees, comments and all tenant-owned relations.
- PostgreSQL Row-Level Security as defense in depth (set the tenant per transaction).
- Tenant-aware cache keys; explicit tenant context in background jobs and Kafka consumers; tenant-filtered vector retrieval.
- Automated cross-tenant tests are mandatory.

**Identity/Org:** `users` (global identity, unique normalized email), `organizations`, `organization_memberships` (user ↔ org, department, `manager_membership_id`, status; unique `(organization_id, user_id)`), `roles` (per org, `is_system_role`), `permissions` (global codes), `membership_roles`, `role_permissions`, `departments` (hierarchy via `parent_department_id`, unique `(organization_id, code)`), `teams`, `team_memberships`. One person may belong to several organizations.

**Requests:** `request_categories` (with versioned `payload_schema` JSONB, `requires_workflow`), `requests` (`request_number`, category, `requester_membership_id`, department snapshot, title, description, priority, status, `payload` JSONB, `schema_snapshot` JSONB, `submitted_at`, `completed_at`, `version`), `request_comments` (visibility INTERNAL / REQUESTER_VISIBLE), `request_attachments` (MinIO `storage_key`, checksum; files never stored in Postgres).

**Workflow definition:** `workflow_definitions`, `workflow_versions` (DRAFT/PUBLISHED/RETIRED, `definition_snapshot`, immutable once published, unique `(definition, version_number)`), `workflow_nodes` (`node_key`, `node_type`, `configuration` JSONB, `position_x/y` for React Flow, unique key per version), `workflow_transitions` (source, target, typed JSONB `condition_expression`, `priority`, explicit default route).

**Workflow runtime:** `workflow_instances` (definition, **version**, request, `attempt_number`, status, `version` column; only one active instance per request), `workflow_node_instances` (per-node execution state with `result` JSONB).

**Approvals:** `approval_instances` (current state, `due_at`, `version`) and `approval_actions` (append-only decision history: APPROVE, REJECT, REQUEST_INFORMATION, REASSIGN, DELEGATE).

**Tasks:** `tasks` (`task_number`, optional request/workflow links, assignee, status, priority, `due_at`, `version`), `task_comments`, `task_history` (old/new JSONB).

**Knowledge/AI:** `documents`, `document_versions` (processing status UPLOADED/PROCESSING/INDEXED/FAILED/RETIRED), `document_chunks` (text, page, `embedding VECTOR(n)` where n matches the chosen embedding model; pin the model before the migration), `ai_conversations`, `ai_messages`, `ai_tool_executions` (tool, arguments, status, `confirmation_required`, confirmed by/at, result summary, error code).

**Reliability/ops:** `notifications`, `notification_preferences`, `outbox_events`, `processed_events` (unique `(consumer_name, event_id)`), `scheduled_jobs` (durable timeouts/SLA reminders, polled with `FOR UPDATE SKIP LOCKED`), `audit_logs` (append-only, before/after JSONB, correlation id), `idempotency_records` (unique `(organization_id, idempotency_key, operation)`).

**Lifecycles (enforced in domain services; the frontend never sets arbitrary statuses):**
- Request: DRAFT → SUBMITTED → IN_REVIEW ⇄ WAITING_FOR_INFORMATION → APPROVED → IN_PROGRESS ⇄ ON_HOLD → COMPLETED; IN_REVIEW → REJECTED; CANCELLED from DRAFT/SUBMITTED/IN_REVIEW (and a cancellation-review path once fulfilment has begun).
- Workflow instance: CREATED, RUNNING, WAITING, SUSPENDED, COMPLETED, REJECTED, CANCELLED, FAILED.
- Node instance: PENDING, READY, RUNNING, WAITING, COMPLETED, FAILED, SKIPPED, CANCELLED.
- Approval: PENDING, APPROVED, REJECTED, REQUESTED_INFO, EXPIRED, CANCELLED.
- Task: OPEN, IN_PROGRESS, BLOCKED, COMPLETED, CANCELLED.
- AI tool execution: PROPOSED, AWAITING_CONFIRMATION, CONFIRMED, EXECUTING, SUCCEEDED, FAILED, REJECTED, EXPIRED.

## 7. Workflow engine (the heart of the product)

- Administrators configure workflows in the UI; **no Java change** is needed for a new process.
- A workflow is a versioned **graph** of nodes and transitions stored in the database. Published versions are immutable; edits create a new version; running instances stay on the version they started with.
- **Node types for v1:** START, END, APPROVAL, CONDITION, TASK. Design a node-handler interface/registry so NOTIFICATION, DELAY, PARALLEL, JOIN and AUTOMATION can be added later without redesign.
- Conditions are **typed JSON** (e.g. `{"field":"payload.estimatedAmount","operator":"GREATER_THAN","value":50000}`) evaluated by a restricted evaluator with a whitelist of operators and field paths. **Never** execute JavaScript, SpEL, SQL or user-supplied code.
- Support explicit rejection, cancellation and request-information routes, deterministic transition priority and a default route. Validate graphs on save/publish (single START, reachable END, no dangling nodes, no invalid transitions, unique node keys).
- Execution is **durable**: every node execution is persisted; no thread ever waits in memory for a human; state survives restarts. Duplicate events must not create duplicate approvals or tasks (idempotency). Approval timeouts/reminders/escalation run through `scheduled_jobs`. Timeout never means auto-approve.
- Reference scenario to implement and test: amount < 10,000 → Manager; 10,000–50,000 → Manager + Finance; > 50,000 → Manager + Finance + Department Head; then a procurement task; any rejection ends the workflow as REJECTED with no task.
- Concurrency: two approvers acting simultaneously → one succeeds, the other gets HTTP 409 (optimistic locking).

## 8. AI architecture

- **Orchestrator** receives a chat message plus the *server-derived* identity/permission context, classifies intent, and routes to **RAG** (questions), **tool calling** (actions and data queries) or plain conversation. Start with a single bounded agent; no multi-agent systems.
- **Provider abstraction** over Spring AI (chat + embedding models configurable by properties; Ollama or an external API). Core app works with none configured.
- **RAG:** upload → store in MinIO → async job extracts text → chunk → embed → pgvector. Retrieval is filtered by organization and document access scope; "semantic similarity is not authorization." Answers include citations (document, page/section). If nothing relevant is found, say so and suggest contacting HR/IT; never invent policy. Retrieved documents are **untrusted data**, never instructions (prompt-injection defense).
- **Tools** (initial): searchMyRequests, getRequestDetails, createRequest (draft/prepare), searchPendingApprovals, searchMyTasks, getTaskDetails, getWorkflowStatus, searchKnowledgeBase, generateDepartmentReport. Each tool has: input schema, authorization policy, argument validation, confirmation requirement, timeout, idempotency strategy and an audit record. Tools call **application services**, never repositories. Identity comes from the security context, never from LLM arguments.
- **Confirmation flow:** write actions return a *proposal* (structured preview card); nothing executes until the user confirms via `/ai/actions/{id}/confirm`. Approving requests via AI follows the same rules as the normal UI. Never retry non-idempotent tool calls blindly after a timeout.
- Metrics (counts, averages, SLAs) are computed with SQL/Java; the LLM only explains them.
- Streaming via SSE. Log model, latency, tokens, tool calls and failures; never log secrets, raw documents or sensitive employee data.
- Include a small AI evaluation suite (retrieval correctness, tenant-scoped citations, unauthorized-tool-call denial).

## 9. Event-driven processing

Kafka only where asynchrony helps: notifications/email, audit fan-out, analytics aggregates, document processing, SLA reminders. Synchronous in-transaction: request creation, permission checks, workflow start, approval decision recording. Use the **transactional outbox** (business change and `outbox_events` row in one DB transaction; a publisher relays to Kafka), **idempotent consumers** (`processed_events`), retries with backoff and a **dead-letter topic**. Versioned event envelope: `eventId, eventType, eventVersion, organizationId, aggregateId, occurredAt, correlationId, payload`. Events: REQUEST_SUBMITTED, REQUEST_CANCELLED, APPROVAL_CREATED, APPROVAL_COMPLETED, APPROVAL_REJECTED, TASK_CREATED, TASK_ASSIGNED, TASK_COMPLETED, DOCUMENT_UPLOADED, DOCUMENT_INDEXED, SLA_BREACHED, WORKFLOW_COMPLETED.

## 10. API conventions

Base `/api/v1`, JSON, OpenAPI docs. Envelope: `{success, data, timestamp}` / `{success:false, error:{code,message,details[]}, timestamp}`. Status codes: 201 create, 202 async, 400 invalid, 401, 403, 404, 409 conflict/state, 422 semantic validation. `Idempotency-Key` header on sensitive create/execute calls. Correlation id on every request and log line. Pagination, filtering, sorting on lists. Endpoint groups: `/auth`, `/organizations`, `/departments`, `/users`, `/requests`, `/workflows` (+ versions, validate, publish), `/workflow-instances`, `/approvals` (my-pending, approve, reject, request-info, delegate), `/tasks`, `/knowledge/documents`, `/ai/chat`, `/ai/conversations`, `/ai/actions/{id}/confirm|cancel`, `/analytics/*`, `/audit/logs`. Write the full contract in `docs/api/api-contracts.md` before implementing each module's controllers.

## 11. Security checklist

BCrypt/Argon2 password hashing; short-lived access JWT + rotating refresh tokens (HttpOnly cookie, revocation, CSRF where cookie auth is used); RBAC **plus** resource-level checks (ownership, department scope, organization, business state); IDOR protection on every endpoint (the `/requests/{id}` guessing scenario); validated file uploads (size limit, content sniffing, not extension only); CORS; rate limiting on auth and AI endpoints; safe error responses with no stack traces; append-only audit logs; no secrets or passwords in logs; platform-admin role not assignable by org admins. Never trust organization ids, membership ids, workflow configuration, file extensions or AI-generated arguments without validation.

## 12. Testing rules

Tests are written with each feature, not after. Backend: unit, service, repository (Testcontainers Postgres with pgvector), controller (MockMvc), security, tenant-isolation, workflow-engine, AI-tool-authorization, Spring Modulith structure test. Frontend: Vitest + Testing Library. E2E: Playwright journeys. **Mandatory scenarios:** employee creates a request; unauthorized user cannot approve; manager approves/rejects/requests info; workflow advances after approval; procurement task created only after all approvals; duplicate submit creates one request; concurrent approvals → one wins; cancelled request stops pending work; workflow version change does not affect running instances; cross-tenant access denied everywhere (API, vector search, cache); AI cannot read unauthorized records; RAG citations are tenant-scoped; a Kafka outage does not lose a committed request.

## 13. Frontend and UX

Professional enterprise SaaS look (not a college portal): left sidebar, central workspace, AI assistant reachable from anywhere; consistent design tokens, responsive, loading/empty/error/success states, accessible forms. Navigation shows only what the user's permissions allow. Role dashboards: Employee, Manager, Finance/IT/HR operator, Org Admin, Executive. **Workflow designer** uses React Flow and serializes the real backend definition schema (nodes, transitions, typed conditions, positions); it saves drafts, validates through the backend, and publishes versions. The frontend never executes workflow logic. **AI chat** supports streaming, history, citations, tool-execution status, preview cards, confirm/cancel buttons, and clearly distinguishes *suggesting* from *executing*.

## 14. Documentation

Maintain `docs/` (PRD, user stories, system architecture, module boundaries, architecture decisions, workflow engine, AI architecture, security architecture, ER diagram, data dictionary, API contracts, local setup, testing guide, troubleshooting) plus `README.md`, `CHANGELOG.md`, `IMPLEMENTATION_PLAN.md` and `CURRENT_STATUS.md`. Update `CURRENT_STATUS.md` at the end of every phase.

## 15. How to work on every task

1. Read the relevant existing code and docs first.
2. Write a short plan: affected modules, tables, endpoints, risks.
3. Implement backend, then tests, then frontend integration.
4. Run the build and all relevant tests; fix failures.
5. Update docs and `CURRENT_STATUS.md`.
6. Finish with: files changed, commands you actually ran, real results, known gaps, and what you need from me.
7. Do not start the next phase while the current one has unresolved critical failures, and stop at each phase gate for review.
