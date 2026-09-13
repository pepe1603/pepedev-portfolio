-- ============================================================
-- V1__init.sql — Modelo de datos mínimo (fuente de verdad: docs/MODELO-DATOS.md)
--
-- Reglas aplicadas:
--   * Sin FKs entre tablas de contenido (una página pública = una lectura).
--   * Bilingüe ES/EN en columnas JSONB {es,en} (nullable → permite fallback).
--   * Enums como VARCHAR(20) + CHECK.
--   * TIMESTAMPTZ DEFAULT now(); updated_at lo gestiona JPA (sin triggers).
--   * Sin seed de users: el admin lo crea el bootstrap (Bloque 3) desde .env.
--   * PERFIL SEEDEA como placeholder; el contenido real entra vía CRM (Bloque 6.3).
--
-- Nota: el CHECK de users.role solo admite 'ADMIN' hoy. Si mañana nacen más roles,
-- se adapta en una V2 (esta migración queda inmutable).
-- ============================================================

-- ---------- users (auth del CRM, sin relación con contenido) ----------
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN'))
);

-- ---------- profile (singleton: id fijo = 1) ----------
CREATE TABLE profile (
    id           SMALLINT     PRIMARY KEY,
    full_name    VARCHAR(120) NOT NULL,
    headline     JSONB,
    bio          JSONB,
    location     VARCHAR(120),
    github_url   VARCHAR(255),
    linkedin_url VARCHAR(255),
    email_public VARCHAR(320),
    website_url  VARCHAR(255),
    cv_url_es    VARCHAR(255),
    cv_url_en    VARCHAR(255),
    avatar_url   VARCHAR(255),
    skills       JSONB        NOT NULL DEFAULT '[]'::jsonb,
    experiences  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    views_count  INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_profile_id CHECK (id = 1)
);

-- ---------- projects (página propia + SEO por slug) ----------
CREATE TABLE projects (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug           VARCHAR(120) NOT NULL,
    title          JSONB        NOT NULL,
    subtitle       JSONB,
    summary        JSONB,
    description_md JSONB,
    thumbnail_url  VARCHAR(255),
    gallery        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    repo_url       VARCHAR(255),
    demo_url       VARCHAR(255),
    stack          JSONB        NOT NULL DEFAULT '[]'::jsonb,
    period_start   DATE,
    period_end     DATE,
    is_featured    BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order     INTEGER      NOT NULL DEFAULT 0,
    status         VARCHAR(20)  NOT NULL DEFAULT 'draft',
    published_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_projects_slug UNIQUE (slug),
    CONSTRAINT ck_projects_status CHECK (status IN ('draft', 'published'))
);

-- ---------- certificates (listado filtrable por kind/issuer) ----------
CREATE TABLE certificates (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title          JSONB        NOT NULL,
    issuer         VARCHAR(120) NOT NULL,
    kind           VARCHAR(20)  NOT NULL,
    issue_date     DATE,
    expiry_date    DATE,
    credential_url VARCHAR(255),
    image_url      VARCHAR(255),
    is_featured    BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order     INTEGER      NOT NULL DEFAULT 0,
    status         VARCHAR(20)  NOT NULL DEFAULT 'draft',
    published_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_certificates_kind   CHECK (kind IN ('certificate', 'course')),
    CONSTRAINT ck_certificates_status CHECK (status IN ('draft', 'published'))
);

-- ---------- messages (bandeja de contacto) ----------
CREATE TABLE messages (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    email      VARCHAR(320) NOT NULL,
    subject    VARCHAR(160) NOT NULL,
    body       TEXT         NOT NULL,
    ip         VARCHAR(45)  NOT NULL, -- anonimizada /24 en el service (Bloque 5.3)
    user_agent VARCHAR(255),
    status     VARCHAR(20)  NOT NULL DEFAULT 'new',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_messages_status CHECK (status IN ('new', 'read', 'archived'))
);

-- ---------- Índices (mínimos; GIN sobre stack[] se difiere a V2) ----------
CREATE INDEX idx_projects_status ON projects (status) WHERE status = 'published';
CREATE INDEX idx_certificates_kind ON certificates (kind);
CREATE INDEX idx_certificates_status ON certificates (status);
CREATE INDEX idx_messages_status ON messages (status);

-- ---------- Seed: profile placeholder (el contenido se completa vía CRM) ----------
INSERT INTO profile (id, full_name)
VALUES (1, '');