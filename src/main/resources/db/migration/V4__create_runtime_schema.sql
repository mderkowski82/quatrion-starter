-- =============================================================================
--  Quatrion Portal — tabele runtime (framework)
--  Tabele: portal_audit_log, iam_permission_mapping, iam_audit_log
--
--  Tabele te istniały wcześniej tylko w kodzie Hibernate (strategy=update).
--  Dodane do Flyway aby produkcja (strategy=validate) mogła je walidować.
--
--  Nazwy kolumn generowane przez CamelCaseToUnderscoresNamingStrategy:
--    entityName    → entity_name
--    entityId      → entity_id
--    actorSub      → actor_sub
--    changesJson   → changes_json
--    createdAt     → created_at
--    actorName     → actor_name
--    resourceType  → resource_type
--    resourceId    → resource_id
--    beforeState   → before_state
--    afterState    → after_state
--    permissionSet → permission_set
--    keycloakRole  → keycloak_role
--    createdBy     → created_by
-- =============================================================================

-- ─── Audit log portalu (PortalAuditLog) ──────────────────────────────────────
-- Zapisywany przez PortalAuditLogService dla encji z auditLog = true

CREATE TABLE portal_audit_log (
    id           BIGSERIAL    PRIMARY KEY,
    entity_name  VARCHAR(255) NOT NULL,
    entity_id    BIGINT       NOT NULL,
    action       VARCHAR(50)  NOT NULL,
    actor_sub    VARCHAR(255) NOT NULL DEFAULT 'anonymous',
    changes_json TEXT         NOT NULL DEFAULT '[]',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_portal_audit_entity  ON portal_audit_log (entity_name, entity_id);
CREATE INDEX idx_portal_audit_created ON portal_audit_log (created_at DESC);

-- ─── Mapowanie uprawnień IAM (PermissionMapping) ─────────────────────────────
-- Przechowuje powiązanie zestaw-uprawnień ↔ rola Keycloak

CREATE TABLE iam_permission_mapping (
    id             BIGSERIAL    PRIMARY KEY,
    permission_set VARCHAR(100) NOT NULL,
    keycloak_role  VARCHAR(100) NOT NULL,
    created_by     VARCHAR(255),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (permission_set, keycloak_role)
);

-- ─── Audit log IAM (IamAuditLog) ─────────────────────────────────────────────
-- Zapisywany przez IamAuditService dla operacji IAM (CREATE/UPDATE/DELETE/ASSIGN)

CREATE TABLE iam_audit_log (
    id            BIGSERIAL    PRIMARY KEY,
    actor_sub     VARCHAR(255) NOT NULL,
    actor_name    VARCHAR(255),
    action        VARCHAR(50)  NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id   VARCHAR(255),
    before_state  TEXT,
    after_state   TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_iam_audit_actor   ON iam_audit_log (actor_sub);
CREATE INDEX idx_iam_audit_created ON iam_audit_log (created_at DESC);

