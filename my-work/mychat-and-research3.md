Let's finalize the architecture for my capstone. Backend: modular. AI: hybrid. Workflow engine: dynamic. Product name: NexusOps. Now prepare the complete technical blueprint: architecture diagram, module boundaries, database ER diagram, entity relationships, API contracts, technology versions, folder structure, and development sequence. Start with the technical design, not code.
NexusOps – Complete Technical Architecture & Engineering Blueprint
Architecture finalized AI-native enterprise SaaS

Technical Design Document · Version 1.0 · November 2026

We are now moving from product ideation to actual software architecture.

The goal is to design NexusOps as a production-oriented enterprise SaaS platform that can be developed, tested, deployed, demonstrated, and potentially extended into a commercial product.

I've locked in your four decisions:

Decision

Selected architecture

Product

NexusOps

Backend

Modular monolith

AI

Hybrid AI: RAG + agents + tool calling

Workflow

Custom dynamic workflow engine

Frontend

React + TypeScript

Database

PostgreSQL

Messaging

Kafka

Cache

Redis

Deployment

Docker + AWS

1. Technical vision and architectural principles
Before designing tables and APIs, we need to establish some architectural principles.

These decisions will influence almost every implementation detail.

1.1 Architecture style
We will build a modular monolith with event-driven internal processing.

This means:

One primary Spring Boot backend application.

Multiple independently structured business modules.

One PostgreSQL database initially.

Strict ownership of business data by modules.

Explicit communication between modules.

Kafka for asynchronous events and background processing.

A logically separate AI orchestration layer.

No unnecessary microservices.

Why this architecture?

Imagine the Request module needs to start an approval workflow.

It should invoke a defined Workflow application interface, not directly manipulate workflow tables.

This separation gives us maintainability without introducing distributed transactions and unnecessary deployment complexity.

1.2 Core architectural principles
Modularity: Every business module has a clearly defined responsibility.

Separation of concerns: Controllers, services, repositories, and entities have distinct responsibilities.

Domain ownership: A module owns its entities and database tables.

Tenant isolation: No organization can access another organization's data.

Security by default: Every operation is authorized on the backend.

AI governance: AI cannot bypass business rules or permissions.

Event-driven processing: Non-critical asynchronous work is processed independently.

Auditability: Important business operations are traceable.

Reliability: Duplicate events, retries, concurrency and failures are explicitly handled.

Testability: Each module can be tested independently.

2. High-level system architecture
This is the architecture I recommend implementing.

Operations
Asynchronous Processing
Data Layer
AI Infrastructure
NexusOps Spring Boot Application
Frontend Layer
Business Modules
Docker
AWS ECS
OpenTelemetry / Metrics
GitHub Actions
Transactional Outbox
Kafka
Background Consumers
PostgreSQL
pgvector
Redis
AWS S3
LLM Provider
RAG Pipeline
Authorized Java Tools
Conversation Memory
Organization & Identity
Request Management
Workflow Engine
Approval Management
Task Management
Knowledge Management
Notification Management
Analytics
Audit
Security LayerJWT / RBAC / Tenant Context
AI Orchestration Layer
REST API Layer
React + TypeScript
TanStack Query
AI Assistant UI
Employees / Managers /Admins
There are five major architectural layers:

Layer

Responsibility

Presentation

React interface and user interactions

Application

API endpoints and orchestration

Domain

Business rules and module logic

Infrastructure

Databases, Kafka, Redis, S3, LLM integrations

Operations

Deployment, observability, testing and CI/CD

One important refinement: the AI orchestration layer is logically separate, but initially runs inside the same Spring Boot deployment. We can extract it later if scaling or operational requirements justify doing so.

3. Module boundaries and responsibilities
This is the most important part of our modular monolith.

We will divide NexusOps into 12 business and supporting modules.

NexusOps backend modules
01. Identity & Access Management

Authentication, authorization, users, roles, permissions and organization membership.
02. Organization Management

Organizations, departments, teams, organizational configuration and tenant settings.
03. Request Management

Request creation, request categories, lifecycle, comments and attachments.
04. Workflow Engine

Workflow definitions, versions, conditions, stages, routing and execution.
05. Approval Management

Approval instances, decisions, rejection, delegation and approval history.
06. Task Management

Task generation, assignments, comments, deadlines, SLA and completion.
07. Knowledge Management

Document upload, processing, chunking, embeddings, indexing and retrieval.
08. AI Orchestration

Chat, RAG, tool calling, conversation management and AI action governance.
09. Notification Management

In-app notifications, email, reminders and escalations.
10. Analytics

KPIs, operational reports, dashboards and bottleneck analysis.
11. Audit & Compliance

Audit history, security-relevant events and AI execution records.
12. Platform Administration

Platform configuration, tenant lifecycle, subscription and system administration.
3.1 Module dependency rules
We should not allow arbitrary modules to call each other or access each other's repositories.

Here is the intended dependency direction:

Identity / Organization
Request
Workflow
Approval
Task
AI Orchestration
Knowledge
Notification
Analytics
Audit
events
events
events
events
events
This is a logical dependency diagram, not a requirement that every arrow be a synchronous Java method call.

For example:

Request Management calls Workflow Management through an application interface.

Workflow Management creates approval instances through Approval Management.

Approval Management informs the workflow engine of decisions.

Notifications and analytics consume events asynchronously.

AI uses controlled application services through registered tools.

We will use explicit interfaces and module APIs rather than allowing cross-module repository access.

4. Database architecture and ER diagram
Now we need to design the most important persistent part of NexusOps.

The database must support:

Multiple organizations.

Multiple departments per organization.

Multiple employees.

Multiple request categories.

Different workflows for different organizations.

Multiple approval steps.

Tasks generated from workflows.

AI conversations and document retrieval.

Complete audit history.

We will use PostgreSQL as our primary relational database and pgvector as its vector extension.

4.1 Database design strategy
I recommend the following rules:

Design decision

Implementation

Primary identifiers

UUID

Tenant identification

organization_id

Timestamps

TIMESTAMPTZ

Monetary values

NUMERIC(19,2)

Flexible request data

JSONB

Document embeddings

pgvector

Schema management

Flyway

Database relationships

Foreign keys

Concurrency

Optimistic locking

Tenant isolation

Application authorization + PostgreSQL RLS

Every organization-owned business record should have an organization_id, even if the organization can technically be inferred through another relationship.

This makes tenant isolation and query auditing much easier.

4.2 Core entity relationship diagram
This is the conceptual ER diagram. It shows the main entities and their relationships, not every implementation column.

