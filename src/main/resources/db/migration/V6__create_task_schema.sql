-- =============================================================================
--  Quatrion Portal — moduł Zadania (task framework)
--  Tabele: task_run, task_run_file, loan_history_csv_task
--
--  Nazwy kolumn generowane przez CamelCaseToUnderscoresNamingStrategy:
--    taskRefId       → task_ref_id
--    taskRefType     → task_ref_type
--    startedBy       → started_by
--    startedAt       → started_at
--    finishedAt      → finished_at
--    errorMessage    → error_message
--    cancelRequested → cancel_requested
--    taskRunId       → task_run_id
--    fileName        → file_name
--    contentType     → content_type
--    s3Key           → s3_key
--    fileSizeBytes   → file_size_bytes
--    cronExpression  → cron_expression
--    createdBy       → created_by
--    createdAt       → created_at
--    lastRunBy       → last_run_by
--    lastRunAt       → last_run_at
--    includeOverdue  → include_overdue
--    memberId        → member_id
-- =============================================================================

-- ─── Uruchomienie zadania (TaskRun) ──────────────────────────────────────────

CREATE TABLE task_run (
    id               BIGSERIAL     PRIMARY KEY,
    task_ref_id      BIGINT        NOT NULL,
    task_ref_type    VARCHAR(100)  NOT NULL,
    status           VARCHAR(30)   NOT NULL DEFAULT 'URUCHOMIONO',
    started_by       VARCHAR(100)  NOT NULL DEFAULT 'system',
    started_at       VARCHAR(30)   NOT NULL DEFAULT '',
    finished_at      VARCHAR(30)   NOT NULL DEFAULT '',
    error_message    TEXT          NOT NULL DEFAULT '',
    cancel_requested BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_task_run_ref        ON task_run (task_ref_type, task_ref_id);
CREATE INDEX idx_task_run_status     ON task_run (status);
CREATE INDEX idx_task_run_started_at ON task_run (started_at DESC);

-- ─── Plik wynikowy zadania (TaskRunFile) ──────────────────────────────────────

CREATE TABLE task_run_file (
    id              BIGSERIAL     PRIMARY KEY,
    task_run_id     BIGINT        NOT NULL REFERENCES task_run(id) ON DELETE CASCADE,
    file_name       VARCHAR(255)  NOT NULL,
    content_type    VARCHAR(100)  NOT NULL DEFAULT '',
    s3_key          VARCHAR(500)  NOT NULL DEFAULT '',
    file_size_bytes BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_task_run_file_run ON task_run_file (task_run_id);

-- ─── Zadanie: Historia wypożyczeń CSV (LoanHistoryCsvTask) ───────────────────
-- Dziedziczy po AbstractTask (@MappedSuperclass) — wszystkie pola bazowe
-- są kopiowane do tej tabeli (strategia TABLE_PER_CLASS).

CREATE TABLE loan_history_csv_task (
    id              BIGSERIAL     PRIMARY KEY,

    -- ── Pola z AbstractTask ────────────────────────────────────────────────
    name            VARCHAR(200)  NOT NULL DEFAULT '',
    description     TEXT          NOT NULL DEFAULT '',
    cron_expression VARCHAR(100)  NOT NULL DEFAULT '',
    status          VARCHAR(30)   NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(100)  NOT NULL DEFAULT '',
    created_at      VARCHAR(30)   NOT NULL DEFAULT '',
    last_run_by     VARCHAR(100)  NOT NULL DEFAULT '',
    last_run_at     VARCHAR(30)   NOT NULL DEFAULT '',

    -- ── Parametry zadania ─────────────────────────────────────────────────
    member_id       BIGINT,
    include_overdue BOOLEAN       NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_loan_csv_task_status ON loan_history_csv_task (status);

