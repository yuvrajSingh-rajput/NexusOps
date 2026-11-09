# NexusOps – End-to-End Acceptance Demo & Evaluator Walkthrough

This document guides an evaluator, examiner, or developer through the complete end-to-end acceptance test scenario defined in `AGENTS.md` (Section 2). 

NexusOps is a commercial-grade, multi-tenant internal operations portal and service orchestration platform running **100% locally** with zero external cloud dependencies.

---

## 1. Quickstart: Running NexusOps Locally

### Prerequisites
- Java 21+ (LTS)
- Node.js 20+ & npm
- Docker & Docker Compose

### Step 1: Start Local Infrastructure
Run local containers for PostgreSQL 16 (with pgvector), Redis 7, Kafka KRaft, MinIO, and Mailpit:
```powershell
docker compose -f infrastructure/docker-compose.yml up -d
```

Verify all services are healthy:
- **PostgreSQL + pgvector:** `localhost:5432`
- **Redis:** `localhost:6379`
- **Kafka KRaft:** `localhost:9092`
- **MinIO S3 API:** `http://localhost:9000` (Console: `http://localhost:9001`, `minioadmin` / `miniopassword`)
- **Mailpit Web UI:** `http://localhost:8025`

### Step 2: Start Backend
In a new terminal:
```powershell
cd nexusops-backend
.\mvnw.cmd spring-boot:run
```
The backend initializes database tables via Flyway, seeds system permissions, and automatically runs `DemoDataSeeder` to provision the Acme Corp and Globex Corp demo environments.

- **Backend Base URL:** `http://localhost:8080`
- **OpenAPI Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **Actuator Health:** `http://localhost:8080/actuator/health`

### Step 3: Start Frontend
In a new terminal:
```powershell
cd nexusops-frontend
npm run dev
```
Open your browser at: **`http://localhost:5173`**

---

## 2. Pre-Seeded Evaluator Personas

The login screen features **1-Click Quick Login** cards for each persona. All passwords are set to `Password123!`.

| Persona | Email | Organization | Department | Role | Purpose & Responsibilities |
|---|---|---|---|---|---|
| **Alice Chen** | `alice@acme.com` | Acme Corporation | Engineering (`ENG`) | `EMPLOYEE` | Submits operational requests, tracks personal requests, questions Nexus AI about policies. |
| **Bob Martinez** | `bob@acme.com` | Acme Corporation | Engineering (`ENG`) | `MANAGER` | Engineering Manager and direct supervisor to Alice. Evaluates Level-1 approvals. |
| **Carol Davis** | `carol@acme.com` | Acme Corporation | Finance (`FIN`) | `FINANCE` | Finance Controller. Reviews budget allocations and approval thresholds exceeding $10,000. |
| **Dave Wilson** | `dave@acme.com` | Acme Corporation | IT (`IT`) | `OPERATOR` | Senior IT Specialist. Claims and fulfills hardware procurement and provisioning tasks. |
| **Eve Vance** | `eve@acme.com` | Acme Corporation | Operations (`OPS`) | `ORG_ADMIN` | Organization Administrator. Full visibility into audit trails, workflows, analytics, and departments. |
| **Frank Grimes** | `frank@globex.com` | Globex Corporation | Operations (`OPS`) | `EMPLOYEE` | Employee of isolated external tenant. Used to verify cross-tenant security boundaries. |

---

## 3. End-to-End Acceptance Journey

### Step 1: Employee Submits a Hardware Requisition (Alice Chen)
1. On `http://localhost:5173`, click **Alice Chen (Employee)** on the 1-Click login panel.
2. In the left navigation, click **Requests** (`/requests`).
3. Click **+ New Request**:
   - **Category:** Select `Developer Workstation & Laptop`.
   - **Title:** `High-Performance AI Workstation Rig`
   - **Priority:** `URGENT`
   - **Device Model:** `MacBook Pro 16 M3 Max`
   - **RAM (GB):** `128`
   - **Estimated Amount ($):** `25000` *(Notice: > $10,000 threshold requires multi-tier approval)*
   - **Business Justification:** `Production model fine-tuning and distributed systems simulation`
4. Click **Submit Request**:
   - Request `REQ-1001` is generated atomically.
   - Status transitions to `IN_REVIEW`.
   - Workflow instance starts and pauses at node `MGR_APPROVAL`.
5. Click **Sign Out** in the top-right user menu.

---

### Step 2: Direct Manager Approval (Bob Martinez)
1. On `http://localhost:5173`, click **Bob Martinez (Manager)** to sign in.
2. In the navigation, click **Approvals** (`/approvals`).
3. Under the **Pending Approvals** tab, observe `REQ-1001` for Alice Chen.
4. Click on the approval card to inspect request details, justification, and dynamic payload.
5. In the Decision box, enter:
   - **Decision:** Click **Approve**.
   - **Comments:** `Approved for engineering lab velocity.`