ORGANIZATION
DEPARTMENT
USER
USER_ROLE
ROLE
REQUEST
REQUEST_COMMENT
REQUEST_ATTACHMENT
WORKFLOW_DEFINITION
WORKFLOW_VERSION
WORKFLOW_NODE
WORKFLOW_TRANSITION
WORKFLOW_INSTANCE
WORKFLOW_NODE_INSTANCE
APPROVAL_INSTANCE
APPROVAL_ACTION
TASK
DOCUMENT
DOCUMENT_CHUNK
AI_CONVERSATION
AI_MESSAGE
AI_TOOL_EXECUTION
NOTIFICATION
AUDIT_LOG
OUTBOX_EVENT
contains
employs
includes
assigned
grants
owns
creates
has
has
configures
versions
contains
connects
triggers
executes
runs
may_create
assigned
records
generates
triggers
assigned
owns
contains
owns
starts
contains
invokes
receives
receives
owns
publishes
Let's examine each database group.

4.3 Identity and organization tables
organizations
Stores each company registered on NexusOps.

Column

Type

Purpose

id

UUID

Primary key

name

VARCHAR

Company name

slug

VARCHAR

Unique organization identifier

email_domain

VARCHAR

Optional company domain

status

VARCHAR

ACTIVE, SUSPENDED

created_at

TIMESTAMPTZ

Creation time

users
Stores employee information.

Column

Type

id

UUID

organization_id

UUID

department_id

UUID

manager_id

UUID, nullable

first_name

VARCHAR

last_name

VARCHAR

email

VARCHAR

password_hash

VARCHAR

status

VARCHAR

created_at

TIMESTAMPTZ

A user belongs to an organization and may belong to a department.

manager_id references another user.

departments
Column

Type

id

UUID

organization_id

UUID

name

VARCHAR

description

TEXT

manager_id

UUID

created_at

TIMESTAMPTZ

roles, permissions, user_roles, role_permissions
These implement RBAC.

Example roles:

EMPLOYEE

MANAGER

DEPARTMENT_OPERATOR

ORGANIZATION_ADMIN

EXECUTIVE

PLATFORM_ADMIN

Permissions are more granular:

REQUEST_CREATE

REQUEST_VIEW_OWN

REQUEST_VIEW_DEPARTMENT

REQUEST_APPROVE

TASK_ASSIGN

WORKFLOW_CONFIGURE

AUDIT_VIEW

One user can have multiple roles.

One role can have multiple permissions.

For tenant-specific roles, keep organization ownership explicit. Platform-level roles should be separately controlled and not assignable by ordinary organization administrators.

4.4 Request management tables
request_categories
Examples:

HARDWARE_PURCHASE

SOFTWARE_ACCESS

EXPENSE_REIMBURSEMENT

IT_SUPPORT

LEAVE

ONBOARDING

Each category can be configured by an organization.

requests
This is one of the most important tables.

Column

Type

Purpose

id

UUID

Primary key

organization_id

UUID

Tenant

request_number

VARCHAR

REQ-1001

category_id

UUID

Request category

requester_id

UUID

Employee

department_id

UUID

Department

title

VARCHAR

Request title

description

TEXT

Description

priority

VARCHAR

LOW, MEDIUM, HIGH, CRITICAL

status

VARCHAR

Current request state

payload

JSONB

Category-specific information

submitted_at

TIMESTAMPTZ

Submission time

created_at

TIMESTAMPTZ

Creation time

updated_at

TIMESTAMPTZ

Modification time

version

BIGINT

Optimistic locking

Why JSONB for payload?

Because different request categories have different fields.

A hardware request might contain:

{
  "item": "Laptop",
  "estimatedAmount": 120000,
  "currency": "INR",
  "businessJustification": "Development work"
}

A software access request might contain:

{
  "software": "Grafana",
  "accessLevel": "PRODUCTION_READ",
  "durationDays": 90,
  "businessJustification": "Monitoring application performance"
}

Both belong to the same request table.

We will validate the payload against the schema configured for its request category. Important frequently queried fields should eventually become indexed or normalized columns.

request_comments
Stores conversations attached to requests.

Column

Type

id

UUID

organization_id

UUID

request_id

UUID

author_id

UUID

comment

TEXT

created_at

TIMESTAMPTZ

request_attachments
Stores attachment metadata.

Actual files live in S3, not in PostgreSQL.

Fields include:

id

organization_id

request_id

uploaded_by

s3_object_key

original_filename

content_type

file_size

uploaded_at

4.5 Workflow engine tables
This is where we need a more sophisticated design.

I don't recommend storing the entire workflow in one JSON column and trying to execute it directly.

We need separate definitions and execution records.

Table

Responsibility

workflow_definitions

Logical workflow identity

workflow_versions

Immutable published versions

workflow_nodes

Steps within a version

workflow_transitions

Connections and routing conditions

workflow_instances

Individual running executions

workflow_node_instances

Runtime execution of each step

Example workflow definition
Laptop Purchase Approval

Version: 1

Nodes:

START

MANAGER_APPROVAL

FINANCE_APPROVAL

PROCUREMENT_TASK

END

Transitions:

START → MANAGER_APPROVAL

MANAGER_APPROVAL → FINANCE_APPROVAL

FINANCE_APPROVAL → PROCUREMENT_TASK

PROCUREMENT_TASK → END

A published workflow version must be immutable. Changes create a new version.

workflow_instances
Suppose Rahul creates REQ-1001.

The system creates a workflow instance:

Column

Value

id

UUID

organization_id

ORG-001

workflow_version_id

Version 1

request_id

REQ-1001

status

RUNNING

current_node_id

MANAGER_APPROVAL

started_at

Timestamp

This is an individual execution of a workflow definition.

The definition describes what should happen.

The instance records what is actually happening.

That distinction is essential.

4.6 Approval tables
approval_instances
Each approval requirement creates a runtime approval record.

Column

Type

id

UUID

organization_id

UUID

workflow_node_instance_id

UUID

approver_id

UUID

status

VARCHAR

due_at

TIMESTAMPTZ

decided_at

TIMESTAMPTZ

version

BIGINT

Possible statuses:

PENDING

APPROVED

REJECTED

REQUESTED_INFO

CANCELLED

EXPIRED

approval_actions
Every decision is stored separately.

Column

Type

id

UUID

organization_id

UUID

approval_instance_id

UUID

actor_id

UUID

action

VARCHAR

comments

TEXT

created_at

TIMESTAMPTZ

