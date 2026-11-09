-- ==============================================================================
-- NexusOps Migration V7: Task Management Schema
-- Multi-Tenant Tasks, Queue Fulfillment, Comments, and History Audit Logs
-- ==============================================================================

-- 1. Tasks Table (Operational Work Units linked to Requests/Workflows)
CREATE TABLE IF NOT EXISTS tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    task_number VARCHAR(30) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    request_id UUID REFERENCES requests(id) ON DELETE SET NULL,
    workflow_instance_id UUID REFERENCES workflow_instances(id) ON DELETE SET NULL,
    node_instance_id UUID REFERENCES workflow_node_instances(id) ON DELETE SET NULL,
    team_id UUID REFERENCES teams(id) ON DELETE SET NULL,
    assignee_membership_id UUID,
    status VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    due_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT uq_task_org_number UNIQUE (organization_id, task_number),
    CONSTRAINT uq_task_org_id UNIQUE (organization_id, id),
    CONSTRAINT fk_task_assignee FOREIGN KEY (organization_id, assignee_membership_id)
        REFERENCES organization_memberships(organization_id, id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_tasks_org_status ON tasks(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_tasks_org_assignee ON tasks(organization_id, assignee_membership_id);
CREATE INDEX IF NOT EXISTS idx_tasks_org_request ON tasks(organization_id, request_id);
CREATE INDEX IF NOT EXISTS idx_tasks_org_team ON tasks(organization_id, team_id);

-- 2. Task Comments Table
CREATE TABLE IF NOT EXISTS task_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author_membership_id UUID NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT fk_task_comment_author FOREIGN KEY (organization_id, author_membership_id)
        REFERENCES organization_memberships(organization_id, id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_task_comments_task ON task_comments(organization_id, task_id, created_at ASC);

-- 3. Task History Table (Append-Only Audit Trail)
CREATE TABLE IF NOT EXISTS task_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    actor_membership_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    old_state JSONB DEFAULT '{}'::jsonb,
    new_state JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT fk_task_history_actor FOREIGN KEY (organization_id, actor_membership_id)
        REFERENCES organization_memberships(organization_id, id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_task_history_task ON task_history(organization_id, task_id, created_at ASC);

-- 4. Defense-in-Depth: Row Level Security (RLS)
ALTER TABLE tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE task_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE task_history ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'tasks' AND policyname = 'tenant_isolation_tasks'
    ) THEN
        CREATE POLICY tenant_isolation_tasks ON tasks
            FOR ALL USING (organization_id = current_setting('app.current_organization_id', true)::uuid);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'task_comments' AND policyname = 'tenant_isolation_task_comments'
    ) THEN
        CREATE POLICY tenant_isolation_task_comments ON task_comments
            FOR ALL USING (organization_id = current_setting('app.current_organization_id', true)::uuid);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'task_history' AND policyname = 'tenant_isolation_task_history'
    ) THEN
        CREATE POLICY tenant_isolation_task_history ON task_history
            FOR ALL USING (organization_id = current_setting('app.current_organization_id', true)::uuid);
    END IF;
END $$;

