# NexusOps – REST API Specifications & Contracts

**Document Version:** 1.0.0  
**Status:** Approved for Architecture & Design  
**Author:** NexusOps Architecture Team  

---

## 1. REST API Conventions & Global Envelope

- **Base Path:** `/api/v1`
- **Content-Type:** `application/json` (except SSE streams: `text/event-stream` and file uploads: `multipart/form-data`)
- **Character Encoding:** UTF-8
- **Authentication Header:** `Authorization: Bearer <jwt-token>`
- **Tracing Header:** `X-Correlation-Id: <uuid>` (client may pass or server generates)
- **Idempotency Header:** `Idempotency-Key: <unique-client-key>` on mutating endpoints

### 1.1 Standard Response Envelopes

#### Success Envelope (Single Object)
```json
{
  "success": true,
  "data": {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "requestNumber": "REQ-1001",
    "status": "SUBMITTED"
  },
  "timestamp": "2026-11-09T05:45:00Z"
}
```

#### Success Envelope (Paginated List)
```json
{
  "success": true,
  "data": {
    "content": [ ... ],
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 85,
    "totalPages": 5,
    "isLast": false
  },
  "timestamp": "2026-11-09T05:45:00Z"
}
```

#### Error Envelope
```json
{
  "success": false,
  "error": {
    "code": "CONCURRENT_MODIFICATION",
    "message": "This approval was already processed by another approver.",
    "details": [
      {
        "field": "version",
        "issue": "Optimistic lock conflict; expected version 0 but was 1"
      }
    ]
  },
  "timestamp": "2026-11-09T05:45:00Z"
}
```

---

## 2. Standard HTTP Status Codes

| Code | Meaning in NexusOps |
| :--- | :--- |
| `200 OK` | Successful query or synchronous update. |
| `201 Created` | Successful resource creation (returns `Location` header). |
| `202 Accepted` | Asynchronous task accepted for processing (e.g., document re-indexing). |
| `400 Bad Request` | Syntactically invalid JSON or missing mandatory parameters. |
| `401 Unauthorized` | Missing, expired, or invalid JWT access token. |
| `403 Forbidden` | Authenticated user lacks required permission or resource scope. |
| `404 Not Found` | Target entity does not exist within the tenant scope. |
| `409 Conflict` | Optimistic concurrency conflict or state machine transition violation. |
| `422 Unprocessable` | Semantic validation failure (e.g. form payload does not match JSON schema). |
| `500 Server Error` | Unexpected backend runtime failure (sanitized error message; no stack trace). |
| `503 Service Unavailable`| Optional subsystem offline (e.g. LLM provider disabled). |

---

## 3. Core Endpoint Specifications

### 3.1 Authentication & Tenant Switching (`/api/v1/auth`)

| Method | Path | Summary | Auth Required |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/register-org` | Register new organization and initial org admin user. | Public |
| `POST` | `/api/v1/auth/login` | Authenticate with email/password; returns JWT + sets refresh cookie. | Public |
| `POST` | `/api/v1/auth/refresh` | Rotate refresh token cookie and return new access JWT. | Cookie |
| `POST` | `/api/v1/auth/logout` | Revoke refresh token and invalidate Redis session. | Bearer |
| `GET` | `/api/v1/auth/me` | Return active user identity, organization context, roles & permissions. | Bearer |
| `POST` | `/api/v1/auth/switch-org` | Switch active organization context (returns fresh JWT). | Bearer |

### 3.2 Organization & Employee Directory (`/api/v1/organizations`, `/api/v1/departments`)

| Method | Path | Summary | Required Permission |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/organizations/current` | Get active tenant details and settings. | Authenticated |
| `PATCH` | `/api/v1/organizations/current` | Update tenant settings. | `ORG_ADMIN_UPDATE` |
| `GET` | `/api/v1/departments` | List organization departments with hierarchy. | Authenticated |
| `POST` | `/api/v1/departments` | Create new department. | `ORG_DEPT_MANAGE` |
| `GET` | `/api/v1/memberships` | Paginated search of organization employees / members. | Authenticated |
| `POST` | `/api/v1/memberships/invite` | Invite new employee to the organization. | `ORG_USER_INVITE` |

### 3.3 Request Management (`/api/v1/requests`, `/api/v1/request-categories`)

