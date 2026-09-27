CREATE TABLE audit_log (
    id            uuid PRIMARY KEY,
    actor_id      uuid,
    actor_email   varchar(255),
    action        varchar(32)  NOT NULL,
    resource_type varchar(32)  NOT NULL,
    resource_id   varchar(64),
    detail        varchar(1000),
    created_at    timestamptz  NOT NULL
);

CREATE INDEX idx_audit_resource_created ON audit_log (resource_type, created_at DESC, id DESC);