Why keep approval_actions?

Because the current approval status tells us the current state, whereas the actions table tells us the complete decision history.

We must not overwrite historical decisions.

4.7 Task management tables
tasks
Column

Type

id

UUID

organization_id

UUID

request_id

UUID

workflow_instance_id

UUID

title

VARCHAR

description

TEXT

assigned_to

UUID

assigned_by

UUID

status

VARCHAR

priority

VARCHAR

due_at

TIMESTAMPTZ

completed_at

TIMESTAMPTZ

version

BIGINT

task_comments
Stores task discussions.

task_history
Stores assignment changes, status changes, due-date modifications and other task events.

This allows us to distinguish between the current task state and its complete history.

4.8 Knowledge and AI tables
Table

Responsibility

documents

Document metadata and storage location

document_chunks

Extracted text and vector embeddings

ai_conversations

User conversations

ai_messages

Individual chat messages

ai_tool_executions

Tool requests and execution outcomes

Example:

A company uploads Reimbursement_Policy.pdf.

The document is stored in S3.

Its metadata is stored in documents.

Its extracted chunks are stored in document_chunks.

Each chunk contains:

Document ID.

Organization ID.

Chunk index.

Text content.

Embedding.

Page number.

Source metadata.

The vector column is indexed using pgvector's supported indexing methods, with tenant-aware retrieval.

AI conversations should also have access-control rules. A user must not be able to retrieve another employee's private conversation simply by guessing its ID.

4.9 Notification, audit and infrastructure tables
Table

Purpose

notifications

User notifications

notification_preferences

User notification settings

audit_logs

Business and security audit records

outbox_events

Reliable event publishing

idempotency_records

Duplicate API submission protection

A note about audit logs: they should be append-only from the application's perspective. We should avoid allowing ordinary update or delete operations on audit records.

Database indexing strategy
At minimum:

Unique (organization_id, email) on users.

Unique (organization_id, request_number) on requests.

Index (organization_id, requester_id, created_at) on requests.

Index (organization_id, status, created_at) on requests.

Index (organization_id, assigned_to, status) on tasks.

Index (organization_id, approver_id, status) on approvals.

Index (organization_id, workflow_id, version) on workflow versions.

Vector index on document embeddings.

For high-value tenant-owned tables, use composite tenant-aware foreign keys where appropriate, so relationships cannot accidentally connect records from different organizations.

Tenant isolation
Every tenant-facing query must be scoped to the authenticated organization.

We should implement:

Tenant context derived from the authenticated principal.

Tenant-aware repository/service operations.

PostgreSQL Row-Level Security as an additional database protection.

Tenant-aware cache keys.

Automated cross-tenant access tests.

Never trust organizationId coming directly from a frontend request or AI tool argument as proof of tenant identity.

5. Dynamic workflow engine – detailed technical design
This deserves its own section because it is one of the most technically interesting parts of NexusOps.

5.1 What exactly are we implementing?
A configurable business process engine.

An administrator creates a workflow using a visual interface.

The workflow is saved as a versioned graph.

When a request is submitted, the engine executes that graph.

5.2 Workflow node types
Our initial workflow engine should support:

Node

Purpose

START

Entry point

END

Workflow completion

APPROVAL

Requires human decision

TASK

Creates an operational task

CONDITION

Evaluates business rules

NOTIFICATION

Sends a notification

DELAY

Waits until a scheduled time

PARALLEL

Executes multiple branches

JOIN

Waits for required branches

AUTOMATION

Invokes an approved application action

Do not implement all node types in the first release. Start with START, END, APPROVAL, CONDITION and TASK.

5.3 Example: Laptop purchase workflow
Let's assume the company has configured this rule:

Amount below ₹10,000: Manager approval.

₹10,000–₹50,000: Manager and Finance approval.

Above ₹50,000: Manager, Finance and Department Head approval.

The administrator creates:

Request submitted
Amount?
Manager approval
Finance approval
Department Head approval
Create procurement task
Completed
Rejected
Below 10000
10000 to 50000
Above 50000
Approved below 10000
Approved 10000+
Approved 10000-50000
Approved above 50000
Approved
Rejected
Rejected
Rejected
The engine doesn't need to know anything specifically about laptops.

It only understands generic workflow nodes, transitions and conditions.

That is what makes it reusable.

5.4 Workflow definition representation
We can store node configuration and transition conditions as JSONB.

An illustrative definition:

{
  "name": "Laptop Purchase Workflow",
  "version": 1,
  "nodes": [
    {
      "id": "start",
      "type": "START"
    },
    {
      "id": "manager",
      "type": "APPROVAL",
      "config": {
        "approver": "REQUEST_MANAGER",
        "timeoutHours": 24
      }
    },
    {
      "id": "finance",
      "type": "APPROVAL",
      "config": {
        "approver": "FINANCE_MANAGER"
      }
    },
    {
      "id": "procurement",
      "type": "TASK",
      "config": {
        "assigneeGroup": "PROCUREMENT"
      }
    },
    {
      "id": "end",
      "type": "END"
    }
  ],
  "transitions": [
    {
      "from": "start",
      "to": "manager"
    },
    {
      "from": "manager",
      "to": "finance",
      "condition": "amount >= 10000"
    },
    {
      "from": "finance",
      "to": "procurement"
    },
    {
      "from": "procurement",
      "to": "end"
    }
  ]
}

This is a simplified illustration. The actual schema will include typed conditions, rejection routes, fallback approvers and validation metadata.

Never execute arbitrary JavaScript, SQL, SpEL or user-supplied code as a workflow condition.

We will create a restricted condition evaluator supporting a safe set of operators, field paths and typed values.

5.5 Workflow execution lifecycle
Database
Task Module
Approval Module
Workflow Engine
Request Module
Database
Task Module
Approval Module
Workflow Engine
Request Module
Employee
Manager
Submit request
Save request
Start workflow
Create workflow instance
Evaluate current node
Create approval
Save approval instance
Notify approver
Approve
Record decision
ApprovalCompleted event
Evaluate next transition
Create task
Save task
Notify task assignee
Employee
Manager
Critical implementation rules
A workflow instance must survive application restarts.

Every node execution must be persisted.

Transitions must be validated against the published workflow version.

Duplicate events must not create duplicate approvals or tasks.

Parallel execution must track branch completion.

Rejection and cancellation must have explicit routes.

Timeouts must be processed through a scheduler or durable job mechanism.

Long-running workflows must never depend on an in-memory Java thread waiting for a person.

