# NexusOps – Current Project Status

**Current Phase:** Visual Workflow Studio Upgrade & v1.1.0 Enterprise Canvas Release (ALL PHASES COMPLETE & ACCEPTED)  
**Last Updated:** 2026-11-09  
**Branch:** main  
**Milestone:** v1.1.0 Enterprise Canvas & Visual Designer  

---

## Completed in v1.1.0 (Visual Workflow Studio Upgrade & Knowledge Hardening)

1. **Enterprise Visual Canvas Designer ([`@xyflow/react`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-frontend/src/features/workflows/components/WorkflowCanvas.tsx)):**
   - Integrated React Flow (`@xyflow/react`) with interactive node palette, drag-and-drop node placement, custom handle connections, zoom/pan controls, and mini-map.
   - Dual-mode Studio: Seamless toggle between **Visual Canvas** (interactive React Flow editor) and **Inspect Graph** (structured card summaries and transition routing table).
   - Dedicated visual node cards for all 5 core types: `START` (Emerald), `APPROVAL` (Amber), `CONDITION` (Sky), `TASK` (Purple), and `END` (Rose/Emerald) with node key chips, status badges, and selection glow rings.
   - Curved Bezier transitions with dynamic routing pills (`APPROVED`, `REJECTED`, `Amount > $10k & ≤ $50k`, `DEFAULT`, `≤ $10,000`).

2. **Interactive Node Configuration Sidebar ([`NodeConfigSidebar.tsx`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-frontend/src/features/workflows/components/NodeConfigSidebar.tsx)):**
   - Dynamic slide-over drawer for configuring selected nodes or transition edges:
     - **Approval Node:** Approver type (`MANAGER`, `DEPT_HEAD`, `ROLE`, `SPECIFIC_USER`), role selection (`FINANCE`, `OPERATOR`, `ORG_ADMIN`, `HR`), SLA timeout (hours), and approval policy (`SINGLE_APPROVER`, `ALL_MUST_APPROVE`, `MAJORITY`).
     - **Condition Node:** Evaluated field paths (`payload.estimatedAmount`, `payload.deviceModel`, etc.).
     - **Task Node:** Fulfillment queue (`IT_OPERATIONS`, `FACILITIES`, `FINANCE_QUEUE`, `HR_QUEUE`), assignment strategy (`DIRECT_CLAIM`, `ROUND_ROBIN`, `LEAST_LOADED`), SLA duration, and priority (`LOW`, `MEDIUM`, `HIGH`, `URGENT`).
     - **End Node:** Outcome status (`COMPLETED`, `REJECTED`, `CANCELLED`).
     - **Transition Edge:** Default fallback route toggle, typed field path, comparison operator (`EQUALS`, `NOT_EQUALS`, `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`, `BETWEEN`, `IN`, `IS_EMPTY`), comparison value, and evaluation priority order.

3. **Backend-Driven Graph Validation & Immutability ([`WorkflowGraphValidator.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/workflow/application/validator/WorkflowGraphValidator.java), [`WorkflowDefinitionService.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/workflow/application/WorkflowDefinitionService.java)):**
   - Strict graph invariants: exactly one `START`, at least one `END`, no dangling transitions, BFS reachability from START to END, unique node keys, required role/queue configurations, and explicit default fallback routes on condition nodes.
   - Version Immutability: Published versions are strictly read-only in both database and UI. Edits require creating a new Draft version (`POST /api/v1/workflows/{id}/versions`) which clones nodes, transitions, and coordinates from the latest published version.
   - Deterministic condition evaluation via [`TypedConditionEvaluator.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/workflow/application/evaluator/TypedConditionEvaluator.java) supporting compound AND/OR grouping and numeric comparison ranges.

4. **REST API Extensions & Endpoints ([`WorkflowDefinitionController.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/workflow/api/WorkflowDefinitionController.java)):**
   - `GET /api/v1/workflows/{id}/versions`: Version history with publication timestamps and statuses.
   - `POST /api/v1/workflows/{id}/versions`: Create next draft version cloning current topology.
   - `PUT /api/v1/workflows/{id}/versions/{versionNumber}`: Save visual draft coordinates, configurations, and transitions.
   - `POST /api/v1/workflows/{id}/versions/{versionNumber}/validate`: Detailed graph validation returning errors and warnings.
   - `POST /api/v1/workflows/{id}/versions/{versionNumber}/publish`: Strict graph verification and publication with version snapshotting.

5. **Knowledge Base Role Permissions & PDF Ingestion:**
   - Secured Knowledge upload, deletion, and re-indexing behind `KNOWLEDGE_MANAGE` and `ROLE_ORG_ADMIN`.
   - Replaced naive byte reader with Apache PDFBox 3.0.3 (`Loader.loadPDF`) with graceful fallback.

