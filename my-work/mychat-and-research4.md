yes go ahead 
one thing keep in mind i dont have accesss to cloud or aws right now
NexusOps – Detailed Database Design & Domain Model
Design phase 2 Version 1.0

First, an important adjustment to our architecture:

We will build NexusOps completely locally. AWS and cloud infrastructure are NOT prerequisites for this project.

You should be able to develop, run, test, demonstrate, and evaluate the entire application on your laptop.

In fact, this is a good engineering decision. We can design the application to be cloud-portable without depending on cloud services during development.

1. Updated infrastructure: Local-first development
Our development environment will look like this:

NexusOps local development environment
Your Windows laptop

VS Code · IntelliJ IDEA · Browser · Terminal
Application layer

React

localhost:5173
Spring Boot

localhost:8080
Docker Compose infrastructure

PostgreSQL

Port 5432
Redis

Port 6379
Kafka

Local broker
MinIO

Local S3-compatible storage
Mailpit

Local email testing
LLM

Local or API provider
All infrastructure runs on your laptop. No AWS account or cloud deployment required.
Local technology decisions
Requirement

Local solution

Database

PostgreSQL + pgvector

Cache

Redis

Messaging

Kafka in KRaft mode

File storage

MinIO

Email testing

Mailpit

Backend

Spring Boot

Frontend

React + Vite

AI

Ollama or external LLM API

Containerization

Docker Compose

A few important points:

MinIO provides an S3-compatible interface, so later we can change the storage implementation to AWS S3.

Mailpit catches outgoing emails locally. You can inspect them without sending real emails.

Kafka runs locally in KRaft mode, so we don't need a separate ZooKeeper service.

Ollama is optional. If your laptop has sufficient RAM and hardware, you can run a local model. Otherwise, use a configurable external model API.

pgvector runs inside PostgreSQL.

We will use Docker Compose for infrastructure, while running the Java and React applications directly from your IDE during development.

Our architecture will be cloud-portable, but local-first.

2. Database design philosophy
Before defining tables, let's settle some foundational decisions.

Property

Decision

Database

PostgreSQL

Database architecture

Shared database, shared schema

Primary key

UUID

Tenant identifier

organization_id

ORM

Hibernate / Spring Data JPA

Migration

Flyway

Flexible data

JSONB

Embeddings

pgvector

Concurrency

Optimistic locking

Timezone

UTC

Monetary precision

NUMERIC(19,2)

Why UUID instead of auto-increment IDs?
Suppose one company creates request ID 1, another creates request ID 2, and so on.

Sequential identifiers can expose information about the volume and ordering of records.

UUIDs are more suitable for distributed and multi-tenant applications, and make it easier to generate identifiers without relying on a central sequence.

We will still provide human-readable identifiers:

REQ-1001

REQ-1002

TASK-1001

WF-1001

These are business identifiers, while UUIDs remain the internal primary keys.

Why shared-schema multi-tenancy?
We want many organizations to use the same NexusOps deployment.

Instead of creating a separate database for every company, we use shared tables with organization identifiers.

For example:

organizations
---------------------------------
id                 name
ORG-UUID-01        Acme Technologies
ORG-UUID-02        XYZ Solutions


requests
---------------------------------
id                 organization_id
REQ-UUID-01        ORG-UUID-01
REQ-UUID-02        ORG-UUID-01
REQ-UUID-03        ORG-UUID-02
The backend ensures users only access data belonging to their organization and permitted scope.

We will additionally use PostgreSQL Row-Level Security as a second security layer.

3. Complete database module structure
I am organizing the database into 11 logical groups.

Group

Main responsibility

A. Identity

Users, memberships, roles and permissions

B. Organization

Organizations, departments and teams

C. Requests

Requests, categories, comments and attachments

D. Workflow

Definitions, versions, nodes and transitions

E. Execution

Workflow instances and runtime node states

F. Approvals

Approval records and decision history

G. Tasks

Task assignments, comments and history

H. Knowledge

Documents, versions and embeddings

I. AI

Conversations, messages and tool execution

J. Operations

Notifications, events and scheduled jobs

K. Governance

Audit logs and idempotency records

One improvement to the earlier design: we will separate global user identities from organization memberships.

This means one person can potentially be a member of more than one organization without creating duplicate login identities.

4. Group A – Identity and access management
This is the foundation of NexusOps.

We need to distinguish between a person, their membership in a company, and their permissions within that company.

4.1 users
Stores the global identity of a person.

Column

Data type

Constraints

id

UUID

PK

first_name

VARCHAR(100)

NOT NULL

last_name

VARCHAR(100)

NOT NULL

email

VARCHAR(255)

NOT NULL, UNIQUE

password_hash

VARCHAR(255)

