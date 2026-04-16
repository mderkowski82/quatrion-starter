-- =============================================================================
--  Quatrion Portal Demo — moduł CRM + Katalog
--  Tabele: demo_country, demo_category, demo_supplier, demo_customer,
--          demo_order, demo_product, demo_order_item
--
--  Nazwy kolumn generowane przez CamelCaseToUnderscoresNamingStrategy:
--    isActive       → is_active
--    customerType   → customer_type
--    favoriteColor  → favorite_color
--    birthDate      → birth_date
--    registeredAt   → registered_at
--    countryId      → country_id
--    creditLimit    → credit_limit
--    loyaltyPoints  → loyalty_points
--    categoryId     → category_id
--    extraData      → extra_data
--    customField    → custom_field
--    orderNumber    → order_number
--    orderDate      → order_date
--    deliveryDate   → delivery_date
--    isPriority     → is_priority
--    totalAmount    → total_amount
--    customerId     → customer_id
--    orderId        → order_id
--    productId      → product_id
--    unitPrice      → unit_price
--    lineTotal      → line_total
--    isVerified     → is_verified
--    imageUrl       → image_url
--    productUrl     → product_url
--    supplierId     → supplier_id
--    isEu           → is_eu   (WAŻNE: pole musi się nazywać isEu, nie isEU!
--                              isEU → iseu wg algorytmu, bo U jest wielkie;
--                              isEu → is_eu bo po E następuje małe u)
-- =============================================================================

-- ─── Kraj (DemoCountry) ──────────────────────────────────────────────────────
-- Covers: TEXT, SELECT, BOOLEAN
-- Filters: CONTAINS, EXACT, IN, BOOLEAN