This is a durable workflow engine, not just a sequence of if-else statements.

6. API architecture and contracts
Now let's define how the frontend communicates with the backend.

We will use REST APIs with JSON.

Base URL:

/api/v1
All protected APIs require authentication.

A request should have a standard response structure.

Standard success response
{
  "success": true,
  "data": {
    "id": "uuid",
    "status": "SUBMITTED"
  },
  "timestamp": "2026-11-08T12:00:00Z"
}

Standard error response
{
  "success": false,
  "error": {
    "code": "REQUEST_NOT_FOUND",
    "message": "The requested resource was not found",
    "details": []
  },
  "timestamp": "2026-11-08T12:00:00Z"
}

We should also support pagination, filtering, sorting and consistent validation errors.

6.1 Authentication APIs
Method

Endpoint

Purpose

POST

/auth/register-organization

Register organization and initial admin

POST

/auth/login

Authenticate

POST

/auth/refresh

Rotate refresh token

POST

/auth/logout

Revoke session

GET

/auth/me

Current user profile

Use short-lived access tokens and securely managed refresh tokens. Prefer secure, HttpOnly cookies for browser sessions, with appropriate CSRF protection where cookie-based authentication is used.

6.2 Organization APIs
Method

Endpoint

Purpose

GET

/organizations/current

Current organization

PUT

/organizations/current

Update organization

POST

/departments

Create department

GET

/departments

List departments

PUT

/departments/{id}

Update department

POST

/users/invitations

Invite employee

GET

/users

List employees

PATCH

/users/{id}/status

Update employee status

6.3 Request APIs
Method

Endpoint

Purpose

POST

/requests

Create request

GET

/requests

Search requests

GET

/requests/{id}

Request details

PUT

/requests/{id}

Update draft

POST

/requests/{id}/submit

Submit request

POST

/requests/{id}/cancel

Cancel request

POST

/requests/{id}/comments

Add comment

POST

/requests/{id}/attachments

Attach file

GET

/requests/{id}/timeline

Full history

Example: Create request
POST /api/v1/requests

{
  "categoryCode": "HARDWARE_PURCHASE",
  "title": "New development laptop",
  "description": "Laptop required for development work",
  "priority": "MEDIUM",
  "payload": {
    "item": "Laptop",
    "estimatedAmount": 120000,
    "currency": "INR",
    "businessJustification": "Development work"
  }
}

The backend obtains the requester and organization from the authenticated identity.

It does not trust user IDs supplied in this body.

Response:

{
  "success": true,
  "data": {
    "id": "request-uuid",
    "requestNumber": "REQ-1001",
    "status": "DRAFT"
  }
}

The user then submits it through the separate submit endpoint.

6.4 Workflow APIs
Method

Endpoint

Purpose

POST

/workflows

Create workflow

GET

/workflows

List workflows

GET

/workflows/{id}

Get workflow

POST

/workflows/{id}/versions

Create new version

PUT

/workflows/{id}/versions/{versionId}

Update draft

POST

/workflows/{id}/versions/{versionId}/validate

Validate workflow

POST

/workflows/{id}/versions/{versionId}/publish

Publish workflow

GET

/workflow-instances/{id}

Runtime execution details

The workflow editor should never modify a published version.

6.5 Approval APIs
Method

Endpoint

Purpose

GET

/approvals/my-pending

Pending approvals

GET

/approvals/{id}

Approval details

POST

/approvals/{id}/approve

Approve

POST

/approvals/{id}/reject

Reject

POST

/approvals/{id}/request-info

Request more information

POST

/approvals/{id}/delegate

Delegate if permitted

Example:

POST /api/v1/approvals/{id}/approve

{
  "comments": "Approved for development requirements"
}

The server checks whether the authenticated user is authorized and whether the approval is still pending.

6.6 Task APIs
Method

Endpoint

Purpose

POST

/tasks

Create manual task

GET

/tasks

Search tasks

GET

/tasks/my

My tasks

GET

/tasks/{id}

Task details

POST

/tasks/{id}/assign

Assign or reassign

PATCH

/tasks/{id}/status

Update status

POST

/tasks/{id}/comments

Add comment

6.7 Knowledge APIs
Method

Endpoint

Purpose

POST

/knowledge/documents

Upload document

GET

/knowledge/documents

List documents

GET

/knowledge/documents/{id}

Document details

POST

/knowledge/documents/{id}/reprocess

Reprocess

DELETE

/knowledge/documents/{id}

Remove from active knowledge base

Document deletion should also handle vector index cleanup, storage retention rules and any relevant legal holds.

6.8 AI APIs
Method

Endpoint

Purpose

POST

/ai/chat

Send message

GET

/ai/conversations

List conversations

GET

/ai/conversations/{id}

Retrieve conversation

DELETE

/ai/conversations/{id}

Delete conversation

GET

/ai/actions/{id}

Get pending action details

POST

/ai/actions/{id}/confirm

Confirm eligible action

POST

/ai/actions/{id}/cancel

Cancel proposed action

Example:

POST /api/v1/ai/chat

{
  "conversationId": null,
  "message": "Show my pending hardware requests"
}

Response can include:

Assistant message.

Structured action or query result.

Citations, if RAG was used.

Confirmation requirement, if an action is proposed.

For streaming responses, we can use Server-Sent Events.

6.9 Analytics and audit APIs
Method

Endpoint

Purpose

GET

/analytics/overview

Dashboard metrics

GET

/analytics/requests

Request statistics

GET

/analytics/approvals

Approval statistics

GET

/analytics/tasks

Task statistics

GET

/analytics/sla

SLA performance

GET

/audit/logs

Authorized audit history

All APIs should be documented through OpenAPI.

API security and reliability conventions
Use 201 Created for resource creation.

Use 202 Accepted for asynchronous processing.

Use 400 for invalid input.

Use 401 for unauthenticated requests.

Use 403 for unauthorized operations.

Use 404 for unavailable resources.

Use 409 for concurrency or state conflicts.

Use 422 where semantic validation failures need separate handling.

Support Idempotency-Key for sensitive create/execute operations.

Add correlation IDs to requests and logs.

7. AI architecture – complete design
Now we design the hybrid AI system.

Our AI architecture has three major capabilities:

Knowledge-based question answering.

Natural-language application interaction.

Multi-step agent orchestration.

