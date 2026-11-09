# NexusOps – Current Project Status

**Current Phase:** Phase 3 – Request Management & Submission Engine Completed (Review Gate Active)  
**Last Updated:** 2026-11-09  
**Branch:** master  

---

## Completed in Phase 3 (Request Management & Dynamic Submission Engine)

1. **Database Schema & Migrations (Flyway):**
   - [`V4__request_schema.sql`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/resources/db/migration/V4__request_schema.sql):
     - `tenant_sequences`: Atomic tenant-scoped counters (`(organization_id, sequence_name)`) for thread-safe sequential human-readable numbers (`REQ-1001`, `REQ-1002`, `TASK-1001`) with zero race conditions.
     - `request_categories`: Configurable request categories with JSON Schema (`payload_schema` JSONB), icons, and workflow requirement flags.
     - `requests`: Core table capturing requests with composite foreign keys `(organization_id, requester_membership_id)`, department snapshots, priority, lifecycle status, dynamic payload JSONB, schema snapshot JSONB, and `@Version` optimistic locking.
     - `request_comments`: Threaded comments with visibility separation (`INTERNAL` vs `REQUESTER_VISIBLE`).
     - `request_attachments`: MinIO storage metadata references, file checksums, and mime-type descriptors.
     - Composite unique indexes and tenant foreign key constraints strictly enforced.

2. **Backend Architecture & Domain Services (`com.nexusops.request`):**
   - **Domain Model:**
     - Entities: `RequestEntity`, `RequestCategoryEntity`, `RequestCommentEntity`, `TenantSequenceEntity`.
     - Enums: `RequestStatus` (DRAFT, SUBMITTED, IN_REVIEW, WAITING_FOR_INFORMATION, APPROVED, IN_PROGRESS, ON_HOLD, COMPLETED, REJECTED, CANCELLED), `RequestPriority` (LOW, MEDIUM, HIGH, URGENT), `CommentVisibility` (INTERNAL, REQUESTER_VISIBLE).
   - **Application Services:**
     - `RequestCategoryService`: Category CRUD with JSON Schema validation for form fields, and automated bootstrapping of standard enterprise categories (`DEVELOPER_LAPTOP`, `SOFTWARE_ACCESS`, `EXPENSE_REIMBURSEMENT`).
     - `RequestService`: Atomic `REQ-1001` generation, dynamic JSON Schema validation, lifecycle transitions, requester department snapshotting, cancellation guardrails, IDOR protection, and internal vs requester-visible comments.
   - **Module Boundary & Modulith Integration:**
     - Added `@NamedInterface` package annotations to expose application services cleanly across modules.
     - Enhanced `JwtTokenProvider` and `UserPrincipal` with verified `membershipId` claim, avoiding database queries on security context operations.
   - **REST Controllers & API Contracts:**
     - `RequestCategoryController` (`/api/v1/request-categories`): Active category listing and category definition.
     - `RequestController` (`/api/v1/requests`): Paginated request search with status, priority, and text filters; request submission; request inspection (`/requests/{id}`); request cancellation (`/requests/{id}/cancel`); and request activity/comments (`/requests/{id}/comments`).

3. **Multi-Tenant & Security Integration Testing:**
   - [`RequestControllerIntegrationTest.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/test/java/com/nexusops/RequestControllerIntegrationTest.java):
     - Verified category auto-seeding for new organizations.
     - Verified thread-safe sequential numbering (`REQ-1001`, `REQ-1002`).
     - Verified dynamic payload schema validation (fails with HTTP 422 if required schema fields are missing).
     - Verified comment visibility scoping (internal comments hidden from general requesters).
     - Verified cross-tenant IDOR defense (Tenant B cannot read or cancel Tenant A's requests; direct queries return HTTP 404).
   - **Backend Test Suite Results:**
     - `NexusOpsModulithTest`: 1 passed (0 architectural violations)
     - `AuthControllerIntegrationTest`: 5 passed
     - `RequestControllerIntegrationTest`: 4 passed
     - `SystemHealthControllerTest`: 1 passed
     - **Total:** 11 passed, 0 failures, 0 errors (`BUILD SUCCESS`).

4. **Frontend Implementation (`nexusops-frontend`):**
   - **TypeScript Contracts (`src/types/api.ts`):** Added `RequestResponseDto`, `RequestCategoryResponseDto`, `RequestCommentResponseDto`, `CreateRequestDto`, `AddCommentDto`, `RequestPriority`, `RequestStatus`, `CommentVisibility`.
   - **Operations Requests Screen ([`RequestsPage.tsx`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-frontend/src/features/requests/RequestsPage.tsx)):**
     - Status tab filters (`All`, `Submitted`, `In Review`, `Approved`, `In Progress`, `Completed`, `Cancelled`) and real-time search.
     - Interactive Request Table with priority badges, status indicators, and department snapshots.
     - **Two-Step New Request Wizard:**
       - Step 1: Category selection cards with custom icons and descriptions.
       - Step 2: Dynamic form dynamically generated from the category's `payloadSchema` JSON (supports text, number, enum select dropdowns, and required-field validation).
     - **Request Detail Drawer:**
       - Comprehensive request details, requester metadata, and structured payload attribute cards.
       - Cancellation action with confirmation for pending requests.
       - Comment and activity timeline with toggle for `INTERNAL` notes (operators and approvers only) vs `REQUESTER_VISIBLE` updates.
   - **Frontend Test & Build Results:**
     - Vitest: 2/2 tests passed.
     - Production bundle: `tsc && vite build` completed cleanly with zero TypeScript errors.

---

## Active Phase Gate: Phase 3 Review
- Phase 3 (Request Management & Submission Engine) is 100% complete and verified against all criteria in `AGENTS.md`.
- Next Phase: **Phase 4 – Workflow Engine (Definition, Graph Validation, Execution Engine, Optimistic Concurrency)**.