Nullable for external identity

status

VARCHAR(30)

NOT NULL

email_verified

BOOLEAN

DEFAULT FALSE

created_at

TIMESTAMPTZ

NOT NULL

updated_at

TIMESTAMPTZ

NOT NULL

Possible user statuses:

ACTIVE, INVITED, SUSPENDED, DEACTIVATED.

The email should be normalized before storage and uniqueness checks.

4.2 organization_memberships
This is an important table.

It represents the relationship between a user and an organization.

Column

Data type

id

UUID PK

organization_id

UUID FK

user_id

UUID FK

department_id

UUID FK, nullable

manager_membership_id

UUID, nullable

employee_code

VARCHAR(50)

designation

VARCHAR(100)

status

VARCHAR(30)

joined_at

TIMESTAMPTZ

created_at

TIMESTAMPTZ

Unique constraint:

(organization_id, user_id)

A user can have multiple organization memberships, but only one membership per organization.

The manager relationship should reference another membership within the same organization.

For example:

User: Rahul
    |
    +-- Acme Technologies membership
    |       Department: Engineering
    |       Manager: Priya
    |
    +-- XYZ Solutions membership
            Department: Consulting
            Manager: Amit
4.3 roles
Defines roles inside an organization.

Column

Data type

id

UUID PK

organization_id

UUID FK

name

VARCHAR(100)

code

VARCHAR(50)

description

TEXT

is_system_role

BOOLEAN

created_at

TIMESTAMPTZ

Example role codes:

EMPLOYEE

MANAGER

FINANCE_OPERATOR

IT_OPERATOR

HR_OPERATOR

ORGANIZATION_ADMIN

EXECUTIVE

4.4 permissions
Defines granular application permissions.

Column

Data type

id

UUID PK

code

VARCHAR(100) UNIQUE

description

TEXT

module

VARCHAR(50)

Examples:

REQUEST_CREATE
REQUEST_VIEW_OWN
REQUEST_VIEW_DEPARTMENT
REQUEST_APPROVE
TASK_ASSIGN
TASK_COMPLETE
WORKFLOW_CREATE
WORKFLOW_PUBLISH
EMPLOYEE_INVITE
AUDIT_VIEW
4.5 membership_roles
Column

Data type

id

UUID PK

organization_id

UUID

membership_id

UUID

role_id

UUID

assigned_by

UUID

assigned_at

TIMESTAMPTZ

Unique:

(organization_id, membership_id, role_id)

4.6 role_permissions
Column

Data type

role_id

UUID FK

permission_id

UUID FK

Composite primary key:

(role_id, permission_id).

This gives us:

User
  |
  v
Organization Membership
  |
  v
Membership Roles
  |
  v
Roles
  |
  v
Role Permissions
  |
  v
Permissions
A user's effective permissions are determined through this relationship and additional resource-level authorization.

5. Group B – Organization management
5.1 organizations
Column

Data type

id

UUID PK

name

VARCHAR(200)

slug

VARCHAR(100) UNIQUE

email_domain

VARCHAR(255), nullable

industry

VARCHAR(100)

status

VARCHAR(30)

timezone

VARCHAR(100)

created_at

TIMESTAMPTZ

updated_at

TIMESTAMPTZ

5.2 departments
Column

Data type

id

UUID PK

organization_id

UUID

name

VARCHAR(150)

code

VARCHAR(50)

description

TEXT

manager_membership_id

UUID, nullable

parent_department_id

UUID, nullable

status

VARCHAR(30)

created_at

TIMESTAMPTZ

Unique:

(organization_id, code).

This supports departmental hierarchies.

For example:

Acme Technologies
│
├── Engineering
│   ├── Backend
│   └── Frontend
│
├── Finance
│
├── IT
│
└── HR
5.3 teams
Teams are optional subdivisions within departments.

Column

Data type

id

UUID PK

organization_id

UUID

department_id

UUID

name

VARCHAR(150)

description

TEXT

created_at

TIMESTAMPTZ

5.4 team_memberships
Column

Data type

id

UUID PK

organization_id

UUID

team_id

UUID

membership_id

UUID

joined_at

TIMESTAMPTZ

A user can belong to multiple teams within an organization.

6. Group C – Request management
This is the central business module.

6.1 request_categories
Defines which types of requests an organization supports.

Column

Data type

id

UUID PK

organization_id

UUID

name

VARCHAR(150)

code

VARCHAR(60)

description

TEXT

payload_schema

JSONB

default_priority

VARCHAR(20)

requires_workflow

BOOLEAN

is_active

BOOLEAN

created_at

TIMESTAMPTZ

Examples:

Code

Name

HARDWARE_PURCHASE

Hardware Purchase

SOFTWARE_ACCESS

