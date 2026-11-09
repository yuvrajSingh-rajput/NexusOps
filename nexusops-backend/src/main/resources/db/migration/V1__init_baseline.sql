-- ==============================================================================
-- NexusOps Flyway Baseline (V1__init_baseline.sql)
-- Ensures database extensions and schema baseline
-- ==============================================================================

-- Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";

-- Health check table for database connectivity verification
CREATE TABLE IF NOT EXISTS platform_system_health (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status VARCHAR(50) NOT NULL,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

INSERT INTO platform_system_health (status) VALUES ('INITIALIZED');