7.1 AI system architecture
User
AI Chat API
Identity & Permission Context
AI Orchestrator
Intent Classification
Intent Router
RAG Pipeline
Agent / Tool Orchestrator
Conversation Memory
Embedding Model
pgvector
Retrieved Context
LLM
Registered Java Tools
Authorized ApplicationServices
PostgreSQL
Response Validation
Question
Application Action
Conversation
7.2 RAG workflow
For document question answering:

Upload PDF
   |
   v
Store original in S3
   |
   v
Extract text
   |
   v
Split into chunks
   |
   v
Generate embeddings
   |
   v
Store in pgvector
   |
   v
User asks question
   |
   v
Semantic retrieval
   |
   v
Tenant-aware document filtering
   |
   v
LLM generates grounded response
   |
   v
Return citations
We should also support document versioning, processing status, reindexing, document access policies, and deletion.

7.3 Tool calling architecture
We will create Java tools for permitted application operations.

Examples:

Tool

Responsibility

searchMyRequests

Retrieve user's requests

createHardwareRequest

Prepare or create hardware request

getRequestDetails

Retrieve request details

searchPendingApprovals

Retrieve authorized pending approvals

searchMyTasks

Retrieve assigned tasks

createSupportTicket

Create IT ticket

generateDepartmentReport

Generate authorized report

The tool layer calls application services.

It never calls repositories directly.

Tool execution policy
Every tool should have:

An input schema.

An authorization policy.

A validation strategy.

A confirmation requirement.

An execution timeout.

An idempotency strategy.

An audit record.

For example, a tool that creates a request may be eligible for execution after user confirmation.

A tool that approves a high-value purchase must verify the authenticated approver and may require additional explicit confirmation.

7.4 Agent orchestration
An agent can coordinate several tools to complete a task.

Example:

"Show overdue IT tickets and recommend who should handle them."

The agent:

Searches overdue tickets.

Retrieves eligible IT engineers.

Gets their current workload.

Produces assignment recommendations.

Presents them to the manager.

Executes assignments only after authorization and confirmation.

Initially, implement a controlled single-agent architecture with bounded tool access.

Do not introduce multi-agent systems just to make the project sound more advanced. They increase complexity and are not necessary for the initial product.

7.5 AI observability
Track:

Model provider.

Model identifier.

Request latency.

Token consumption.

Estimated cost.

Tool calls.

Tool failures.

Retrieval quality.

Citation coverage.

User feedback.

Never put sensitive employee information, raw documents or secrets into ordinary application logs.

8. Technology stack and recommended versions
Since this is intended to be a production-oriented project, we should avoid blindly selecting the newest release or mixing incompatible framework generations.

The following recommendations are based on the official release information available on November 8, 2026.

8.1 Backend technologies
Technology

Recommended version

Purpose

Java

25 LTS

Main language

Spring Boot

4.1.2

Backend framework

Spring Framework

7.x

Core framework

Spring Security

7.1.x

Authentication and authorization

Spring Data JPA

2026.0.x

Persistence

Spring AI

2.0.x

AI integration

Hibernate

Boot-managed

ORM

Maven

3.9.x

Build management

Flyway

Boot-compatible stable

Database migrations

JUnit

Boot-managed

Testing

Mockito

Boot-managed

Mocking

Testcontainers

Compatible stable

Integration testing

Spring Modulith

2.1.x

Module verification

Java 25 is an LTS release, and Spring Boot 4.1.2 officially supports Java 25. Spring AI 2.0 is designed for the Spring Boot 4 generation. 

ops.java
+3

Important compatibility decision: We will use Spring Boot 4.1.x and Spring AI 2.0.x together. We should let Spring Boot's dependency management control Spring Framework, Hibernate, Jackson and other managed dependencies. Avoid manually overriding transitive versions without a compatibility reason.

8.2 Frontend technologies
Technology

Recommended version

React

19.3.x

TypeScript

7.0.x stable

Node.js

26.x LTS

Vite

Latest compatible stable

Tailwind CSS

4.x

React Router

7.x

TanStack Query

5.x

Zustand

5.x

Axios

1.x

React Hook Form

7.x

Zod

4.x

Recharts

3.x

React 19.3 and TypeScript 7.0 are available in the current release information. Node.js 26 has entered LTS. 

React
+2

8.3 Infrastructure technologies
Technology

Recommended version

PostgreSQL

18.6

pgvector

Latest stable compatible with PostgreSQL 18

Redis

8.10.3

Apache Kafka

4.4.0

Docker

Current stable

Docker Compose

Current stable

AWS ECS

Fargate

AWS S3

Managed storage

AWS RDS

PostgreSQL

OpenTelemetry

Current stable

Prometheus

Current stable

Grafana

Current stable

PostgreSQL 18 is currently supported, Kafka 4.4.0 was released in October 2026, and Redis 8.10.3 is available in the official release archive. 

PostgreSQL
+2

For pgvector, pin an extension release explicitly tested with your PostgreSQL version rather than assuming that every pgvector release supports every PostgreSQL major version.

8.4 Recommended AI providers
We should make the model provider configurable.

For example:

Component

Recommendation

Chat LLM

OpenAI API or another supported provider

Embedding model

Dedicated embedding model

Vector store

PostgreSQL + pgvector

AI framework

Spring AI

Document parsing

Apache Tika + PDFBox, with OCR where needed

File storage

AWS S3

We should not tightly couple the application to one LLM provider.

Create an abstraction around model configuration so that providers and models can be changed without rewriting business modules.

Final technology decision
Recommended baseline
Java 25 + Spring Boot 4.1
Spring AI 2.0 + React 19
PostgreSQL 18 + Kafka 4.4
Redis 8 + AWS
Pin exact dependency and container image versions in the project before implementation. Recheck patch releases and compatibility at build time.
9. Complete backend folder structure
I recommend using a feature-oriented modular package structure instead of organizing the entire application into global controller, service and repository folders.

The latter often becomes difficult to maintain as the project grows.