6. **Requests Endpoint Permission & Status Filtering Hardening:**
   - Fixed HTTP 403 Forbidden on the Operations Requests page (`/requests`) when accessed by standard employees (e.g. Alice Chen).
   - In [`RequestController.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/request/api/RequestController.java): Updated `GET /api/v1/requests` to authorize callers with `REQUEST_VIEW_OWN` in addition to administrative roles (`REQUEST_VIEW_ALL`, `ORG_ADMIN_MANAGE`), automatically and safely scoping the response to the caller's own requests if they lack org-wide viewing authority.
   - In [`RequestsPage.tsx`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-frontend/src/features/requests/RequestsPage.tsx): Switched default intake endpoint to `/requests/my` for employees, and dynamically rendered an interactive scope switcher ("My Requests" vs "All Requests") for administrators and managers.
   - Added status filtering (`ALL`, `SUBMITTED`, `IN_REVIEW`, `APPROVED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`) across [`RequestRepository.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/request/infrastructure/RequestRepository.java), [`RequestService.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/request/application/RequestService.java), and [`RequestController.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/request/api/RequestController.java).

7. **Automated Task Creation & Task History Foreign Key Fix:**
   - Fixed `DataIntegrityViolationException` / PostgreSQL foreign key constraint violation (`fk_task_history_actor`) when an approval decision (e.g. Bob Martinez approving `REQ-1001`) automatically advanced the workflow and created an unassigned task (`TASK_PROCUREMENT`).
   - Root cause: [`TaskService.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/task/application/TaskService.java) previously passed `organizationId` as fallback when `assigneeMembershipId` was null, which failed composite foreign key validation on `(organization_id, actor_membership_id)`.
   - Added Flyway migration [`V11__task_history_actor_nullable.sql`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/resources/db/migration/V11__task_history_actor_nullable.sql) allowing `NULL` for system-generated initial task creation events.
   - Updated [`TaskHistoryEntity.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/task/domain/TaskHistoryEntity.java) and [`TaskService.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/task/application/TaskService.java) to set `actorMembershipId = null` for automated transitions, and updated [`IdentityService.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/identity/application/IdentityService.java) to return `"System Automated"` when `membershipId` is null.
   - Upgraded [`TaskController.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/task/api/TaskController.java) `/queue` and [`ApprovalController.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/approval/api/ApprovalController.java) `/pending` to return empty collections gracefully for unauthorized roles rather than HTTP 403.

---

## Completed in v1.0.1 Stability Patch (Knowledge Vector Ingestion, AI Co-Pilot & UI Hardening)

1. **pgvector & Hibernate 6 Type Mapping ([`PgVectorType.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/knowledge/domain/PgVectorType.java) & [`DocumentChunkEntity.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/knowledge/domain/DocumentChunkEntity.java)):**
   - Implemented custom Hibernate `UserType<String>` mapping PostgreSQL pgvector (`Types.OTHER`) with `vector(768)` columns while allowing H2 in-memory test export.
   - Verified automated boot-time indexing of the Acme IT Hardware & Procurement Policy into PostgreSQL pgvector chunks without warnings.
   - All tests passing (`KnowledgeEngineIntegrationTest` 3/3, `EndToEndDemoIntegrationTest` 3/3).

2. **Modern PDF Decompression ([`TextExtractionService.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/knowledge/application/TextExtractionService.java)):**
   - Added `InflaterInputStream` support to uncompress `/FlateDecode` streams in uploaded PDF documents.

3. **Grounded AI RAG & Tool Query Routing ([`AiAssistantService.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/ai/application/AiAssistantService.java), [`SearchMyRequestsTool.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/ai/application/tools/SearchMyRequestsTool.java), [`IntentClassifier.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/ai/application/IntentClassifier.java)):**
   - Calibrated RAG semantic search threshold to `0.25` for deterministic local embeddings, returning full grounded citations and policy paragraphs.
   - Enabled administrative request query fallback so org admins querying requests see active organization requests.
   - Expanded natural language intent matching for requests, approvals, and tasks.

