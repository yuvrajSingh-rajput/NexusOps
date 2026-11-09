-- ==============================================================================
-- NexusOps Migration V3: Seed Global System Permissions
-- ==============================================================================

INSERT INTO permissions (id, code, category, description) VALUES
    -- Request Permissions
    (gen_random_uuid(), 'REQUEST_CREATE', 'REQUEST', 'Create new operational requests'),
    (gen_random_uuid(), 'REQUEST_VIEW_OWN', 'REQUEST', 'View requests created by oneself'),
    (gen_random_uuid(), 'REQUEST_VIEW_DEPT', 'REQUEST', 'View requests within own department'),
    (gen_random_uuid(), 'REQUEST_VIEW_ALL', 'REQUEST', 'View all requests within organization'),
    (gen_random_uuid(), 'REQUEST_COMMENT', 'REQUEST', 'Post comments on requests'),
    (gen_random_uuid(), 'REQUEST_VIEW_INTERNAL_COMMENTS', 'REQUEST', 'View internal operator-only comments'),
    (gen_random_uuid(), 'REQUEST_ATTACH', 'REQUEST', 'Upload and attach documents to requests'),
    (gen_random_uuid(), 'REQUEST_CANCEL', 'REQUEST', 'Cancel in-flight operational requests'),

    -- Approval Permissions
    (gen_random_uuid(), 'APPROVAL_VIEW_PENDING', 'APPROVAL', 'View pending approvals assigned to self or role'),
    (gen_random_uuid(), 'APPROVAL_EXECUTE', 'APPROVAL', 'Approve, reject, or request information on approvals'),
    (gen_random_uuid(), 'APPROVAL_DELEGATE', 'APPROVAL', 'Delegate approval authority to another member'),

    -- Task Permissions
    (gen_random_uuid(), 'TASK_VIEW_ASSIGNED', 'TASK', 'View tasks assigned to oneself'),
    (gen_random_uuid(), 'TASK_QUEUE_VIEW', 'TASK', 'View unassigned department task queues'),
    (gen_random_uuid(), 'TASK_CLAIM', 'TASK', 'Claim an unassigned fulfillment task'),
    (gen_random_uuid(), 'TASK_UPDATE', 'TASK', 'Update task progress and operational details'),
    (gen_random_uuid(), 'TASK_COMPLETE', 'TASK', 'Mark fulfillment tasks as completed'),

    -- Workflow Permissions
    (gen_random_uuid(), 'WORKFLOW_VIEW', 'WORKFLOW', 'View workflow definitions and versions'),
    (gen_random_uuid(), 'WORKFLOW_MANAGE', 'WORKFLOW', 'Create, edit, validate, and publish workflow graphs'),

    -- Knowledge & AI Permissions
    (gen_random_uuid(), 'KNOWLEDGE_VIEW', 'KNOWLEDGE', 'Search and read knowledge base documents'),
    (gen_random_uuid(), 'KNOWLEDGE_MANAGE', 'KNOWLEDGE', 'Upload, delete, and re-index policy documents'),
    (gen_random_uuid(), 'AI_CHAT_ACCESS', 'AI', 'Access Nexus AI chat assistant'),

    -- Analytics & Audit Permissions
    (gen_random_uuid(), 'ANALYTICS_VIEW', 'ANALYTICS', 'Access KPI and operational bottleneck analytics'),
    (gen_random_uuid(), 'AUDIT_VIEW', 'AUDIT', 'View system-wide append-only audit trail'),

    -- Organization Administration Permissions
    (gen_random_uuid(), 'ORG_ADMIN_MANAGE', 'ORGANIZATION', 'Full organization administration and settings'),
    (gen_random_uuid(), 'ORG_DEPT_MANAGE', 'ORGANIZATION', 'Manage organization departments and teams'),
    (gen_random_uuid(), 'ORG_USER_INVITE', 'ORGANIZATION', 'Invite new employees and manage memberships')
ON CONFLICT (code) DO NOTHING;
