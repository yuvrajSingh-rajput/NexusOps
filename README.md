# NexusOps – Intelligent Enterprise Operations & Workflow Platform

NexusOps is a commercial-grade, multi-tenant internal operations portal and service orchestration platform. It streamlines and automates how organizations process employee needs—from hardware procurement and software licenses to travel reimbursements and IT support tickets.

---

## 🌟 Key Features

- **Dynamic Request Intake:** Category-driven forms with flexible, versioned JSON schemas.
- **Configurable Workflow Engine:** Visual, graph-based routing supporting multi-stage approvals and conditional branching without code changes.
- **Strict Domain Separation:** Enforces clear boundaries between **Request** (need), **Workflow** (rules), **Approval** (authorization), and **Task** (fulfillment work).
- **Nexus AI Operations Assistant:** A strictly governed co-pilot offering grounded document QA (RAG with citations) and safe operational actions via two-phase human confirmation cards.
- **Multi-Tenant from Day 1:** Complete tenant isolation enforced at the API, service layer, composite database foreign keys, PostgreSQL Row-Level Security (RLS), and vector store.
- **Event-Driven Resilience:** Asynchronous notifications, audit logs, and document chunking powered by a Transactional Outbox and Apache Kafka.
- **Local-First Architecture:** 100% executable and testable on a single laptop via Docker Compose—no cloud or paid accounts required.

---

## 🏗️ Architecture & Technology Stack

- **Backend:** Java 21 LTS, Spring Boot 3.3.x (Spring Security, Spring Data JPA, Spring Modulith, Spring AI, Spring Kafka, Flyway).
- **Frontend:** React 18, TypeScript (strict), Vite, Tailwind CSS, TanStack Query, React Flow, Lucide React.
- **Infrastructure:** Docker Compose (PostgreSQL 16 + pgvector, Redis 7, Apache Kafka 3.8 in KRaft mode, MinIO S3-compatible storage, Mailpit SMTP).
- **Architecture Style:** Governed Modular Monolith with strict module boundary verification.

---

## 📁 Repository Structure

```
NexusOps/
├── AGENTS.md                  # Master project instructions & architectural rules
├── README.md                  # Project overview and quickstart guide
├── CHANGELOG.md               # Version evolution log
├── IMPLEMENTATION_PLAN.md     # 12-phase detailed execution roadmap
├── CURRENT_STATUS.md          # Real-time development progress and phase gates
├── .env.example               # Environment variables template
├── .gitignore                 # Source control ignore rules
├── infrastructure/            # Docker Compose configuration and init scripts
├── nexusops-backend/          # Spring Boot modular monolith backend
├── nexusops-frontend/         # React + TypeScript + Vite frontend
└── docs/                      # Architectural, database, API, and product design docs
    ├── product/               # PRD, user stories, role matrices
    ├── architecture/          # System architecture, engine design, AI, security, events
    ├── database/              # ER diagram, schema data dictionary, Flyway plan
    ├── api/                   # REST API contracts and envelope specifications
    ├── development/           # Local developer setup and testing guides
    └── operations/            # Operational guides and deployment references
```

---

## 🚀 Getting Started

Read [Local Setup Guide](file:///docs/development/local-setup.md) for full prerequisites and instructions.

```bash
# 1. Start local infrastructure services
docker compose -f infrastructure/docker-compose.yml up -d

# 2. Run backend (from IDE or terminal)
cd nexusops-backend
./mvnw spring-boot:run

# 3. Run frontend
cd nexusops-frontend
npm install
npm run dev
```

---

## 📖 Documentation Index

- [PRD & User Stories](file:///docs/product/prd-and-user-stories.md)
- [System Architecture & Boundaries](file:///docs/architecture/system-architecture.md)
- [Database Schema & ER Diagram](file:///docs/database/er-diagram-and-schema.md)
- [Workflow Engine Design](file:///docs/architecture/workflow-engine-design.md)
- [Nexus AI & RAG Architecture](file:///docs/architecture/ai-rag-and-tools.md)
- [Security & Tenant Isolation Model](file:///docs/architecture/security-architecture.md)
- [REST API Contracts](file:///docs/api/api-contracts.md)
- [Event Catalogue & Outbox Architecture](file:///docs/architecture/event-catalogue.md)
- [Master Implementation Plan](file:///IMPLEMENTATION_PLAN.md)