9.1 Backend structure
nexusops-backend/
│
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── README.md
│
├── src/
│   ├── main/
│   │   ├── java/com/nexusops/
│   │   │
│   │   ├── NexusOpsApplication.java
│   │   │
│   │   ├── identity/
│   │   │   ├── api/
│   │   │   ├── application/
│   │   │   ├── domain/
│   │   │   └── infrastructure/
│   │   │
│   │   ├── organization/
│   │   │   ├── api/
│   │   │   ├── application/
│   │   │   ├── domain/
│   │   │   └── infrastructure/
│   │   │
│   │   ├── request/
│   │   │   ├── api/
│   │   │   ├── application/
│   │   │   ├── domain/
│   │   │   └── infrastructure/
│   │   │
│   │   ├── workflow/
│   │   │   ├── api/
│   │   │   ├── application/
│   │   │   ├── domain/
│   │   │   └── infrastructure/
│   │   │
│   │   ├── approval/
│   │   ├── task/
│   │   ├── knowledge/
│   │   ├── ai/
│   │   ├── notification/
│   │   ├── analytics/
│   │   ├── audit/
│   │   ├── platform/
│   │   │
│   │   └── shared/
│   │       ├── security/
│   │       ├── tenant/
│   │       ├── exception/
│   │       ├── events/
│   │       ├── storage/
│   │       ├── observability/
│   │       └── configuration/
│   │
│   └── resources/
│       ├── application.yml
│       ├── application-local.yml
│       ├── application-test.yml
│       ├── application-prod.yml
│       │
│       └── db/
│           └── migration/
│               ├── V1__create_organizations.sql
│               ├── V2__create_identity_tables.sql
│               ├── V3__create_request_tables.sql
│               ├── V4__create_workflow_tables.sql
│               ├── V5__create_approval_tables.sql
│               ├── V6__create_task_tables.sql
│               └── V7__create_ai_knowledge_tables.sql
│
└── src/test/
    ├── java/com/nexusops/
    │   ├── identity/
    │   ├── organization/
    │   ├── request/
    │   ├── workflow/
    │   ├── approval/
    │   ├── task/
    │   └── integration/
    │
    └── resources/
What goes inside each module?
Consider the Request module:

request/
│
├── api/
│   ├── RequestController.java
│   ├── RequestDtos.java
│   └── RequestExceptionHandler.java
│
├── application/
│   ├── RequestService.java
│   ├── RequestSubmissionService.java
│   ├── RequestQueryService.java
│   └── RequestMapper.java
│
├── domain/
│   ├── Request.java
│   ├── RequestStatus.java
│   ├── RequestCategory.java
│   ├── RequestRepository.java
│   └── RequestPolicy.java
│
└── infrastructure/
    ├── persistence/
    │   └── JpaRequestRepository.java
    └── events/
        └── RequestEventPublisher.java
The domain layer contains business concepts and rules.

The application layer orchestrates use cases.

The API layer handles HTTP communication.

The infrastructure layer handles technical implementations.

For a modular monolith, this is a good balance between maintainability and simplicity.

I would also use Spring Modulith to validate module boundaries and document module dependencies.

9.2 Frontend structure
nexusops-frontend/
│
├── package.json
├── tsconfig.json
├── vite.config.ts
├── Dockerfile
│
├── src/
│   ├── app/
│   │   ├── router/
│   │   ├── providers/
│   │   └── layouts/
│   │
│   ├── features/
│   │   ├── authentication/
│   │   ├── organization/
│   │   ├── employees/
│   │   ├── requests/
│   │   ├── workflows/
│   │   ├── approvals/
│   │   ├── tasks/
│   │   ├── knowledge-base/
│   │   ├── ai-assistant/
│   │   ├── analytics/
│   │   └── administration/
│   │
│   ├── components/
│   │   ├── ui/
│   │   ├── common/
│   │   └── layout/
│   │
│   ├── services/
│   │   ├── api-client.ts
│   │   └── endpoints/
│   │
│   ├── hooks/
│   ├── store/
│   ├── types/
│   ├── utils/
│   └── styles/
│
└── tests/
Each frontend feature should contain its own pages, components, API functions, hooks, validation schemas and TypeScript types.

10. Event-driven architecture
Now let's define the events that will travel through Kafka.

We should use domain events to communicate that something meaningful has happened.

10.1 Main domain events
Event

Produced when

ORGANIZATION_CREATED

Company registers

EMPLOYEE_INVITED

Employee invitation sent

REQUEST_SUBMITTED

Employee submits request

REQUEST_CANCELLED

Request cancelled

APPROVAL_CREATED

Approval generated

APPROVAL_COMPLETED

Approval decision made

APPROVAL_REJECTED

Approval rejected

TASK_CREATED

Task generated

TASK_ASSIGNED

Task assigned

TASK_COMPLETED

Task completed

DOCUMENT_UPLOADED

Document received

DOCUMENT_INDEXED

Document indexed successfully

SLA_BREACHED

Deadline missed

10.2 Event flow
Request Module
Transactional Outbox
Kafka
Notification Consumer
Analytics Consumer
Audit Consumer
Workflow Consumer
Email / In-app
Operational Metrics
Audit Records
Workflow Progression
Event contract example
{
  "eventId": "uuid",
  "eventType": "REQUEST_SUBMITTED",
  "eventVersion": 1,
  "organizationId": "uuid",
  "aggregateId": "request-uuid",
  "occurredAt": "2026-11-08T10:00:00Z",
  "correlationId": "uuid",
  "payload": {
    "requestNumber": "REQ-1001",
    "category": "HARDWARE_PURCHASE",
    "requesterId": "user-uuid"
  }
}

We should standardize events using versioned schemas.

Use a Kafka dead-letter topic for messages that cannot be processed after the configured retries.

Also, remember that Kafka does not automatically make database transactions and message publication atomic. That is why the outbox pattern is part of our architecture.

10.3 What should be asynchronous?
Operation

Execution

Create request

Synchronous

Validate permissions

Synchronous

Start workflow

Synchronous within the initial business transaction where practical

Record approval

Synchronous

Send email

Asynchronous

Process uploaded PDF

Asynchronous

Generate embeddings

Asynchronous

Update analytics aggregates

Asynchronous

Generate scheduled reports

Asynchronous

Process SLA reminders

Background processing

A request should be visible immediately after successful creation, even if an email takes a few seconds to send.

11. Frontend application architecture and user experience
We need to decide exactly which screens we are going to implement.

I recommend one shared application shell with role-based navigation and feature-specific pages.

11.1 Employee dashboard
ServiceNow Portal UI Design #1 by Adeel Naeem Naqi on Dribbble | Portal design, Ui design website, Web design typography
Pages:

Overview.

Create Request.

My Requests.

My Tasks.

Notifications.

Nexus AI.

Knowledge Base.

11.2 Manager dashboard
Expensely — Mileage & Expense Approval Dashboard UI/UX Case Stud by Alvin R on Dribbble
Pages:

Team Overview.

Pending Approvals.

Team Requests.

Team Tasks.

Workload.

Analytics.

11.3 Administrator dashboard
HR & Employment Dashboard by Mohaiminul Islam for Tenso UI UX Agency on Dribbble
Pages:

Organization Settings.

Departments.

