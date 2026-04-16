-- =============================================================================
--  Quatrion Portal — moduł System
--  Tabela: portal_dashboard_widget
--
--  Nazwy kolumn generowane przez CamelCaseToUnderscoresNamingStrategy:
--    entityName              → entity_name
--    ownerUsername           → owner_username
--    chartType               → chart_type
--    aggregationFunction     → aggregation_function
--    aggregationField        → aggregation_field
--    formulaExpression       → formula_expression
--    groupByField            → group_by_field
--    dateRangeField          → date_range_field
--    dateRangePreset         → date_range_preset
--    daysBackFrom            → days_back_from
--    daysBackTo              → days_back_to
--    dateFrom                → date_from
--    dateTo                  → date_to
--    colSpan                 → col_span
--    refreshIntervalSeconds  → refresh_interval_seconds
--    valueFormat             → value_format
-- =============================================================================

-- ─── Sekwencja dla portal_dashboard_widget ───────────────────────────────────
-- GenerationType.SEQUENCE z allocationSize=1 — sekwencja rośnie o 1

CREATE SEQUENCE portal_dashboard_widget_seq
    INCREMENT BY 1
    START WITH 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

-- ─── Widget dashboardu (DashboardWidget) ─────────────────────────────────────
-- flat entity, moduł System
-- Covers: TEXT, SELECT, TEXTAREA, NUMBER, DATE

CREATE TABLE portal_dashboard_widget (
    id                       BIGINT       NOT NULL DEFAULT nextval('portal_dashboard_widget_seq') PRIMARY KEY,

    -- ── Identyfikacja ─────────────────────────────────────────────────────
    title                    VARCHAR(100) NOT NULL,
    entity_name              VARCHAR(100) NOT NULL,
    scope                    VARCHAR(20)  NOT NULL DEFAULT 'GLOBAL',
    owner_username           VARCHAR(150),

    -- ── Typ wykresu ───────────────────────────────────────────────────────
    chart_type               VARCHAR(20)  NOT NULL DEFAULT 'BAR',

    -- ── Agregacja ─────────────────────────────────────────────────────────
    aggregation_function     VARCHAR(20)  NOT NULL DEFAULT 'COUNT',
    aggregation_field        VARCHAR(200),
    formula_expression       VARCHAR(500),

    -- ── Grupowanie ────────────────────────────────────────────────────────
    group_by_field           VARCHAR(100),

    -- ── Zakres dat ────────────────────────────────────────────────────────
    date_range_field         VARCHAR(100),
    date_range_preset        VARCHAR(20)  DEFAULT 'NONE',
    days_back_from           INTEGER      NOT NULL DEFAULT 30,
    days_back_to             INTEGER      NOT NULL DEFAULT 0,
    date_from                DATE,
    date_to                  DATE,

    -- ── Układ i odświeżanie ───────────────────────────────────────────────
    position                 INTEGER      NOT NULL DEFAULT 0,
    col_span                 VARCHAR(5)   DEFAULT '1',
    refresh_interval_seconds INTEGER      NOT NULL DEFAULT 0,

    -- ── Format wartości ───────────────────────────────────────────────────
    value_format             VARCHAR(20)  NOT NULL DEFAULT 'AUTO'
);

-- Sekwencja należy do tabeli — zostanie usunięta razem z nią (DROP TABLE)
ALTER SEQUENCE portal_dashboard_widget_seq
    OWNED BY portal_dashboard_widget.id;

