-- ==============================================================================
-- NexusOps Migration V4: Request Management Schema
-- Multi-Tenant Request Categories, Requests, Comments, Attachments, and Sequence
-- ==============================================================================

-- 1. Tenant Sequence Tracking (for human-readable REQ-1001, TASK-1001 counters)
CREATE TABLE IF NOT EXISTS tenant_sequences (
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    entity_type VARCHAR(30) NOT NULL,
    current_val BIGINT NOT NULL DEFAULT 1000,
    PRIMARY KEY (organization_id, entity_type)
);

-- 2. Request Categories (Dynamic Payload Schema Definitions)
CREATE TABLE IF NOT EXISTS request_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    icon VARCHAR(50) NOT NULL DEFAULT 'file-text',
    payload_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    schema_version INT NOT NULL DEFAULT 1,
    requires_workflow BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_cat_org_code UNIQUE (organization_id, code),
    CONSTRAINT uq_cat_org_id UNIQUE (organization_id, id)
);

-- 3. Requests (Intake Need, Snapshot Schema & Dynamic JSONB Payload)
CREATE TABLE IF NOT EXISTS requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    request_number VARCHAR(30) NOT NULL,
    category_id UUID NOT NULL,
    requester_membership_id UUID NOT NULL,
    department_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    schema_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    submitted_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_req_org_num UNIQUE (organization_id, request_number),
    CONSTRAINT uq_req_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_req_category FOREIGN KEY (organization_id, category_id)
        REFERENCES request_categories(organization_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_req_requester FOREIGN KEY (organization_id, requester_membership_id)
        REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_req_department FOREIGN KEY (organization_id, department_id)
        REFERENCES departments(organization_id, id) ON DELETE RESTRICT
);

-- 4. Request Comments (Public Requester vs Internal Operator Notes)
CREATE TABLE IF NOT EXISTS request_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    author_membership_id UUID NOT NULL,
    content TEXT NOT NULL,
    visibility VARCHAR(30) NOT NULL DEFAULT 'REQUESTER_VISIBLE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT fk_comm_request FOREIGN KEY (organization_id, request_id)
        REFERENCES requests(organization_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_comm_author FOREIGN KEY (organization_id, author_membership_id)
        REFERENCES organization_memberships(organization_id, id) ON DELETE RESTRICT
);

-- 5. Request Attachments (MinIO / S3 Metadata)
CREATE TABLE IF NOT EXISTS request_attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256_checksum VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT fk_att_request FOREIGN KEY (organization_id, request_id)
        REFERENCES requests(organization_id, id) ON DELETE CASCADE
);

-- 6. Indexes for Performance & Scoped Queries
CREATE INDEX IF NOT EXISTS idx_requests_org_requester ON requests(organization_id, requester_membership_id);
CREATE INDEX IF NOT EXISTS idx_requests_org_status ON requests(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_requests_org_dept ON requests(organization_id, department_id);
CREATE INDEX IF NOT EXISTS idx_comments_org_request ON request_comments(organization_id, request_id);
CREATE INDEX IF NOT EXISTS idx_attachments_org_request ON request_attachments(organization_id, request_id);