Software Access

EXPENSE

Expense Reimbursement

IT_SUPPORT

IT Support

ONBOARDING

Employee Onboarding

The payload_schema defines which fields are required for a specific category.

For example, hardware requests require an item and estimated amount.

Software requests require software name and access level.

The schema is versioned or snapshotted at submission so that future category changes don't silently invalidate historical requests.

6.2 requests
Core business entity
Column

Data type

Purpose

id

UUID PK

Internal identifier

organization_id

UUID

Tenant

request_number

VARCHAR(50)

REQ-1001

category_id

UUID

Category

requester_membership_id

UUID

Request creator

department_id

UUID

Requester's department at submission

title

VARCHAR(255)

Request title

description

TEXT

Description

priority

VARCHAR(20)

Priority

status

VARCHAR(30)

Lifecycle status

payload

JSONB

Category-specific data

schema_snapshot

JSONB

Validation schema at submission

submitted_at

TIMESTAMPTZ

Submission timestamp

completed_at

TIMESTAMPTZ

Completion timestamp

created_at

TIMESTAMPTZ

Creation

updated_at

TIMESTAMPTZ

Modification

version

BIGINT

Optimistic lock

Possible request statuses:

DRAFT
SUBMITTED
IN_REVIEW
WAITING_FOR_INFORMATION
APPROVED
IN_PROGRESS
ON_HOLD
REJECTED
CANCELLED
COMPLETED
Not every status transition should be allowed from every other status. The Request domain service will enforce the permitted transition rules.

6.3 request_comments
Column

Data type

id

UUID PK

organization_id

UUID

request_id

UUID

author_membership_id

UUID

comment

TEXT

visibility

VARCHAR(20)

created_at

TIMESTAMPTZ

Visibility can be:

INTERNAL

REQUESTER_VISIBLE

This allows private internal notes from departments without exposing them to employees.

6.4 request_attachments
Column

Data type

id

UUID PK

organization_id

UUID

request_id

UUID

uploaded_by_membership_id

UUID

storage_key

VARCHAR(500)

original_filename

VARCHAR(255)

content_type

VARCHAR(150)

size_bytes

BIGINT

checksum

VARCHAR(128)

created_at

TIMESTAMPTZ

The actual file goes to MinIO locally.

PostgreSQL stores its metadata and storage key.

7. Group D – Dynamic workflow definition
This group stores the administrator's workflow configuration.

We must distinguish between a workflow template and its actual execution.

7.1 workflow_definitions
Represents the logical identity of a workflow.

Column

Data type

id

UUID PK

organization_id

UUID

name

VARCHAR(200)

code

VARCHAR(100)

description

TEXT

category_id

UUID

status

VARCHAR(30)

created_by_membership_id

UUID

created_at

TIMESTAMPTZ

updated_at

TIMESTAMPTZ

Example:

Laptop Purchase Approval

7.2 workflow_versions
Every published change creates a new version.

Column

Data type

id

UUID PK

organization_id

UUID

workflow_definition_id

UUID

version_number

INTEGER

status

VARCHAR(30)

definition_snapshot

JSONB

published_at

TIMESTAMPTZ

published_by_membership_id

UUID

created_at

TIMESTAMPTZ

Statuses:

DRAFT, PUBLISHED, RETIRED.

Unique:

(workflow_definition_id, version_number).

Published versions must be immutable.

The snapshot preserves the exact workflow definition used during execution.

7.3 workflow_nodes
Defines the individual nodes within a workflow version.

Column

Data type

id

UUID PK

organization_id

UUID

workflow_version_id

UUID

node_key

VARCHAR(100)

node_type

VARCHAR(40)

name

VARCHAR(150)

configuration

JSONB

position_x

NUMERIC(10,2)

position_y

NUMERIC(10,2)

Node types:

START
END
APPROVAL
TASK
CONDITION
NOTIFICATION
DELAY
PARALLEL
JOIN
AUTOMATION
The position fields are used by the React Flow visual editor.

7.4 workflow_transitions
Defines how execution moves between nodes.

Column

Data type

id

UUID PK

organization_id

UUID

workflow_version_id

UUID

source_node_id

UUID

target_node_id

UUID

condition_expression

JSONB

priority

INTEGER

created_at

TIMESTAMPTZ

Why JSONB for conditions?

Because we want typed, structured rules.

For example:

{
  "field": "payload.estimatedAmount",
  "operator": "GREATER_THAN",
  "value": 50000
}

This is safer than allowing arbitrary code to execute.

For a workflow with multiple outgoing transitions, the engine should have explicit default routing and deterministic condition priority.

8. Group E – Workflow runtime execution
This group records what actually happens when a workflow runs.

This is separate from the definition tables.