4. **Frontend Hardening & Network Proxy Stability:**
   - Fixed undefined string method calls on request category badges with `getCategoryIcon` fallbacks.
   - Created `RouteErrorBoundary.tsx` connected to application routing.
   - Fixed Axios double-prefixing on Knowledge base endpoints and unwrapped response objects.
   - Configured Vite proxy in `vite.config.ts` to `http://127.0.0.1:8080` to prevent Windows IPv6 connection refused errors.
   - Fixed `apiClient` Axios default header collision with `FormData`: automatically stripped default `'Content-Type': 'application/json'` when sending `FormData` so browser/Axios attaches boundary headers (`multipart/form-data; boundary=----...`).
   - Added explicit `HttpMediaTypeNotSupportedException` and `MaxUploadSizeExceededException` handlers in `GlobalExceptionHandler.java` with structured 415/413 error envelopes.

5. **Rich Markdown Formatting & Conversational AI Chat Memory:**
   - Implemented zero-dependency, secure [`MarkdownRenderer.tsx`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-frontend/src/components/common/MarkdownRenderer.tsx) rendering headings, metadata banners, styled bullet/number lists, bold/italic, code blocks, tables, and blockquotes.
   - Upgraded both [`AiAssistantPage.tsx`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-frontend/src/features/ai-assistant/AiAssistantPage.tsx) and [`AiAssistantDrawer.tsx`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-frontend/src/features/ai-assistant/AiAssistantDrawer.tsx) to render assistant responses with rich typography.
   - Enhanced [`IntentClassifier.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/ai/application/IntentClassifier.java) and [`AiAssistantService.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/ai/application/AiAssistantService.java) with `CONVERSATION_FOLLOW_UP` (recalling and quoting previous answers when asked "what did you say" or "repeat"), `INCOMPLETE_QUERY` (guiding truncated queries like "what is"), and conversational gratitude handling ("thank you", "ok").
   - Added resilient natural language request matching and conversational hardware selection memory: phrases like *"request for a new laptop for me"* or direct hardware model selections (*"Apple MacBook Pro 16..."*) seamlessly generate actionable proposal cards with pre-populated schema parameters instead of misclassifying into policy QA.

6. **Comprehensive Role-Based Access Control (RBAC) & UI Authorization Guards:**
   - **Backend Security:** Enforced `@PreAuthorize` method security across all exposed REST endpoints:
     - `KnowledgeController`: Added `@PreAuthorize("hasAuthority('KNOWLEDGE_MANAGE') or hasAuthority('ROLE_ORG_ADMIN')")` on document upload, deletion, and re-indexing; added `@PreAuthorize("hasAuthority('KNOWLEDGE_VIEW') or hasAuthority('ROLE_ORG_ADMIN')")` on document reading, search, and downloads.
     - `TaskController` & `TaskService`: Added `@PreAuthorize` guards for `TASK_CLAIM`, `TASK_COMPLETE`, `TASK_UPDATE`, `TASK_QUEUE_VIEW`; enforced caller authorization in `claimTask`.
     - `ApprovalController`: Added `@PreAuthorize` guards for `APPROVAL_VIEW_PENDING` and `APPROVAL_EXECUTE`.
     - `AuditLogController` & `AnalyticsController`: Added `@PreAuthorize` guards for `AUDIT_VIEW` and `ANALYTICS_VIEW`.
     - `WorkflowDefinitionController`, `DepartmentController`, `MembershipController`: Fully secured administrative mutations.
   - **Frontend UI & Navigation:**
     - `Sidebar.tsx`: Dynamically filters navigation links based on user roles and permissions (e.g., standard employees only see Dashboard, My Requests, Knowledge Base, and Nexus AI Co-pilot; operators see Tasks; managers/finance see Approvals and Analytics; admins see all).
     - `KnowledgePage.tsx`: Conditionally hides upload modal button, document deletion (trash), and vector re-indexing (refresh) icons for users lacking `KNOWLEDGE_MANAGE`.
     - `TasksPage.tsx`: Hides task claiming and completion buttons for non-operators.
     - `DepartmentsPage.tsx` & `EmployeesPage.tsx`: Hides "Add Department" and "Invite Employee" buttons and modals for non-administrators while preserving directory browsing.
     - `AuditTrailPage.tsx` & `AnalyticsPage.tsx`: Displays dedicated enterprise access-restricted screens if an unauthorized user navigates directly to the route.
   - **Verification:** Verified live via authenticated REST tests: Alice (`alice@acme.com`, Employee) receives HTTP 403 Forbidden when attempting document deletion, task claiming, or audit log viewing; Eve (`eve@acme.com`, Admin) receives HTTP 200 OK. All frontend unit and routing tests passing (2/2).

---

## Completed in Phase 11 (End-to-End Acceptance Demo, Seed Data & Final Hardening)

