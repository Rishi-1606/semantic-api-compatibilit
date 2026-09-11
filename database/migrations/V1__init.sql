-- =========================================================
-- V1__init.sql
-- Initial schema for Semantic API Compatibility Framework
-- Phase 1: Foundation tables
-- =========================================================

-- Table: apis
-- Represents a named API (e.g., "Customer API")
CREATE TABLE IF NOT EXISTS apis (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table: api_versions
-- Represents a specific version of an API (e.g., V1, V2)
CREATE TABLE IF NOT EXISTS api_versions (
    id          BIGSERIAL PRIMARY KEY,
    api_id      BIGINT NOT NULL REFERENCES apis(id) ON DELETE CASCADE,
    version     VARCHAR(50) NOT NULL,
    base_url    VARCHAR(500),
    description TEXT,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (api_id, version)
);

-- Table: endpoints
-- Stores individual HTTP endpoints for an API version
CREATE TABLE IF NOT EXISTS endpoints (
    id              BIGSERIAL PRIMARY KEY,
    api_version_id  BIGINT NOT NULL REFERENCES api_versions(id) ON DELETE CASCADE,
    http_method     VARCHAR(10) NOT NULL,       -- GET, POST, PUT, DELETE, PATCH
    path            VARCHAR(500) NOT NULL,
    description     TEXT,
    request_schema  TEXT,                        -- JSON schema as text
    response_schema TEXT,                        -- JSON schema as text
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table: schema_fields
-- Extracted fields from parsed JSON schemas
CREATE TABLE IF NOT EXISTS schema_fields (
    id              BIGSERIAL PRIMARY KEY,
    endpoint_id     BIGINT NOT NULL REFERENCES endpoints(id) ON DELETE CASCADE,
    schema_type     VARCHAR(20) NOT NULL,        -- REQUEST or RESPONSE
    field_name      VARCHAR(255) NOT NULL,
    field_path      VARCHAR(500) NOT NULL,       -- dot-notation path e.g., contact.phone
    field_type      VARCHAR(50),                 -- string, integer, boolean, object, array
    is_required     BOOLEAN NOT NULL DEFAULT FALSE,
    parent_path     VARCHAR(500),
    description     TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- Indexes
-- =========================================================
CREATE INDEX IF NOT EXISTS idx_api_versions_api_id ON api_versions(api_id);
CREATE INDEX IF NOT EXISTS idx_endpoints_api_version_id ON endpoints(api_version_id);
CREATE INDEX IF NOT EXISTS idx_schema_fields_endpoint_id ON schema_fields(endpoint_id);

-- =========================================================
-- Phase 1 seed: no data yet — populated via API in Phase 3
-- =========================================================