8.1 workflow_instances
Column

Data type

id

UUID PK

organization_id

UUID

workflow_definition_id

UUID

workflow_version_id

UUID

request_id

UUID

attempt_number

INTEGER

status

VARCHAR(30)

started_at

TIMESTAMPTZ

completed_at

TIMESTAMPTZ

created_at

TIMESTAMPTZ

version

BIGINT

Example:

Rahul submits REQ-1001.

NexusOps starts workflow instance WF-INSTANCE-001.

It uses workflow version 1.

A request may have multiple workflow attempts if resubmission is supported. We will permit only one active execution per request at a time.

8.2 workflow_node_instances
Column

Data type

id

UUID PK

organization_id

UUID

workflow_instance_id

UUID

workflow_node_id

UUID

execution_status

VARCHAR(30)

entered_at

TIMESTAMPTZ

completed_at

TIMESTAMPTZ

result

JSONB

version

BIGINT

Possible execution states:

PENDING, READY, RUNNING, WAITING, COMPLETED, FAILED, SKIPPED, CANCELLED.

This table is essential.

Suppose Rahul's laptop workflow has:

Manager approval: Completed.

Finance approval: Pending.

Procurement: Not started.

These states are stored in runtime node instances.

We never need to infer execution state solely from the workflow definition.

9. Group F – Approval management
9.1 approval_instances
Each approval node creates one or more approval instances.

Column

Data type

id

UUID PK

organization_id

UUID

workflow_node_instance_id

UUID

approver_membership_id

UUID

status

VARCHAR(30)

assigned_at

TIMESTAMPTZ

due_at

TIMESTAMPTZ

decided_at

TIMESTAMPTZ

comments

TEXT

version

BIGINT

Statuses:

PENDING, APPROVED, REJECTED, REQUESTED_INFO, CANCELLED, EXPIRED.

9.2 approval_actions
Stores every decision and action.

Column

Data type

id

UUID PK

organization_id

UUID

approval_instance_id

UUID

actor_membership_id

UUID

action_type

VARCHAR(30)

comments

TEXT

created_at

TIMESTAMPTZ

Examples:

APPROVE
REJECT
REQUEST_INFORMATION
REASSIGN
DELEGATE
Why have both tables?

approval_instances tells us the current state.

approval_actions tells us the history of how the state was reached.

For example:

Approval: APPROVED

History:
1. Assigned to Priya
2. Information requested
3. Rahul supplied information
4. Priya approved
We preserve this history.

10. Group G – Task management
10.1 tasks
Column

Data type

id

UUID PK

organization_id

UUID

task_number

VARCHAR(50)

request_id

UUID, nullable

workflow_instance_id

UUID, nullable

workflow_node_instance_id

UUID, nullable

title

VARCHAR(255)

description

TEXT

assigned_to_membership_id

UUID

assigned_by_membership_id

UUID

status

VARCHAR(30)

priority

VARCHAR(20)

due_at

TIMESTAMPTZ

completed_at

TIMESTAMPTZ

created_at

TIMESTAMPTZ

updated_at

TIMESTAMPTZ

version

BIGINT

Task statuses:

OPEN, IN_PROGRESS, BLOCKED, COMPLETED, CANCELLED.

A task can originate from a workflow or be manually created.

10.2 task_comments
Column

Data type

id

UUID PK

organization_id

UUID

task_id

UUID

author_membership_id

UUID

comment

TEXT

created_at

TIMESTAMPTZ

10.3 task_history
Column

Data type

id

UUID PK

organization_id

UUID

task_id

UUID

actor_membership_id

UUID

action_type

VARCHAR(50)

old_value

JSONB

new_value

JSONB

created_at

TIMESTAMPTZ

For example, if Karan is replaced by Amit, the reassignment is recorded in this table.

11. Group H – Knowledge management and RAG
This group powers the company knowledge assistant.

We will store original documents separately from extracted text and vector embeddings.

11.1 documents
Stores document metadata.

Column

Data type

id

UUID PK

organization_id

UUID

title

VARCHAR(255)

description

TEXT

document_type

VARCHAR(50)

current_version_id

UUID, nullable

status

VARCHAR(30)

uploaded_by_membership_id

UUID

created_at

TIMESTAMPTZ

updated_at

TIMESTAMPTZ

Example documents:

Employee Handbook.

IT Security Policy.

Reimbursement Policy.

Procurement Guidelines.

11.2 document_versions
A document may be updated over time.

Column

Data type

id

UUID PK

organization_id

UUID

document_id

UUID

version_number

INTEGER

storage_key

VARCHAR(500)

checksum

VARCHAR(128)

mime_type

VARCHAR(150)

size_bytes

BIGINT

processing_status

VARCHAR(30)

uploaded_at

