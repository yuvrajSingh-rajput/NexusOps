# NexusOps – Changelog

All notable changes to the NexusOps platform will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.1.0-design] - 2026-11-09

### Added
- Initialized Git repository and set up root folder structure per Section 5 of `AGENTS.md`.
- Added `AGENTS.md` containing authoritative architectural directives, non-negotiable constraints, and coding standards.
- Added comprehensive Phase 0 technical design documentation under `docs/`:
  - `docs/product/prd-and-user-stories.md`: PRD, 4 core concepts, role personas, user stories with acceptance criteria.
  - `docs/architecture/system-architecture.md`: Governed modular monolith, 12 modules + shared kernel, Spring Modulith boundaries, architecture diagram.
  - `docs/architecture/architecture-decisions.md`: ADRs for modular monolith, tech stack, shared-schema RLS, workflow engine, governed AI.
  - `docs/database/er-diagram-and-schema.md`: Complete Mermaid ERD, 11 table groups, composite tenant FKs, data dictionary, performance indexes, Flyway roadmap.
  - `docs/architecture/workflow-engine-design.md`: Node handlers, typed JSON condition evaluator, state machines, durability, timeout/escalation, optimistic locking.
  - `docs/architecture/ai-rag-and-tools.md`: Grounded RAG, prompt injection defense, governed tools registry, two-phase confirmation flow, graceful degradation.
  - `docs/architecture/security-architecture.md`: Multi-tenant defense in depth, JWT + rotating refresh tokens in HttpOnly cookies, IDOR protection, upload security.
  - `docs/api/api-contracts.md`: REST conventions, standard response envelopes, status codes, endpoint contracts for all modules.
  - `docs/architecture/event-catalogue.md`: Transactional Outbox pattern, standard event envelope, full event catalogue, idempotent consumer registry.
- Added `IMPLEMENTATION_PLAN.md` with 12 phased roadmaps and concrete acceptance gates.
- Added `CURRENT_STATUS.md` tracking the active Phase 0 review gate.
- Added `.env.example` and `.gitignore`.
