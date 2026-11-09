# NexusOps – Architecture Decision Records (ADR)

**Document Version:** 1.0.0  
**Status:** Approved  
**Author:** NexusOps Architecture Team  

---

## ADR 001: Modular Monolith vs Microservices
- **Context:** NexusOps is an enterprise SaaS platform with 12 distinct functional domains.
- **Decision:** Build as a single Spring Boot modular monolith with modules verified using Spring Modulith.
- **Rationale:** 
  1. Local-first development constraint: No cloud infrastructure is available; everything must run on a developer laptop in Docker Compose.
  2. Eliminates distributed transactions, two-phase commits, and network serialization latency across internal domain boundaries.
  3. Spring Modulith enforces package and repository boundaries as strictly as microservices, allowing individual extraction in the future if required.

---

## ADR 002: Java 21 LTS + Spring Boot 3.3.x
- **Context:** Selection of backend language runtime and application framework.
- **Decision:** Java 21 LTS (or Temurin 25 LTS present on local system) and Spring Boot 3.3.5.
- **Rationale:**
  1. Java 21 introduces virtual threads (Loom), record patterns, and sequenced collections.
  2. Spring Boot 3.3 is the primary supported baseline for Spring AI 1.0.0 milestone builds and Spring Modulith 1.2.x.
  3. Spring Boot dependency management ensures mutual compatibility across Hibernate 6, Jackson, Kafka, and Flyway.

---

## ADR 003: Shared-Database, Shared-Schema Multi-Tenancy with Defense in Depth
- **Context:** Multiple organizations will utilize the NexusOps portal.
- **Decision:** Shared database, shared schema with `organization_id` on all tenant-owned tables, reinforced by tenant-aware composite foreign keys and PostgreSQL Row-Level Security (RLS).
- **Rationale:**
  1. Avoids schema sprawl (managing hundreds of identical schemas via Flyway becomes an operational bottleneck).
  2. Composite foreign keys `(organization_id, id)` prevent cross-tenant foreign key injection at the database engine level.
  3. PostgreSQL RLS acts as a secondary defense in depth even if an application query omits tenant filtering.

---

## ADR 004: Visual, Graph-Based Workflow Engine with Typed Conditions
- **Context:** Workflows need to be adaptable by administrators without writing Java code or redeploying the application.
- **Decision:** Directed node graph persisted in PostgreSQL with immutable versioning and strict typed JSON conditions.
- **Rationale:**
  1. Business rules change frequently across organizations.
  2. Security: Executing SpEL, JavaScript, or dynamic code in multi-tenant environments is a catastrophic RCE risk. Typed JSON conditions (`field`, `operator`, `value`) provide zero-injection-risk routing.
  3. Running instances pin their starting `WorkflowVersion`, preventing in-flight process corruption when an admin edits a workflow.

---

## ADR 005: Hybrid Governed AI Architecture with Two-Phase Confirmation
- **Context:** AI co-pilot capabilities (RAG policy answers and operational actions).
- **Decision:** Single bounded agent using Spring AI; RAG answers require verifiable pgvector citations; all mutating write actions return action proposals requiring explicit human confirmation via `/ai/actions/{id}/confirm`.
- **Rationale:**
  1. AI hallucinations or prompt injections cannot be allowed to bypass business authorization or directly approve financial expenditures.
  2. Decoupling AI generation from execution guarantees compliance and auditability.
  3. Complete graceful degradation: if no LLM provider is active, all core operations function without degradation.

---

## ADR 006: Transactional Outbox + Kafka for Asynchronous Processing
- **Context:** Notifications, analytics aggregation, and heavy document processing must not block synchronous user transactions.
- **Decision:** Transactional Outbox pattern committing business records and outbox events in a single PostgreSQL transaction; relayed to Kafka via background worker.
- **Rationale:**
  1. Avoids dual-write inconsistency (e.g. database commits but Kafka is down, or Kafka emits but database rolls back).
  2. Kafka outages do not impact user-facing API operations.