TIMESTAMPTZ

Possible processing statuses:

UPLOADED, PROCESSING, INDEXED, FAILED, RETIRED.

This allows us to retain document history.

11.3 document_chunks
This table is used by RAG.

Column

Data type

id

UUID PK

organization_id

UUID

document_version_id

UUID

chunk_index

INTEGER

content

TEXT

page_number

INTEGER

embedding

VECTOR

metadata

JSONB

created_at

TIMESTAMPTZ

The vector dimension must match the selected embedding model.

For example, if we select a 1536-dimensional embedding model, we can use VECTOR(1536).

We should select and pin the embedding model before creating the production vector schema.

How it works
Reimbursement_Policy.pdf
          |
          v
      MinIO Storage
          |
          v
    Document Processing
          |
          v
      Text Extraction
          |
          v
      Text Chunking
          |
          v
     Embedding Model
          |
          v
     document_chunks
          |
          v
        pgvector
Important design rule: the vector retrieval query must include the user's organization and document-access scope. Semantic similarity alone is not authorization.

12. Group I – AI conversation and execution
This group records the interaction between users and Nexus AI.

12.1 ai_conversations
Column

Data type

id

UUID PK

organization_id

UUID

user_membership_id

UUID

title

VARCHAR(255)

status

VARCHAR(30)

created_at

TIMESTAMPTZ

updated_at

TIMESTAMPTZ

12.2 ai_messages
Column

Data type

id

UUID PK

organization_id

UUID

conversation_id

UUID

role

VARCHAR(20)

content

TEXT

model_name

VARCHAR(100)

token_usage

JSONB

created_at

TIMESTAMPTZ

Roles:

USER

ASSISTANT

SYSTEM

TOOL

We should be careful about storing system prompts and sensitive tool output in conversation history.

12.3 ai_tool_executions
This is particularly important for our AI governance.

Column

Data type

id

UUID PK

organization_id

UUID

conversation_id

UUID

message_id

UUID

tool_name

VARCHAR(150)

arguments

JSONB

status

VARCHAR(30)

confirmation_required

BOOLEAN

confirmed_by_membership_id

UUID, nullable

confirmed_at

TIMESTAMPTZ

result_summary

JSONB

error_code

VARCHAR(100), nullable

executed_at

TIMESTAMPTZ

created_at

TIMESTAMPTZ

Possible states:

PROPOSED, AWAITING_CONFIRMATION, CONFIRMED, EXECUTING, SUCCEEDED, FAILED, REJECTED, EXPIRED.

For a high-risk action, the user must explicitly confirm it.

We should also record the authenticated actor and the authorization decision. The LLM must never be the source of user identity.

13. Group J – Notifications and asynchronous processing
13.1 notifications
Column

Data type

id

UUID PK

organization_id

UUID

recipient_membership_id

UUID

title

VARCHAR(255)

message

TEXT

notification_type

VARCHAR(50)

reference_type

VARCHAR(50)

reference_id

UUID

read_at

TIMESTAMPTZ, nullable

created_at

TIMESTAMPTZ

Examples:

Request submitted.

Approval required.

Request approved.

Task assigned.

SLA deadline approaching.

13.2 notification_preferences
Column

Data type

id

UUID PK

organization_id

UUID

membership_id

UUID

channel

VARCHAR(30)

event_type

VARCHAR(100)

enabled

BOOLEAN

Channels:

IN_APP

EMAIL

13.3 outbox_events
This table makes our event publishing reliable.

Column

Data type

id

UUID PK

organization_id

UUID

aggregate_type

VARCHAR(100)

aggregate_id

UUID

event_type

VARCHAR(150)

event_version

INTEGER

payload

JSONB

status

VARCHAR(30)

occurred_at

TIMESTAMPTZ

published_at

TIMESTAMPTZ

retry_count

INTEGER

The business operation and outbox record are committed in the same database transaction.

A background publisher reads unpublished records and sends them to Kafka.

13.4 processed_events
Consumers need to prevent duplicate processing.

Column

Data type

id

UUID PK

consumer_name

VARCHAR(150)

event_id

UUID

processed_at

TIMESTAMPTZ

Unique constraint:

(consumer_name, event_id).

This helps make event processing idempotent.

13.5 scheduled_jobs
We need this for workflow timeouts and SLA reminders.

Column

Data type

id

UUID PK

organization_id

UUID

job_type

VARCHAR(100)

reference_type

VARCHAR(100)

reference_id

UUID

scheduled_at

TIMESTAMPTZ

status

VARCHAR(30)

retry_count

INTEGER

last_error

TEXT

created_at

TIMESTAMPTZ

Why store scheduled jobs in PostgreSQL?

Because an in-memory Java scheduler loses its pending work if the application restarts.

