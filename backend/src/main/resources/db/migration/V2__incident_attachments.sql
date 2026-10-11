-- =============================================================================
-- V2: incident attachments
--
-- File contents live in the private S3 bucket; this table holds only the
-- metadata and the S3 object key. Flyway applies it automatically on the next
-- start under the `postgres` profile.
-- =============================================================================

-- --------------------------------------------------- incident_attachments ---
CREATE TABLE incident_attachments (
    id                UUID         NOT NULL,
    incident_id       UUID         NOT NULL,
    uploaded_by_id    UUID         NOT NULL,
    object_key        VARCHAR(512) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(127) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL,
    CONSTRAINT pk_incident_attachments PRIMARY KEY (id),
    -- One row per stored object: two rows can never share (and so delete) a file.
    CONSTRAINT uk_attachments_object_key UNIQUE (object_key),
    -- Empty files are rejected by the API; the database refuses them too.
    CONSTRAINT ck_attachments_size CHECK (size_bytes > 0),
    CONSTRAINT fk_attachments_incident    FOREIGN KEY (incident_id)    REFERENCES incidents (id),
    CONSTRAINT fk_attachments_uploaded_by FOREIGN KEY (uploaded_by_id) REFERENCES users (id)
);

-- Attachments are always listed for one incident.
CREATE INDEX ix_attachments_incident ON incident_attachments (incident_id);

-- --------------------------------------------------- incident_activities ---
-- The activity trail gains the two attachment actions.
ALTER TABLE incident_activities DROP CONSTRAINT ck_activities_action;
ALTER TABLE incident_activities ADD CONSTRAINT ck_activities_action CHECK (action IN (
    'INCIDENT_CREATED', 'INCIDENT_ASSIGNED', 'INCIDENT_REASSIGNED', 'TEAM_CHANGED',
    'AGENT_CHANGED', 'STATUS_CHANGED', 'PRIORITY_CHANGED', 'INCIDENT_UPDATED',
    'COMMENT_ADDED', 'INCIDENT_RESOLVED', 'INCIDENT_CLOSED', 'INCIDENT_REOPENED',
    'SLA_AT_RISK', 'SLA_BREACHED', 'SLA_COMPLETED',
    'ATTACHMENT_ADDED', 'ATTACHMENT_REMOVED'));
