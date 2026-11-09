# NexusOps – Changelog

All notable changes to the NexusOps platform will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