Employees.

Roles and Permissions.

Request Categories.

Workflow Designer.

Knowledge Base Management.

Audit Logs.

11.4 Workflow designer
This is one UI feature I would particularly like you to implement.

Synergy Codes — AI workflow automation: How intelligent workflows make business processes more profitable?
Use React Flow for the visual graph editor.

An administrator should be able to:

Drag an approval node.

Add a condition node.

Connect nodes.

Configure the approver.

Set the transition condition.

Validate the workflow.

Publish a new workflow version.

The frontend creates a workflow definition.

The backend validates and persists it.

The frontend does not execute workflow logic itself.

11.5 AI assistant UI
Using the Chat Interface - Noema AI Guide
The AI interface should support:

Conversation history.

Streaming responses.

Source citations.

Structured data results.

Action preview cards.

Confirmation buttons.

Error and retry states.

For example, an AI-generated request should be displayed as a structured preview rather than an unformatted text response.

12. Security architecture
Security is not something we add at the end.

It is a cross-cutting part of every module.

12.1 Authentication
Recommended flow:

User enters credentials
       |
       v
Spring Security
       |
       v
Validate credentials
       |
       v
Issue access token
       |
       v
Create secure refresh session
       |
       v
Frontend accesses protected APIs
For production browser authentication:

Secure, HttpOnly refresh/session cookies.

Short-lived access tokens.

Refresh-token rotation.

Session revocation.

CSRF protection where applicable.

Password hashing using a strong password hashing algorithm.

12.2 Authorization
Use RBAC combined with resource-level authorization.

Example:

A user may have permission to view requests, but that does not mean they can view every request.

We must also check ownership, department scope, organization scope and business state.

12.3 Multi-tenancy
I recommend shared-schema multi-tenancy for the capstone.

All organizations share the same PostgreSQL database, but their records are separated using organization_id.

Additional safeguards:

Tenant context is derived from authentication.

Tenant filters are mandatory.

RLS policies provide defense in depth.

Cross-tenant foreign-key relationships are prevented.

Cache keys contain tenant identity.

AI retrieval is tenant-filtered.

Background jobs carry explicit tenant context.

12.4 AI security
AI has additional risks.

We need to protect against:

Prompt injection.

Unauthorized tool execution.

Cross-tenant retrieval.

Sensitive data leakage.

Malicious document instructions.

Excessive tool execution.

Unbounded LLM costs.

Documents must be treated as untrusted data, not as instructions to the AI system.

AI-generated arguments must be validated by Java services.

13. Testing strategy
We should build tests alongside the modules.

Not after completing the application.

Testing type

Technology

What we verify

Unit tests

JUnit + Mockito

Business rules

Integration tests

Spring Boot Test

Module interactions

Database tests

Testcontainers

PostgreSQL behavior

Kafka tests

Testcontainers

Event processing

API tests

MockMvc

HTTP contracts

Security tests

Spring Security Test

Permissions

Frontend tests

Vitest + Testing Library

Components

E2E tests

Playwright

Full user journeys

Load tests

k6

Performance

AI evaluations

Custom evaluation suite

Retrieval and response quality

Mandatory business test scenarios
These should be part of the acceptance test suite:

Employee creates a request.

Unauthorized employee cannot approve another person's request.

Manager approves a request.

Manager rejects a request.

Approver requests additional information.

Workflow advances after approval.

Procurement task is created only after required approvals.

Duplicate submission does not create duplicate requests.

Concurrent approval attempts cannot both succeed.

Cancelled requests stop pending execution.

Workflow version changes do not alter existing instances.

Cross-tenant access is denied.

AI cannot access unauthorized records.

RAG returns tenant-scoped document citations.

Kafka failure does not lose committed requests.

These scenarios will demonstrate engineering quality far better than simply showing a large number of screens.

14. Deployment and AWS architecture
Now let's decide how NexusOps will run outside your local machine.

I recommend Docker-based deployment on AWS.

For the capstone, we don't need Kubernetes. ECS Fargate is sufficient and avoids unnecessary infrastructure complexity.

14.1 Production deployment diagram
AWS Cloud
NexusOps Application
Spring Boot API
AI Orchestration
Background Worker
Application Load Balancer
ECS Fargate
RDS PostgreSQL
ElastiCache Redis
Managed Kafka
S3 Storage
External LLM Provider
Secrets Manager
CloudWatch
Users
CloudFront
React Frontend
AWS services
Service

Responsibility

ECS Fargate

Run backend containers

Application Load Balancer

Distribute API traffic

CloudFront

Frontend delivery

S3

Store documents and attachments

RDS PostgreSQL

Primary database

ElastiCache Redis

Cache

MSK or compatible managed Kafka

Event streaming

Secrets Manager

Store secrets

CloudWatch

Logs and metrics

IAM

Access control

For a cost-conscious capstone, you can initially use Docker Compose for PostgreSQL, Redis and Kafka locally, then deploy the essential application and database components to AWS.

We don't need to pay for every managed service during development.

14.2 CI/CD pipeline
Whenever you push code to GitHub:

Developer pushes code
GitHub Actions
Run unit tests
Run integration tests
Code quality and securitychecks
Build Docker image
Push image to ECR
Deploy to ECS
Health check
Deployment successful
If the health check fails, the deployment should not be treated as successful.

Use automated rollback or ECS deployment circuit-breaker behavior where configured.

15. Development sequence – how we will actually build NexusOps
This is the sequence I recommend following.

One important thing: We will build a working application incrementally, not spend weeks designing every possible module before writing code.

Implementation roadmap
Suggested 10–12 week plan for a focused solo capstone. Actual duration depends on your available hours and testing depth.

Phase 0 – Architecture and setup

Foundation
Finalize architecture and ERD.

Create GitHub repository.

Initialize React and Spring Boot.

Configure Docker Compose.

Configure PostgreSQL and Flyway.

Establish module boundaries.

Deliverable: Running application skeleton.

Phase 1 – Identity and organization

Organization registration.

Login and refresh sessions.

User management.

Departments.

Roles and permissions.

Tenant isolation.

Deliverable: Multiple organizations can securely use NexusOps.

Phase 2 – Request management

Request categories.

Request creation.

Request submission.

Comments and attachments.

Request lifecycle.

Request timeline.

Deliverable: Employees can submit and track requests.

Phase 3 – Workflow and approval engine

Workflow definitions.

Workflow versions.

Approval nodes.

Conditional routing.

Approval decisions.

Rejection and resubmission.

Task generation.

