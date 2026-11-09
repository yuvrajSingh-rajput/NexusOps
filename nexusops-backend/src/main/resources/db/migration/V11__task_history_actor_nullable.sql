-- V11__task_history_actor_nullable.sql
-- Allow system/automated actions in task_history where no human actor exists yet (e.g. initial task node creation by workflow engine)

ALTER TABLE task_history ALTER COLUMN actor_membership_id DROP NOT NULL;