6. Click **Confirm Decision**:
   - Node `MGR_APPROVAL` completes.
   - Condition node `COND_AMOUNT` evaluates `payload.estimatedAmount = 25000`.
   - Because $25,000 > $10,000, the workflow engine automatically advances to `FIN_APPROVAL`.
7. Click **Sign Out**.

---

### Step 3: Finance Budget Approval (Carol Davis)
1. On `http://localhost:5173`, click **Carol Davis (Finance)** to sign in.
2. Click **Approvals** (`/approvals`).
3. Observe the Level-2 approval for `REQ-1001` in the Finance queue.
4. Enter comments: `CapEx budget line ENG-CAPEX-2026 authorized.`
5. Click **Approve**:
   - Node `FIN_APPROVAL` completes.
   - All required approvals are finished!
   - Workflow engine automatically enters node `TASK_PROCUREMENT`.
   - Task `TASK-1001` is dispatched to the `IT_OPERATIONS` queue.
6. Click **Sign Out**.

---

### Step 4: IT Fulfillment Task Execution (Dave Wilson)
1. On `http://localhost:5173`, click **Dave Wilson (IT Operator)** to sign in.
2. Click **Tasks** (`/tasks`).
3. Under the **Department Queue** tab, observe unassigned task `TASK-1001`: `IT Procurement & Provisioning`.
4. Click **Claim Task**:
   - Status changes to `IN_PROGRESS`, assignee set to Dave Wilson.
5. In the task drawer, click **Complete Task**:
   - **Resolution Notes:** `MacBook Pro 16 provisioned with corporate MDM profile, serial #ACME-8849-MBP.`
   - Click **Submit Completion**.
   - Node `TASK_PROCUREMENT` completes.
   - Workflow reaches node `END_COMPLETED`.
   - Request `REQ-1001` automatically transitions to `COMPLETED`!
6. Click **Sign Out**.

---

### Step 5: Full Audit Trail & Operational Analytics (Eve Vance)
1. On `http://localhost:5173`, click **Eve Vance (Org Admin)** to sign in.
2. Click **Audit Trail** (`/audit`):
   - Observe the append-only, tamper-evident log stream.
   - Notice events for `REQUEST_SUBMITTED`, `APPROVAL_COMPLETED` (Bob), `APPROVAL_COMPLETED` (Carol), `TASK_ASSIGNED` (Dave), `TASK_COMPLETED`, and `WORKFLOW_COMPLETED`.
3. Click **Analytics** (`/analytics`):
   - Observe real-time KPIs: Active Requests, Completed Requests, Pending Approvals, Open Tasks.
   - Review the 100% SLA Adherence rate and category throughput distribution.
   - Click **Generate Report** for Department `ENG` to see live aggregations.

---

### Step 6: Nexus AI Assistant (Natural Language Policy RAG & Actions)
1. While logged in as Alice or Eve, click the **Nexus AI** co-pilot button in the top bar.
2. In the AI chat window, ask a policy question:
   > *"What are our laptop purchase approval tiers and policies?"*
3. Observe the response:
   - Nexus AI retrieves relevant chunks from the indexed policy document via semantic vector similarity.
   - Cites `Acme Corporation - Global IT Hardware & Procurement Policy (2026)`.
   - Explains Tier 1 (Manager up to $10k), Tier 2 (Finance up to $50k), and Tier 3 (Dept Head > $50k).
4. Ask Nexus AI for a department report:
   > *"Generate a report for the Engineering department"*
   - Nexus AI invokes the `generateDepartmentReport` governed tool and returns aggregated operational metrics.

---

### Step 7: Multi-Tenant Boundary Isolation (Frank Grimes)
1. Sign out and click **Frank Grimes (Globex Corp)** to sign in.
2. Observe that Frank is scoped to Globex Corporation:
   - Frank's Request Queue shows `0` requests (zero access to Acme's requests).
   - Attempting to inspect Acme's request `REQ-1001` via direct ID results in `404 Not Found` (IDOR defense).
   - Approvals queue shows `0` pending approvals.
   - Asking Nexus AI about Acme's internal policies yields `0` Acme citations (vector retrieval strictly tenant-scoped).

---

## 4. Automated Acceptance Verification
You can also run the entire automated acceptance suite directly:
```powershell
cd nexusops-backend
.\mvnw.cmd test -Dtest=EndToEndDemoIntegrationTest
```
Result: **3/3 master test scenarios pass with 0 failures and 0 errors.**
```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in com.nexusops.EndToEndDemoIntegrationTest
[INFO] BUILD SUCCESS
```