A durable job table allows us to recover pending work.

For the initial version, we can implement a polling worker with row locking and SKIP LOCKED.

Later, we can replace it with a more sophisticated job scheduling system if necessary.

14. Group K – Audit and reliability
14.1 audit_logs
This records important actions throughout NexusOps.

Column

Data type

id

UUID PK

organization_id

UUID

actor_membership_id

UUID, nullable

actor_type

VARCHAR(30)

action

VARCHAR(150)

resource_type

VARCHAR(100)

resource_id

UUID

before_state

JSONB

after_state

JSONB

ip_address

INET, nullable

correlation_id

UUID

created_at

TIMESTAMPTZ

Examples:

REQUEST_CREATED
REQUEST_SUBMITTED
APPROVAL_GRANTED
APPROVAL_REJECTED
TASK_REASSIGNED
WORKFLOW_PUBLISHED
AI_TOOL_EXECUTED
USER_ROLE_CHANGED
We should avoid storing passwords, tokens, full confidential documents, or unnecessary personal data in audit records.

14.2 idempotency_records
Protects APIs against accidental duplicate submissions.

Column

Data type

id

UUID PK

organization_id

UUID

idempotency_key

VARCHAR(255)

operation

VARCHAR(150)

request_hash

VARCHAR(128)

response_status

INTEGER

response_body

JSONB

created_at

TIMESTAMPTZ

expires_at

TIMESTAMPTZ

Unique:

(organization_id, idempotency_key, operation).

For example, if Rahul double-clicks Submit, the application should not create two identical requests.

The server should return the previously recorded result for a repeated matching request.

15. Complete logical ER relationship diagram
We now have the actual database groups.

Let's visualize how the most important relationships connect.

ORGANIZATIONS
ORGANIZATION_MEMBERSHIPS
USERS
DEPARTMENTS
MEMBERSHIP_ROLES
ROLES
ROLE_PERMISSIONS
PERMISSIONS
REQUEST_CATEGORIES
REQUESTS
REQUEST_COMMENTS
REQUEST_ATTACHMENTS
WORKFLOW_DEFINITIONS
WORKFLOW_VERSIONS
WORKFLOW_NODES
WORKFLOW_TRANSITIONS
WORKFLOW_INSTANCES
WORKFLOW_NODE_INSTANCES
APPROVAL_INSTANCES
APPROVAL_ACTIONS
TASKS
TASK_COMMENTS
TASK_HISTORY
DOCUMENTS
DOCUMENT_VERSIONS
DOCUMENT_CHUNKS
AI_CONVERSATIONS
AI_MESSAGES
AI_TOOL_EXECUTIONS
NOTIFICATIONS
AUDIT_LOGS
OUTBOX_EVENTS
has
joins
contains
includes
assigned
grants
contains
defines
defines
owns
categorizes
creates
contains
contains
triggers
versions
contains
connects
executes
uses
runs
generates
records
generates
contains
records
versions
indexes
starts
contains
invokes
receives
records
publishes
This diagram deliberately omits some technical relationships for readability, but it represents the central domain structure.

16. Important entity relationships and JPA mapping strategy
Now let's translate the database relationships into the Java domain model.

Since you're learning Spring Boot, I want you to understand not only which entity references another entity, but also why we choose particular relationship mappings.

16.1 Relationship mapping
Relationship

JPA mapping

Example

One-to-many

@OneToMany

Organization → Departments

Many-to-one

@ManyToOne

Request → Requester

One-to-one

@OneToOne

Document → Current version, if modelled as a separate reference

Many-to-many

Through join entity

Membership → Roles

Self-referencing

@ManyToOne

User membership → Manager membership

I recommend using explicit join entities instead of direct many-to-many mappings for relationships that have metadata.

For example, membership_roles has assigned_by and assigned_at, so it should be its own entity.

16.2 Example: Request relationships
A request belongs to:

One organization.

One request category.

One requester membership.

One department snapshot.

A request can have:

Many comments.

Many attachments.

Multiple workflow execution attempts.

Multiple tasks.

Conceptually:

Request
  |
  |---- Many RequestComments
  |
  |---- Many RequestAttachments
  |
  |---- Many WorkflowInstances
  |
  |---- Many Tasks
For JPA, use lazy relationships by default.

Avoid automatically fetching every relationship whenever a request is retrieved.

For example, loading a request list should not automatically load all its comments, attachments, approvals, and tasks.

Use DTO projections and explicit query fetching instead.

16.3 Important JPA rules
I recommend:

Use FetchType.LAZY for most associations.

Avoid unnecessary bidirectional mappings.

Avoid Lombok @Data on JPA entities because generated equals, hashCode and toString can cause relationship recursion and lazy-loading problems.