Deliverable: Complete laptop purchase lifecycle.

Phase 4 – Task management

Task assignment.

Task status.

Task comments.

Reassignment.

Deadlines.

SLA monitoring.

Deliverable: Department operators can fulfil requests.

Phase 5 – AI and RAG

Spring AI integration.

Model provider abstraction.

AI chat.

Structured output.

Tool calling.

Document ingestion.

Embeddings and pgvector.

Citation-based answers.

Deliverable: Working Nexus AI assistant.

Phase 6 – Event-driven architecture

Kafka.

Transactional outbox.

Background consumers.

Notification processing.

Retry and dead-letter handling.

Redis caching.

Deliverable: Reliable asynchronous operations.

Phase 7 – Analytics and administration

Role-specific dashboards.

Operational KPIs.

Audit logs.

Workflow designer.

Department analytics.

AI-generated reports.

Deliverable: Complete operational management interface.

Phase 8 – Production hardening

Integration and E2E testing.

Security testing.

Performance testing.

Structured logging.

Observability.

API documentation.

Deliverable: Production-oriented release candidate.

Phase 9 – Deployment

Docker image builds.

AWS configuration.

CI/CD.

Database backups.

Monitoring.

Demo environment.

Deliverable: Publicly accessible NexusOps deployment.

15.1 Recommended development priority
Do not start with Kafka, AI agents, or the workflow visual designer.

Start with the business lifecycle:

Organization
    ↓
Employee
    ↓
Request
    ↓
Workflow
    ↓
Approval
    ↓
Task
    ↓
Completion
Once this lifecycle works correctly, introduce AI and event-driven infrastructure.

Why?

Because AI needs real business capabilities to interact with. Kafka needs real business events to process. Analytics needs actual business data.

Building the foundation first makes every later feature meaningful.

16. Non-functional requirements
These are the quality attributes we should target.

Attribute

Initial engineering target

API response

P95 below 500 ms for ordinary indexed CRUD operations under a defined test load

Availability

Design target of 99.5% for the initial deployment

Tenant isolation

Zero unauthorized cross-tenant access

Data integrity

Transactional consistency for core operations

AI response

Streaming support and measured latency

File upload

Asynchronous processing for large documents

Observability

Traceable request and event correlation

Recovery

Automated database backups and tested restoration

Scalability

Horizontal application scaling

Security

OWASP-oriented security testing

These are proposed engineering targets, not measured performance claims.

For AI operations, response time depends on the model provider, retrieval workload and external network conditions. We should measure it separately.

17. Final architecture decisions
Let's consolidate everything into one architectural decision record.

Area

Final decision

Product

NexusOps

Product model

Multi-tenant enterprise SaaS

Backend architecture

Modular monolith

Java

Java 25 LTS

Backend framework

Spring Boot 4.1.x

Module architecture

Feature-oriented, layered modules

Frontend

React 19 + TypeScript

Frontend tooling

Vite

Database

PostgreSQL 18

ORM

Spring Data JPA / Hibernate

Migrations

Flyway

Authentication

Spring Security

Authorization

RBAC + resource-level authorization

Multi-tenancy

Shared schema + organization ID + RLS

Workflow engine

Custom, dynamic, versioned graph engine

AI

Spring AI 2.0

AI strategy

RAG + tool calling + controlled agents

Vector database

pgvector

Cache

Redis

Messaging

Kafka

File storage

AWS S3

API style

REST + SSE

Testing

JUnit + Mockito + Testcontainers + Playwright

Containerization

Docker

Deployment

AWS ECS Fargate

CI/CD

GitHub Actions

Observability

OpenTelemetry + CloudWatch + metrics

18. What we should deliberately avoid
I want to prevent a few architectural mistakes before they happen.

1. Don't turn every module into a microservice.

One backend application is sufficient initially.

2. Don't create a workflow engine that executes arbitrary code.

Use a restricted, typed workflow definition.

3. Don't give AI direct database access.

AI must interact through authorized Java application services.

4. Don't use Kafka for everything.

Only asynchronous operations and meaningful domain events should use it.

5. Don't store files in PostgreSQL.

Store files in S3 and their metadata in PostgreSQL.

6. Don't put all business logic in controllers.

Controllers should remain thin.

7. Don't use AI to calculate critical business metrics.

Calculate them with SQL and Java; use AI to interpret them.

8. Don't sacrifice tenant isolation for development convenience.

Tenant isolation must be implemented and tested from the beginning.

9. Don't overengineer the MVP.

A complete laptop purchase lifecycle with proper authorization, dynamic approvals, tasks and AI assistance is already a substantial engineering achievement.

19. The actual project deliverables
When we finish NexusOps, I want the repository and demonstration to contain the following.

Final capstone deliverables
Product

Working employee, manager and admin portals.

Multi-tenant organization support.

Request and task management.

Configurable workflow engine.

AI assistant and knowledge base.

Analytics and audit dashboard.

Engineering

Complete Spring Boot source code.

React + TypeScript source code.

Database ERD and Flyway migrations.

OpenAPI specification.

Architecture and sequence diagrams.

Automated tests.

Docker configuration.

CI/CD pipeline.

Documentation

Product Requirements Document.

Software Architecture Document.

Database Design Document.

API documentation.

Security documentation.

Deployment guide.

User guide.

Test report.

20. The next step: Freeze the architecture and design the implementation model
We now have the complete high-level technical architecture.

However, there is one more important design stage before implementation.

We need to convert this architecture into a precise engineering specification.

I suggest that our next deliverable be:

NexusOps – Detailed Database Design & Domain Model (Version 1.0)

It should contain:

Every database table.

Every column and its data type.

Primary and foreign keys.

Unique constraints.

Indexing strategy.

Entity relationships.

JPA entity mapping strategy.

Tenant isolation strategy.

Flyway migration sequence.

Complete ER diagram.

After that, we should prepare the backend implementation specification: module interfaces, service responsibilities, DTOs, API contracts, and transaction boundaries.

My recommendation is to freeze this architecture as the baseline and not start coding until the detailed domain model has been reviewed.

You are building a fairly substantial system. The quality of the domain model will determine how easy the next several weeks of development will be.

Useful official references for our implementation:

Spring Boot documentation

Spring AI documentation

Spring Modulith

PostgreSQL documentation

Apache Kafka documentation

AWS ECS documentation

One principle I want us to follow throughout development: every feature must have a clear business purpose, a well-defined domain model, a secure API, and a testable implementation. That's how we turn NexusOps from an impressive idea into a genuinely good software product.