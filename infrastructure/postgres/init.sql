-- ==============================================================================
-- NexusOps PostgreSQL Initialization Script
-- Executed on container creation
-- ==============================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";

-- Log extension versions
DO $$
BEGIN
    RAISE NOTICE 'pgcrypto version: %', (SELECT extversion FROM pg_extension WHERE extname = 'pgcrypto');
    RAISE NOTICE 'vector version: %', (SELECT extversion FROM pg_extension WHERE extname = 'vector');
END $$;