Use explicit constructors or controlled entity creation methods.

Use @Version for optimistic locking.

Use @Enumerated(EnumType.STRING) for enums.

Avoid CascadeType.ALL without careful ownership analysis.

Avoid automatic orphan deletion for audit-sensitive records.

One particularly important decision:

Do not use JPA entities as API request or response objects.

The architecture should remain:

HTTP Request
     |
     v
Request DTO
     |
     v
Controller
     |
     v
Application Service
     |
     v
Domain Entity
     |
     v
Repository
     |
     v
PostgreSQL
This prevents database implementation details from leaking into the frontend.

17. Database integrity and constraints
A production-grade database is not just a collection of tables.

It must prevent invalid relationships and states.

Here are the constraints I want in NexusOps.

Constraint

Why it matters

Unique organization slug

Prevent duplicate company identifiers

Unique organization email membership

Prevent duplicate memberships

Unique request number per organization

Reliable business identification

Unique workflow version

Prevent duplicate versions

Valid workflow node references

Prevent broken workflow graphs

Unique team membership

Prevent duplicate team assignments

Valid task assignee membership

Prevent cross-company assignment

Valid approval membership

Prevent unauthorized cross-company approvers

Unique processed event

Prevent duplicate event processing

Unique idempotency key

Prevent duplicate API execution

Tenant-aware foreign keys
This deserves special attention.

Suppose:

Request A belongs to Acme.

Employee B belongs to XYZ Solutions.

We must never allow Request A to reference Employee B.

A normal foreign key to a UUID alone doesn't express the tenant relationship.

Instead, use composite constraints.

For example, organization_memberships should have:

UNIQUE (organization_id, id)

Then requests can reference the membership using:

FOREIGN KEY (organization_id, requester_membership_id)
REFERENCES organization_memberships (organization_id, id)

This enforces organization consistency at the database level.

Apply this pattern to requesters, approvers, task assignees, comments, and other tenant-owned relationships.

We should also enforce:

Non-negative monetary amounts.

Valid due dates.

Valid workflow version numbers.

Unique node keys within a workflow version.

No self-referencing or invalid workflow transitions.

Valid request and task status values.

The workflow graph itself needs application-level validation in addition to database constraints.

18. Request and workflow lifecycle state design
We should standardize the lifecycle states before writing service classes.

Request lifecycle
DRAFT
SUBMITTED
CANCELLED
IN_REVIEW
WAITING_FOR_INFORMATION
APPROVED
REJECTED
IN_PROGRESS
ON_HOLD
COMPLETED
We must distinguish approval completion from overall request completion.

For example, a request can be approved but not yet fulfilled.

That is why APPROVED and COMPLETED are separate states.

Workflow instance lifecycle
CREATED
   |
   v
RUNNING
   |
   +---- WAITING
   |
   +---- SUSPENDED
   |
   +---- COMPLETED
   |
   +---- REJECTED
   |
   +---- CANCELLED
   |
   +---- FAILED
Approval lifecycle
PENDING
   |
   +---- APPROVED
   |
   +---- REJECTED
   |
   +---- REQUESTED_INFO
   |
   +---- EXPIRED
   |
   +---- CANCELLED
Task lifecycle
OPEN
  |
  v
IN_PROGRESS
  |
  +---- BLOCKED
  |
  +---- COMPLETED
  |
  +---- CANCELLED
The Java domain services should enforce legal transitions.

We should never let the frontend directly change a request from SUBMITTED to COMPLETED just by sending a status value.

19. Flyway migration sequence
We will not manually create database tables using pgAdmin every time we change the application.

We will use Flyway.

The migration files will be version-controlled.

Recommended sequence:

Migration

Tables

V1

Organizations

V2

Users and memberships

V3

Departments and teams

V4

Roles and permissions

V5

Request categories

V6

Requests, comments and attachments

V7

Workflow definitions and versions

V8

Workflow nodes and transitions

V9

Workflow instances and node instances

V10

Approval instances and actions

V11

Tasks, comments and history

V12

Documents and document versions

V13

Document chunks and pgvector

V14

AI conversations and messages

V15

AI tool executions

V16

Notifications

V17

Outbox and processed events

V18

Scheduled jobs

V19

Audit and idempotency

V20

Indexes, tenant constraints and RLS policies

For development, we can add more migrations as the schema evolves.

A migration should not be edited after it has been applied to a shared environment. Create a new migration instead.

19.1 Local PostgreSQL configuration
We will create a database named:

nexusops_db

Initial PostgreSQL configuration:

database:
  name: nexusops_db
  host: localhost
  port: 5432
  username: nexusops
  password: local-dev-secret

extensions:
  - vector
  - pgcrypto

