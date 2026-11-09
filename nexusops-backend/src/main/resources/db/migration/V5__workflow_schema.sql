-- ==============================================================================
-- Migration V5: Workflow Engine Schema (Definitions, Graphs, and Execution State)
-- Enforces tenant isolation, graph immutability on publish, and durable execution
-- ==============================================================================

-- 1. Workflow Definitions (Tenant-owned master catalog)
CREATE TABLE IF NOT EXISTS workflow_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    category_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_wf_def_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_wf_def_org_code UNIQUE (organization_id, code),
    CONSTRAINT fk_wf_def_category FOREIGN KEY (organization_id, category_id)
        REFERENCES request_categories(organization_id, id) ON DELETE RESTRICT
);

-- 2. Workflow Versions (Immutable once published)
CREATE TABLE IF NOT EXISTS workflow_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_definition_id UUID NOT NULL REFERENCES workflow_definitions(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    definition_snapshot JSONB,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_wf_ver_num UNIQUE (workflow_definition_id, version_number)
);

-- 3. Workflow Graph Nodes
CREATE TABLE IF NOT EXISTS workflow_nodes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_version_id UUID NOT NULL REFERENCES workflow_versions(id) ON DELETE CASCADE,
    node_key VARCHAR(50) NOT NULL,
    node_type VARCHAR(30) NOT NULL,
    title VARCHAR(100) NOT NULL,
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    position_x DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    position_y DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_wf_node_ver_key UNIQUE (workflow_version_id, node_key)
);

-- 4. Workflow Graph Transitions (Directed Edges with Typed Predicates)
CREATE TABLE IF NOT EXISTS workflow_transitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_version_id UUID NOT NULL REFERENCES workflow_versions(id) ON DELETE CASCADE,
    source_node_id UUID NOT NULL REFERENCES workflow_nodes(id) ON DELETE CASCADE,
    target_node_id UUID NOT NULL REFERENCES workflow_nodes(id) ON DELETE CASCADE,
    condition_expression JSONB,
    priority INT NOT NULL DEFAULT 0,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- 5. Workflow Runtime Instances (Durable Execution State)
CREATE TABLE IF NOT EXISTS workflow_instances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    workflow_version_id UUID NOT NULL REFERENCES workflow_versions(id) ON DELETE RESTRICT,
    request_id UUID NOT NULL,
    attempt_number INT NOT NULL DEFAULT 1,
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    current_node_key VARCHAR(50),
    context_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_wf_inst_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_wf_inst_req UNIQUE (organization_id, request_id),
    CONSTRAINT fk_wf_inst_request FOREIGN KEY (organization_id, request_id)
        REFERENCES requests(organization_id, id) ON DELETE CASCADE
);

-- 6. Workflow Node Execution Instances (Execution Audit Trail)
CREATE TABLE IF NOT EXISTS workflow_node_instances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_instance_id UUID NOT NULL REFERENCES workflow_instances(id) ON DELETE CASCADE,
    node_id UUID NOT NULL REFERENCES workflow_nodes(id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    result JSONB,
    entered_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    exited_at TIMESTAMPTZ
);

-- Indexes for performance & tenant lookup
CREATE INDEX IF NOT EXISTS idx_wf_def_org ON workflow_definitions(organization_id);
CREATE INDEX IF NOT EXISTS idx_wf_ver_def ON workflow_versions(workflow_definition_id);
CREATE INDEX IF NOT EXISTS idx_wf_node_ver ON workflow_nodes(workflow_version_id);
CREATE INDEX IF NOT EXISTS idx_wf_tran_ver ON workflow_transitions(workflow_version_id);
CREATE INDEX IF NOT EXISTS idx_wf_inst_org_req ON workflow_instances(organization_id, request_id);
CREATE INDEX IF NOT EXISTS idx_wf_inst_status ON workflow_instances(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_wf_node_inst_wf ON workflow_node_instances(workflow_instance_id);