| Method | Path | Summary | Required Permission |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/request-categories` | List active request categories with JSON schemas. | Authenticated |
| `POST` | `/api/v1/requests` | Submit or draft a new operational request (`Idempotency-Key` enabled). | `REQUEST_CREATE` |
| `GET` | `/api/v1/requests/my` | Paginated list of requests submitted by current user. | `REQUEST_VIEW_OWN` |
| `GET` | `/api/v1/requests/{id}` | Get full request details, current node, and audit timeline. | Scope Checked |
| `POST` | `/api/v1/requests/{id}/cancel` | Cancel an active in-flight request. | Scope Checked |
| `GET` | `/api/v1/requests/{id}/comments`| List comments (automatically filters `INTERNAL` comments for employee). | Scope Checked |
| `POST` | `/api/v1/requests/{id}/comments`| Add a new comment (`REQUESTER_VISIBLE` or `INTERNAL`). | `REQUEST_COMMENT` |
| `POST` | `/api/v1/requests/{id}/attachments`| Upload an attachment file to MinIO (multipart). | `REQUEST_ATTACH` |

### 3.4 Workflow Engine Studio (`/api/v1/workflows`)

| Method | Path | Summary | Required Permission |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/workflows` | List workflow definitions. | `WORKFLOW_VIEW` |
| `POST` | `/api/v1/workflows` | Create a new workflow definition. | `WORKFLOW_MANAGE` |
| `GET` | `/api/v1/workflows/{id}/versions/{v}`| Retrieve full node/edge graph for React Flow designer. | `WORKFLOW_VIEW` |
| `PUT` | `/api/v1/workflows/{id}/versions/{v}`| Save updated draft graph positions, nodes, and conditions. | `WORKFLOW_MANAGE` |
| `POST` | `/api/v1/workflows/{id}/versions/{v}/validate`| Run backend graph validation checks (entry, reachability). | `WORKFLOW_MANAGE` |
| `POST` | `/api/v1/workflows/{id}/versions/{v}/publish`| Formally publish and lock version (activates for requests). | `WORKFLOW_MANAGE` |

### 3.5 Approval Inbox (`/api/v1/approvals`)

| Method | Path | Summary | Required Permission |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/approvals/my-pending` | Paginated list of pending approvals for current user. | `APPROVAL_VIEW_PENDING` |
| `GET` | `/api/v1/approvals/{id}` | Get approval details, request snapshot, and history. | Scope Checked |
| `POST` | `/api/v1/approvals/{id}/action` | Execute decision: `APPROVE`, `REJECT`, `REQUEST_INFORMATION`. | Scope Checked (`@Version`) |
| `POST` | `/api/v1/approvals/{id}/delegate`| Delegate approval to a peer or surrogate approver. | Scope Checked |

### 3.6 Task Management & Queue Fulfillment (`/api/v1/tasks`)

| Method | Path | Summary | Required Permission |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/tasks/my` | Tasks assigned to authenticated operator. | `TASK_VIEW_ASSIGNED` |
| `GET` | `/api/v1/tasks/queue/{teamId}` | Unassigned tasks in functional department queue. | `TASK_QUEUE_VIEW` |
| `POST` | `/api/v1/tasks/{id}/claim` | Claim an unassigned task from the queue. | `TASK_CLAIM` |
| `PATCH`| `/api/v1/tasks/{id}/status` | Update task status (`IN_PROGRESS`, `BLOCKED`). | Scope Checked |
| `POST` | `/api/v1/tasks/{id}/complete` | Mark task completed with operational output data. | Scope Checked |

### 3.7 Knowledge Base & Documents (`/api/v1/knowledge`)

| Method | Path | Summary | Required Permission |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/knowledge/documents` | List organization policy documents and index status. | `KNOWLEDGE_VIEW` |
| `POST` | `/api/v1/knowledge/documents` | Upload document (`.pdf`, `.md`, `.docx`) for RAG indexing. | `KNOWLEDGE_MANAGE` |
| `DELETE`| `/api/v1/knowledge/documents/{id}`| Remove document and delete corresponding vector embeddings. | `KNOWLEDGE_MANAGE` |
| `POST` | `/api/v1/knowledge/documents/{id}/reindex`| Re-run text extraction and vector embedding generation. | `KNOWLEDGE_MANAGE` |

### 3.8 Nexus AI Operations Assistant (`/api/v1/ai`)

| Method | Path | Summary | Required Permission |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/ai/chat` | Send prompt; returns Server-Sent Events (SSE) stream. | `AI_CHAT_ACCESS` |
| `GET` | `/api/v1/ai/conversations` | List user's historical AI conversations. | `AI_CHAT_ACCESS` |
| `GET` | `/api/v1/ai/conversations/{id}`| Get conversation messages, citations, and preview cards. | `AI_CHAT_ACCESS` |
| `POST` | `/api/v1/ai/actions/{actionId}/confirm`| User confirms execution of a proposed write action. | Scope Checked |
| `POST` | `/api/v1/ai/actions/{actionId}/cancel` | User rejects / dismisses an action proposal card. | Scope Checked |

### 3.9 Operational Analytics & Audit Logs (`/api/v1/analytics`, `/api/v1/audit`)

| Method | Path | Summary | Required Permission |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/analytics/overview` | KPI summaries: totals, approvals avg time, completion rate. | `ANALYTICS_VIEW` |
| `GET` | `/api/v1/analytics/department-workload`| Workload and SLA performance grouped by department. | `ANALYTICS_VIEW` |
| `GET` | `/api/v1/audit/logs` | Paginated search of append-only audit trail with diffs. | `AUDIT_VIEW` |
