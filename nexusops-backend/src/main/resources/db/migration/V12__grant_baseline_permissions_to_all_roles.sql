-- ==============================================================================
-- NexusOps Migration V12: Grant Baseline Permissions to All System Roles
-- Guarantees that every persona (Employee, Manager, Operator, Finance, Admin)
-- has essential self-service request capabilities (REQUEST_CREATE, REQUEST_VIEW_OWN,
-- REQUEST_CANCEL, etc.) and complete operational authorities without 403 Forbidden.
-- ==============================================================================

-- 1. Baseline permissions for all system roles (EMPLOYEE, MANAGER, OPERATOR, FINANCE, ORG_ADMIN)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('EMPLOYEE', 'MANAGER', 'OPERATOR', 'FINANCE', 'ORG_ADMIN')
  AND p.code IN (
      'REQUEST_CREATE',
      'REQUEST_VIEW_OWN',
      'REQUEST_COMMENT',
      'REQUEST_ATTACH',
      'REQUEST_CANCEL',
      'KNOWLEDGE_VIEW',
      'AI_CHAT_ACCESS'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 2. Operator fulfillment & visibility permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'OPERATOR'
  AND p.code IN (
      'TASK_VIEW_ASSIGNED',
      'TASK_QUEUE_VIEW',
      'TASK_CLAIM',
      'TASK_UPDATE',
      'TASK_COMPLETE',
      'REQUEST_VIEW_ALL',
      'REQUEST_VIEW_INTERNAL_COMMENTS',
      'ANALYTICS_VIEW'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 3. Finance audit & approval permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'FINANCE'
  AND p.code IN (
      'REQUEST_VIEW_ALL',
      'REQUEST_VIEW_INTERNAL_COMMENTS',
      'APPROVAL_VIEW_PENDING',
      'APPROVAL_EXECUTE',
      'APPROVAL_DELEGATE',
      'ANALYTICS_VIEW'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 4. Manager leadership permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'MANAGER'
  AND p.code IN (
      'REQUEST_VIEW_DEPT',
      'APPROVAL_VIEW_PENDING',
      'APPROVAL_EXECUTE',
      'APPROVAL_DELEGATE',
      'ANALYTICS_VIEW',
      'TASK_VIEW_ASSIGNED',
      'TASK_QUEUE_VIEW'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 5. Organization Administrator full system permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'ORG_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
