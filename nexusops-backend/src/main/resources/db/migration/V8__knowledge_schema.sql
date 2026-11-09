-- ==============================================================================
-- NexusOps Database Migration: V8__knowledge_schema.sql
-- Module: Knowledge Base & RAG Ingestion Subsystem
-- ==============================================================================

-- Ensure vector extension is enabled
CREATE EXTENSION IF NOT EXISTS vector;

-- Allow implicit cast from text/varchar to vector so Hibernate/JPA can insert string vector representations
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_cast c
        JOIN pg_type s ON c.castsource = s.oid
        JOIN pg_type t ON c.casttarget = t.oid
        WHERE s.typname = 'varchar' AND t.typname = 'vector'
    ) THEN
        CREATE CAST (varchar AS vector) WITH INOUT AS IMPLICIT;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_cast c
        JOIN pg_type s ON c.castsource = s.oid
        JOIN pg_type t ON c.casttarget = t.oid
        WHERE s.typname = 'text' AND t.typname = 'vector'
    ) THEN
        CREATE CAST (text AS vector) WITH INOUT AS IMPLICIT;
    END IF;
END $$;

-- 1. Documents (Catalog of Policy Manuals, SOPs, and Guides)
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL DEFAULT 'GENERAL',
    access_scope VARCHAR(50) NOT NULL DEFAULT 'ALL', -- ALL, DEPARTMENT, ROLE
    target_scope_id UUID, -- Optional department_id or role_id if scoped
    created_by_membership_id UUID REFERENCES organization_memberships(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT uq_documents_org_id UNIQUE (organization_id, id)
);

CREATE INDEX idx_documents_org ON documents(organization_id);
CREATE INDEX idx_documents_category ON documents(organization_id, category);

-- 2. Document Versions (Binary Files Stored in MinIO S3)
CREATE TABLE document_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    document_id UUID NOT NULL,
    version_number INT NOT NULL DEFAULT 1,
    storage_key VARCHAR(500) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    checksum VARCHAR(128) NOT NULL, -- SHA-256
    processing_status VARCHAR(50) NOT NULL DEFAULT 'UPLOADED', -- UPLOADED, PROCESSING, INDEXED, FAILED, RETIRED
    error_message TEXT,
    chunk_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    processed_at TIMESTAMPTZ,

    CONSTRAINT uq_document_versions_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_document_version_number UNIQUE (organization_id, document_id, version_number),
    CONSTRAINT fk_document_versions_doc FOREIGN KEY (organization_id, document_id)
        REFERENCES documents(organization_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_document_versions_doc ON document_versions(organization_id, document_id);
CREATE INDEX idx_document_versions_status ON document_versions(organization_id, processing_status);

-- 3. Document Chunks (Vector Store for Semantic Similarity Search)
CREATE TABLE document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    page_number INT,
    token_count INT NOT NULL DEFAULT 0,
    embedding VECTOR(768) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT uq_document_chunks_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_document_chunks_index UNIQUE (organization_id, document_version_id, chunk_index),
    CONSTRAINT fk_document_chunks_version FOREIGN KEY (organization_id, document_version_id)
        REFERENCES document_versions(organization_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_document_chunks_version ON document_chunks(organization_id, document_version_id);
CREATE INDEX idx_document_chunks_org ON document_chunks(organization_id);

-- HNSW Vector Index for Fast Cosine Similarity
CREATE INDEX idx_document_chunks_embedding ON document_chunks 
    USING hnsw (embedding vector_cosine_ops);

-- ==============================================================================
-- Row-Level Security (RLS) Defense-in-Depth Policies
-- ==============================================================================
ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_chunks ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_documents ON documents
    FOR ALL
    USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_document_versions ON document_versions
    FOR ALL
    USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_document_chunks ON document_chunks
    FOR ALL
    USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
