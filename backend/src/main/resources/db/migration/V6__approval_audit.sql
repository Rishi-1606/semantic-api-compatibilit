-- =========================================================
-- V6__approval_audit.sql
-- Phase 9 -- Safety & Approval audit columns
-- =========================================================
--
-- Adds three audit columns to the transformation_rules table
-- to track approval/rejection timestamps and human notes.
--
-- V5 is NOT modified -- only additive changes here.
--
-- approved_at   -- timestamp set when ruleStatus transitions to APPROVED
-- rejected_at   -- timestamp set when ruleStatus transitions to REJECTED
-- approval_note -- human-supplied note for approval or rejection
--                  (complements existing rejection_reason; this field
--                   carries the human context for both directions)
--
-- Note: each ADD COLUMN is a separate statement for H2 / PostgreSQL compatibility.

ALTER TABLE transformation_rules ADD COLUMN IF NOT EXISTS approved_at   TIMESTAMP;
ALTER TABLE transformation_rules ADD COLUMN IF NOT EXISTS rejected_at   TIMESTAMP;
ALTER TABLE transformation_rules ADD COLUMN IF NOT EXISTS approval_note TEXT;

-- Index for Phase 9 audit queries on approval timestamp
CREATE INDEX IF NOT EXISTS idx_tr_approved_at ON transformation_rules(approved_at);
