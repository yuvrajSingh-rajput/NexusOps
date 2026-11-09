-- ==============================================================================
-- NexusOps Database Migration: V9__ai_schema.sql
-- Module: Nexus AI Assistant, Conversations & Governed Tool Executions
-- ==============================================================================

-- 1. AI Conversations (Chat Sessions Scoped to Organization and User)
CREATE TABLE ai_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT uq_ai_conversations_org_id UNIQUE (organization_id, id)
);

CREATE INDEX idx_ai_conversations_user ON ai_conversations(organization_id, user_id, updated_at DESC);

-- 2. AI Messages (Individual Chat Messages with Grounded Citations & Tool Calls)
CREATE TABLE ai_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    conversation_id UUID NOT NULL,
    role VARCHAR(30) NOT NULL, -- USER, ASSISTANT, SYSTEM, TOOL
    content TEXT NOT NULL,
    citations JSONB,
    tool_call JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT uq_ai_messages_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_ai_messages_conv FOREIGN KEY (organization_id, conversation_id)
        REFERENCES ai_conversations(organization_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_ai_messages_conv ON ai_messages(organization_id, conversation_id, created_at ASC);

-- 3. AI Tool Executions (Two-Phase Governed Action Proposal Registry)
CREATE TABLE ai_tool_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    conversation_id UUID,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tool_name VARCHAR(100) NOT NULL,
    arguments JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PROPOSED', -- PROPOSED, AWAITING_CONFIRMATION, CONFIRMED, EXECUTING, SUCCEEDED, FAILED, REJECTED, EXPIRED
    confirmation_required BOOLEAN NOT NULL DEFAULT TRUE,
    confirmed_by_id UUID REFERENCES users(id) ON DELETE SET NULL,
    confirmed_at TIMESTAMPTZ,
    result_summary JSONB,
    error_code VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_ai_tool_executions_org_id UNIQUE (organization_id, id)
);

CREATE INDEX idx_ai_tool_executions_user ON ai_tool_executions(organization_id, user_id, status);
CREATE INDEX idx_ai_tool_executions_conv ON ai_tool_executions(organization_id, conversation_id);

-- ==============================================================================
-- Row-Level Security (RLS) Defense-in-Depth Policies
-- ==============================================================================
ALTER TABLE ai_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE ai_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE ai_tool_executions ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_ai_conversations ON ai_conversations
    FOR ALL
    USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_ai_messages ON ai_messages
    FOR ALL
    USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_ai_tool_executions ON ai_tool_executions
    FOR ALL
    USING (organization_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

