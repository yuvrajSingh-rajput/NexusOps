# NexusOps – Current Project Status

**Current Phase:** Phase 8 – Nexus AI Assistant & Governed Tool Registry Completed (Review Gate Active)  
**Last Updated:** 2026-11-09  
**Branch:** master  

---

## Completed in Phase 8 (Nexus AI Assistant & Governed Tool Registry)

1. **Database Schema & Migrations (Flyway):**
   - [`V9__ai_schema.sql`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/resources/db/migration/V9__ai_schema.sql):
     - `ai_conversations`: Scoped to `(organization_id, id)` with `user_id`, `title`, and timestamps.
     - `ai_messages`: Append-only message log with `role: USER, ASSISTANT, SYSTEM, TOOL`, `content TEXT`, `citations JSONB`, `tool_call JSONB`.
     - `ai_tool_executions`: Two-phase action proposal table with `tool_name`, `arguments JSONB`, `status: PROPOSED, AWAITING_CONFIRMATION, CONFIRMED, EXECUTING, SUCCEEDED, FAILED, REJECTED, EXPIRED`, `confirmation_required`, `confirmed_by_id`, `confirmed_at`, `result_summary JSONB`, and `expires_at` (15-min durable TTL).
     - Composite foreign keys and Row-Level Security (RLS) policies on all AI tables.

2. **Backend Architecture & Governed Tool Engine (`com.nexusops.ai`):**
   - **Domain & Infrastructure Entities:**
     - `AiConversationEntity`, `AiMessageEntity`, `AiToolExecutionEntity`, `AiMessageRole`, `ToolExecutionStatus`.
     - Repositories: `AiConversationRepository`, `AiMessageRepository`, `AiToolExecutionRepository`.
   - **Governed Tool Registry ([`ToolRegistry.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/ai/application/tools/ToolRegistry.java)):**
     - `SearchMyRequestsTool`: Read-only queries against `RequestService.getMyRequests`.
     - `GetRequestDetailsTool`: Detailed single-request lookup with tenant boundary check.
     - `SearchPendingApprovalsTool`: Read-only queries against `ApprovalService.getMyPendingApprovals`.
     - `SearchMyTasksTool`: Read-only queries against `TaskService.getMyTasks`.
     - `SearchKnowledgeBaseTool`: Semantic RAG vector queries against `KnowledgeService.semanticSearch`.
     - `CreateRequestTool`: Governed two-phase action proposal tool. Returns structured proposal cards; does NOT mutate database until confirmed.
   - **Orchestration, RAG Grounding & Prompt-Injection Defense:**
     - `IntentClassifier`: Classifies user input into `CONVERSATION`, `RAG_QUERY`, `READ_TOOL`, or `ACTION_PROPOSAL`.
     - `RagContextBuilder`: Encapsulates retrieved knowledge chunks within strict untrusted XML boundary delimiters (`<untrusted_document_context>`) to defend against indirect prompt-injection attacks.
     - `AiModelProvider`: Local Ollama integration with deterministic template synthesizer fallback when no LLM is configured.
     - `AiAssistantService`: Main service orchestrating chats, SSE streaming, action proposal generation, and two-phase confirmation execution.
   - **REST APIs ([`AiController.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/main/java/com/nexusops/ai/api/AiController.java)):**
     - `POST /api/v1/ai/chat`: Synchronous chat query returning grounded answers, citations, and action proposals.
     - `GET /api/v1/ai/chat/stream`: Server-Sent Events (SSE) streaming chat.
     - `GET /api/v1/ai/conversations`: Paginated list of conversations.
     - `GET /api/v1/ai/conversations/{id}`: Detailed conversation message history.
     - `POST /api/v1/ai/actions/{id}/confirm`: Two-phase action confirmation executing backend services.
     - `POST /api/v1/ai/actions/{id}/cancel`: Cancellation of proposed tool execution.

3. **Backend Integration Testing ([`AiAssistantIntegrationTest.java`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-backend/src/test/java/com/nexusops/AiAssistantIntegrationTest.java)):**
   - `shouldProvideGroundedPolicyAnswerWithCitations`: Verifies RAG policy lookup and citations return.
   - `shouldExecuteReadToolsToInspectUserRequests`: Verifies read tool execution returning personal user requests.
   - `shouldExecuteTwoPhaseConfirmationForWriteActions`: Verifies proposing an action returns PROPOSED status card with TTL, and confirming executes `RequestService.createRequest`, advancing workflow to `MGR_APPROVAL` and generating `REQ-1001`.
   - `shouldEnforceCrossTenantAiIsolation`: Verifies Tenant B cannot access Tenant A's conversations or confirm Tenant A's actions (returns 404).
   - **Full Backend Test Suite:**
     - **32 tests passed, 0 failures, 0 errors (`BUILD SUCCESS`)**.
     - Spring Modulith architectural boundaries test: 100% clean across all modules.

4. **Frontend Implementation (`nexusops-frontend`):**
   - **TypeScript Definitions (`src/types/api.ts`):** Added `AiMessageRole`, `ToolExecutionStatus`, `ActionProposalDto`, `ChatCitationDto`, `ChatResponseDto`, `ChatRequestDto`, `ConfirmActionDto`, `ConversationSummaryDto`, `ChatMessageDto`.
   - **Slide-over Drawer ([`AiAssistantDrawer.tsx`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-frontend/src/features/ai-assistant/AiAssistantDrawer.tsx)):**
     - Persistent drawer accessible anywhere via topbar sparkle icon.
     - Interactive chat stream with grounded citation pills.
     - Two-phase action cards with "Confirm & Submit" and "Dismiss" buttons.
     - Excerpt inspection modal on citation click.
   - **Dedicated Workspace Studio ([`AiAssistantPage.tsx`](file:///c:/Users/yuvra/Desktop/NexusOps/nexusops-frontend/src/features/ai-assistant/AiAssistantPage.tsx)):**
     - 3-column enterprise layout: Conversations History, Main Chat Canvas, and Governance Inspector panel.
     - Quick prompt suggestions for policies, active requests, pending approvals, and laptop proposals.
   - **Navigation & Routing:** Added `/ai-assistant` route in `routes.tsx` and sidebar link in `Sidebar.tsx`.
   - **Frontend Verification:** `npm run build` (`✓ built in 9.57s`) and Vitest unit tests passed.

---

## Next Milestone: Phase 9 – Transactional Outbox & Kafka Event Bus

- Outbox table (`outbox_events`) written in the same DB transaction as business mutations.
- Outbox relay publisher forwarding events to Kafka in KRaft mode.
- Idempotent consumers with `processed_events` table deduplication.
- Dead-letter topics and retry backoff.