The password above is only an illustrative local-development value. It must be stored in an environment file excluded from Git, not committed in the project.

For pgvector, use a PostgreSQL container image that already includes the compatible extension rather than assuming the default PostgreSQL image contains it.

20. Updated local folder structure
Since you don't have cloud access, I want the repository structure to reflect the local development environment.

NexusOps/
│
├── nexusops-backend/
│
├── nexusops-frontend/
│
├── infrastructure/
│   ├── docker-compose.yml
│   ├── postgres/
│   │   └── init.sql
│   ├── kafka/
│   ├── redis/
│   ├── minio/
│   └── mailpit/
│
├── docs/
│   ├── product/
│   │   └── PRD.md
│   │
│   ├── architecture/
│   │   ├── system-architecture.md
│   │   ├── module-boundaries.md
│   │   └── workflow-engine.md
│   │
│   ├── database/
│   │   ├── er-diagram.md
│   │   ├── data-dictionary.md
│   │   └── relationship-design.md
│   │
│   ├── api/
│   │   └── api-contracts.md
│   │
│   └── deployment/
│       └── local-setup.md
│
├── .env.example
├── .gitignore
└── README.md
This gives us a clean separation:

Application source code.

Infrastructure configuration.

Technical documentation.

Environment configuration.

We will eventually add production deployment configurations, but they won't be required to run the project.

21. How to run the complete infrastructure locally
Let's make sure this is practical on your laptop.

You already have experience with Java, Spring Boot, React, Docker, MySQL and backend development. We will use those skills, but move our primary database to PostgreSQL.

Required software
Software

Why we need it

JDK 25

Java development

IntelliJ IDEA / VS Code

IDE

Maven 3.9.x

Build management

Node.js 26 LTS

React development

Docker Desktop

Run infrastructure

PostgreSQL 18 + pgvector

Database

Git

Version control

Postman

API testing

Docker Compose services
Our local infrastructure will contain:

Container

Purpose

Port

PostgreSQL

Main database

5432

Redis

Caching

6379

Kafka

Event messaging

9092

MinIO

File storage

9000

MinIO Console

Storage management

9001

Mailpit SMTP

Email testing

1025

Mailpit UI

View emails

8025

The backend will run on port 8080 and frontend on 5173.

You will be able to start infrastructure with:

docker compose up -d

And stop it with:

docker compose down

Your database data should be stored in persistent Docker volumes so that stopping containers doesn't erase your development data.

What about AI without cloud access?
We have two options:

Option A – Local AI

Run Ollama with a compatible chat and embedding model.

Advantages:

No cloud account.

No per-request API charges.

Your prompts and documents can remain local.

Disadvantages:

Requires sufficient RAM and potentially GPU resources.

Model quality and response speed depend on your hardware.

Option B – External model API

Use a provider API through Spring AI.

Advantages:

Generally easier to obtain strong model performance.

Less hardware required.

Disadvantages:

Requires API access and potentially paid usage.

Requires appropriate handling of sensitive company data.

We will create a provider abstraction so that the rest of NexusOps doesn't care which model is being used.

For the initial project, I recommend keeping AI provider configuration optional. The core application must work even if no LLM is available.

22. Important design decisions to freeze
Before implementation, here is the database model's final set of decisions.

Database design freeze – v1.0
Database
PostgreSQL

Primary identifiers
UUID

Tenant model
Shared schema

Tenant protection
RLS + application checks

Request payloads
JSONB

Workflow definitions
Versioned graph

Workflow execution
Persisted runtime instances

Approval history
Separate action records

Task history
Separate history records

Document storage
MinIO locally

Vector storage
pgvector

Event reliability
Transactional outbox

Scheduled operations
Persistent job table

Deployment
Local Docker Compose

Cloud-independent development
No AWS account, cloud database, or paid hosting is needed for development and demonstration.

23. What comes next?
We have completed the conceptual database architecture and its domain relationships.

However, before creating Java entities, I want to take one more step: convert this design into an implementation-ready data dictionary and validate the difficult relationships.

My proposed next sequence is:

Step

Deliverable

1

Detailed ER diagram with all entities and cardinalities

2

Complete SQL schema with constraints and indexes

3

Flyway migration files V1–V20

4

Java JPA entity mapping specification

5

Repository and module interface design

6

Transaction boundaries and service responsibilities

One architectural recommendation: We should implement and test Identity, Organization and Request Management first. Only then should we implement the dynamic workflow engine. The workflow engine depends on these domain concepts being stable.

And throughout the entire project, we will use your laptop as our development environment. AWS will remain an optional future deployment target, never a blocker.

Our next major milestone is a database schema that can actually be translated into PostgreSQL migrations and Spring Boot entities without having to redesign it halfway through development.

