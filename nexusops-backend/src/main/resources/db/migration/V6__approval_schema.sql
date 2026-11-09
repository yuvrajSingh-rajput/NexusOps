-- ==============================================================================
-- Migration V6: Approval Engine Schema (Instances, Actions & Scheduled Jobs)
-- Enforces tenant isolation, append-only decision audit, and optimistic locking
-- ==============================================================================

-- 1. Approval Instances (Pending and resolved decision state)
CREATE TABLE IF NOT EXISTS approval_instances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    node_instance_id UUID NOT NULL REFERENCES workflow_node_instances(id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    assigned_membership_id UUID REFERENCES organization_memberships(id) ON DELETE SET NULL,
    assigned_role_id UUID REFERENCES roles(id) ON DELETE SET NULL,
    approver_type VARCHAR(50) NOT NULL DEFAULT 'DIRECT_MEMBERSHIP',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    due_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_appr_inst_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_appr_inst_request FOREIGN KEY (organization_id, request_id)
        REFERENCES requests(organization_id, id) ON DELETE CASCADE
);

-- 2. Approval Actions (Append-only immutable decision history)
CREATE TABLE IF NOT EXISTS approval_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    approval_instance_id UUID NOT NULL REFERENCES approval_instances(id) ON DELETE CASCADE,
    actor_membership_id UUID NOT NULL REFERENCES organization_memberships(id) ON DELETE RESTRICT,
    action VARCHAR(30) NOT NULL,
    comments TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- 3. Scheduled Jobs (Durable timer and escalation polling queue)
CREATE TABLE IF NOT EXISTS scheduled_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    job_type VARCHAR(50) NOT NULL,
    target_id UUID NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    executed_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- Indexes for performance, queue polling, and tenant lookup
CREATE INDEX IF NOT EXISTS idx_appr_inst_org_status ON approval_instances(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_appr_inst_assigned_mem ON approval_instances(organization_id, assigned_membership_id);
CREATE INDEX IF NOT EXISTS idx_appr_inst_assigned_role ON approval_instances(organization_id, assigned_role_id);
CREATE INDEX IF NOT EXISTS idx_appr_inst_req ON approval_instances(organization_id, request_id);
CREATE INDEX IF NOT EXISTS idx_appr_act_inst ON approval_actions(approval_instance_id);
CREATE INDEX IF NOT EXISTS idx_sched_jobs_poll ON scheduled_jobs(status, scheduled_at);