1. **Automated Demo Data Seeder ([`DemoDataSeeder.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/platform/application/DemoDataSeeder.java)):**
   - Automatically provisions complete commercial demo topologies on boot if the database is unseeded:
     - **Tenant 1: Acme Corporation (`acme-corp`)**:
       - **Personas (Standard Password: `Password123!`):**
         - `eve@acme.com`: Eve Vance (Organization Administrator)
         - `bob@acme.com`: Bob Martinez (Engineering Manager & Dept Head)
         - `alice@acme.com`: Alice Chen (Software Engineer, reports to Bob)
         - `carol@acme.com`: Carol Davis (Finance Manager & Dept Head)
         - `dave@acme.com`: Dave Wilson (IT Operations Specialist & Dept Head)
       - **Departments:** Engineering (`ENG`), Finance (`FIN`), IT Operations (`IT`), Human Resources (`HR`).
       - **Categories:** Developer Laptop (`DEVELOPER_LAPTOP`), Software Access (`SOFTWARE_ACCESS`), Expense Reimbursement (`EXPENSE_REIMBURSEMENT`).
       - **Published Workflow:** `WF_DEVELOPER_LAPTOP` (Start $\to$ Manager Approval $\to$ Condition $> \$10,000$ $\to$ Finance Approval $\to$ IT Task Fulfillment $\to$ End).
       - **Grounded Knowledge Base:** Pre-indexes "Acme Corporation - Global IT Hardware & Procurement Policy (2026)" into pgvector with 768-dimensional embeddings.
       - **Baseline Request:** Pre-populates an initial baseline request for instant operational analytics dashboards.
     - **Tenant 2: Globex Corporation (`globex-corp`)**:
       - Isolated tenant for rigorous cross-tenant security verification.
       - Personas: `homer@globex.com` (Admin), `frank@globex.com` (Employee).
   - In-memory multipart file support ([`ByteArrayMultipartFile.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/platform/application/ByteArrayMultipartFile.java)).

2. **Master End-to-End Acceptance Test Suite ([`EndToEndDemoIntegrationTest.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/test/java/com/nexusops/EndToEndDemoIntegrationTest.java)):**
   - `executeFullCommercialLifecycleAcceptanceDemo`:
     - Evaluator logs in as Alice (`alice@acme.com`) $\to$ submits \$25,000 laptop purchase request.
     - Evaluator logs in as Bob (`bob@acme.com`) $\to$ inspects pending review inbox $\to$ approves request.
     - Evaluator logs in as Carol (`carol@acme.com`) $\to$ conditional branch triggers finance threshold review ($> \$10,000$) $\to$ approves request.
     - Evaluator logs in as Dave (`dave@acme.com`) $\to$ claims IT procurement fulfillment task $\to$ completes task with serial tracking.
     - Request transitions to `COMPLETED`.
     - Evaluator logs in as Eve (`eve@acme.com`) $\to$ reviews tamper-evident append-only audit trail and live operational analytics KPIs.
   - `executeNexusAiAcceptanceCapabilities`:
     - RAG policy QA returning grounded citations with document title and page/section reference.
     - Governed AI tool execution synthesizing executive department reports from pre-computed SQL aggregations.
   - `verifyCrossTenantIsolationStrictness`:
     - Frank (`frank@globex.com`) forbidden from accessing Acme requests (HTTP 404 IDOR defense).
     - Frank sees 0 pending approvals and 0 open tasks.
     - Frank queries Nexus AI $\to$ 0 Acme documents returned.
   - **Backend Verification:** **46/46 tests passed (0 failures, 0 errors, 100% clean Spring Modulith architecture).**

3. **Frontend Evaluator Polish & 1-Click Persona Switcher:**
   - [`LoginPage.tsx`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-frontend/src/features/auth/LoginPage.tsx): Added an interactive 1-Click Evaluator Personas grid with role badges, allowing evaluators to switch seamlessly between Alice, Bob, Carol, Dave, Eve, and Frank with single-click authentication.
   - Production bundle compiled (`tsc && vite build` in 9.49s).
   - Vitest suite passing (2/2 tests passed).

4. **Comprehensive Documentation & Evaluator Walkthrough:**
   - [`docs/operations/demo-acceptance-walkthrough.md`](file:///c:/Users/yuvra/Desktop/NexusOps/docs/operations/demo-acceptance-walkthrough.md): Comprehensive scripted walkthrough detailing credentials, step-by-step UI flows, API interactions, and automated acceptance test commands.

---

## Overall Project Milestone Status (All Phases Complete)

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
| **Phase 10** | Operational Analytics, SLA Monitoring & Department Reporting | **DONE** (`phase-10`) |
| **Phase 11** | End-to-End Acceptance Demo, Seed Data & Final Hardening | **DONE** (`phase-11`, `v1.0.0`) |