CREATE TABLE demo_country (
    id        BIGSERIAL    PRIMARY KEY,
    name      VARCHAR(255) NOT NULL,
    code      VARCHAR(3)   NOT NULL,
    continent VARCHAR(20),
    is_eu     BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_demo_country_code      ON demo_country (code);
CREATE INDEX idx_demo_country_continent ON demo_country (continent);
CREATE INDEX idx_demo_country_is_eu     ON demo_country (is_eu);

-- ─── Kategoria (DemoCategory) ────────────────────────────────────────────────
-- Covers: TEXT, TEXTAREA, COLOR, BOOLEAN, RELATION (self-ref)
-- Filters: CONTAINS, NONE, BOOLEAN, EXACT

CREATE TABLE demo_category (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    color       VARCHAR(7),
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    parent_id   BIGINT       REFERENCES demo_category (id)
);

CREATE INDEX idx_demo_category_is_active ON demo_category (is_active);
CREATE INDEX idx_demo_category_parent_id ON demo_category (parent_id);

-- ─── Dostawca (DemoSupplier) ─────────────────────────────────────────────────
-- Covers: TEXT, EMAIL, URL, NUMBER, BOOLEAN, TEXTAREA
-- Filters: CONTAINS, EXACT, RANGE, BOOLEAN, NONE

CREATE TABLE demo_supplier (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    email       VARCHAR(255),
    website     VARCHAR(255),
    rating      INTEGER      NOT NULL DEFAULT 0,
    is_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    notes       TEXT,
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_demo_supplier_is_verified ON demo_supplier (is_verified);
CREATE INDEX idx_demo_supplier_rating      ON demo_supplier (rating);

-- ─── Klient (DemoCustomer) ───────────────────────────────────────────────────
-- 4 tabs, wszystkie typy rendererów
-- Covers: TEXT, TEXTAREA, NUMBER, DECIMAL, DATE, DATETIME, BOOLEAN, SELECT,
--         MULTI_SELECT, EMAIL, URL, PASSWORD, COLOR, FILE, JSON, CUSTOM, RELATION (×2)
-- Filters: CONTAINS, EXACT, RANGE, IN, BOOLEAN, NONE, STARTS_WITH

CREATE TABLE demo_customer (
    id             BIGSERIAL        PRIMARY KEY,
    name           VARCHAR(255)     NOT NULL,
    customer_type  VARCHAR(20),
    is_active      BOOLEAN          NOT NULL DEFAULT TRUE,
    favorite_color VARCHAR(7),
    email          VARCHAR(255)     UNIQUE,
    phone          VARCHAR(20),
    website        VARCHAR(255),
    birth_date     VARCHAR(255),
    registered_at  VARCHAR(255),
    country_id     BIGINT           REFERENCES demo_country (id),
    credit_limit   DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    loyalty_points INTEGER          NOT NULL DEFAULT 0,
    tags           VARCHAR(255),
    category_id    BIGINT           REFERENCES demo_category (id),
    password       VARCHAR(255),
    avatar         VARCHAR(255),
    extra_data     TEXT,
    notes          TEXT,
    custom_field   VARCHAR(255),
    version        BIGINT           NOT NULL DEFAULT 0
);

CREATE INDEX idx_demo_customer_type        ON demo_customer (customer_type);
CREATE INDEX idx_demo_customer_is_active   ON demo_customer (is_active);
CREATE INDEX idx_demo_customer_registered  ON demo_customer (registered_at);
CREATE INDEX idx_demo_customer_country_id  ON demo_customer (country_id);
CREATE INDEX idx_demo_customer_category_id ON demo_customer (category_id);

-- ─── Zamówienie (DemoOrder) ───────────────────────────────────────────────────
-- 2 tabs
-- Covers: DATE, DATETIME, DECIMAL, SELECT, BOOLEAN, TEXTAREA, RELATION, RELATION_LIST
-- Filters: STARTS_WITH, RANGE, IN, BOOLEAN, NONE, EXACT

CREATE TABLE demo_order (
    id            BIGSERIAL        PRIMARY KEY,
    order_number  VARCHAR(255)     NOT NULL,
    order_date    VARCHAR(255),
    delivery_date VARCHAR(255),
    status        VARCHAR(20),
    is_priority   BOOLEAN          NOT NULL DEFAULT FALSE,
    total_amount  DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    notes         TEXT,
    customer_id   BIGINT           REFERENCES demo_customer (id),
    version       BIGINT           NOT NULL DEFAULT 0
);

CREATE INDEX idx_demo_order_status       ON demo_order (status);
CREATE INDEX idx_demo_order_customer_id  ON demo_order (customer_id);
CREATE INDEX idx_demo_order_date         ON demo_order (order_date);
CREATE INDEX idx_demo_order_total_amount ON demo_order (total_amount);
CREATE INDEX idx_demo_order_is_priority  ON demo_order (is_priority);

-- ─── Produkt (DemoProduct) ───────────────────────────────────────────────────
-- 3 tabs
-- Covers: TEXT, TEXTAREA, NUMBER, DECIMAL, BOOLEAN, SELECT, MULTI_SELECT,
--         URL, FILE, COLOR, JSON, RELATION (×3)
-- Filters: CONTAINS, EXACT, RANGE, IN, BOOLEAN, NONE

CREATE TABLE demo_product (
    id          BIGSERIAL        PRIMARY KEY,
    name        VARCHAR(255)     NOT NULL,
    sku         VARCHAR(255)     NOT NULL UNIQUE,
    price       DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    quantity    INTEGER          NOT NULL DEFAULT 0,
    is_active   BOOLEAN          NOT NULL DEFAULT TRUE,
    category_id BIGINT           REFERENCES demo_category (id),
    description TEXT,
    weight      DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    tags        VARCHAR(255),
    country_id  BIGINT           REFERENCES demo_country (id),
    supplier_id BIGINT           REFERENCES demo_supplier (id),
    image_url   VARCHAR(255),
    product_url VARCHAR(255),
    color       VARCHAR(7),
    metadata    TEXT
);

CREATE INDEX idx_demo_product_is_active   ON demo_product (is_active);
CREATE INDEX idx_demo_product_price       ON demo_product (price);
CREATE INDEX idx_demo_product_quantity    ON demo_product (quantity);
CREATE INDEX idx_demo_product_category_id ON demo_product (category_id);

-- ─── Pozycja zamówienia (DemoOrderItem) ──────────────────────────────────────
-- flat, child of DemoOrder
-- Covers: NUMBER, DECIMAL, TEXT, RELATION (×2)

CREATE TABLE demo_order_item (
    id         BIGSERIAL        PRIMARY KEY,
    order_id   BIGINT           REFERENCES demo_order (id),
    product_id BIGINT           REFERENCES demo_product (id),
    quantity   INTEGER          NOT NULL DEFAULT 1,
    unit_price DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    line_total DOUBLE PRECISION NOT NULL DEFAULT 0.0
);

CREATE INDEX idx_demo_order_item_order_id   ON demo_order_item (order_id);
CREATE INDEX idx_demo_order_item_product_id ON demo_order_item (product_id);

