# NexusOps – Changelog

All notable changes to the NexusOps platform will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
