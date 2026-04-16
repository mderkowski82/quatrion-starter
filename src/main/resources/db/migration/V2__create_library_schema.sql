-- =============================================================================
--  Quatrion Portal Demo — moduł Library (Biblioteka)
--  Tabele: genre, author, book, member, loan
--
--  Nazwy kolumn generowane przez CamelCaseToUnderscoresNamingStrategy:
--    isActive          → is_active
--    parentId          → parent_id
--    firstName         → first_name
--    lastName          → last_name
--    birthDate         → birth_date
--    photoUrl          → photo_url
--    internalNotes     → internal_notes
--    coverColor        → cover_color
--    pageCount         → page_count
--    dailyPrice        → daily_price
--    publishedDate     → published_date
--    authorId          → author_id
--    genreId           → genre_id
--    membershipType    → membership_type
--    avatarUrl         → avatar_url
--    passwordHash      → password_hash
--    registrationDate  → registration_date
--    expiryDate        → expiry_date
--    maxLoans          → max_loans
--    memberId          → member_id
--    bookId            → book_id
--    loanDate          → loan_date
--    dueDate           → due_date
--    returnDate        → return_date
--    renewalCount      → renewal_count
--    overdueNotes      → overdue_notes
--    createdAt         → created_at
-- =============================================================================

-- ─── Gatunek literacki (Genre) ───────────────────────────────────────────────
-- flat, self-ref RELATION, softDelete=true
-- Covers: TEXT, COLOR, TEXTAREA, BOOLEAN, RELATION (self-ref)
-- Filters: CONTAINS, STARTS_WITH, BOOLEAN, EXACT

CREATE TABLE genre (
    id           BIGSERIAL   PRIMARY KEY,
    name         VARCHAR(100) NOT NULL UNIQUE,
    abbreviation VARCHAR(50),
    -- Kolor etykiety; DB default zgodny z defaultValue w @PortalField
    color        VARCHAR(7)  DEFAULT '#6366F1',
    description  TEXT,
    is_active    BOOLEAN     NOT NULL DEFAULT TRUE,
    parent_id    BIGINT      REFERENCES genre (id),
    -- Wymagane przez softDelete = true w @PortalEntity
    deleted      BOOLEAN     NOT NULL DEFAULT FALSE
);

-- ─── Autor (Author) ──────────────────────────────────────────────────────────
-- auditLog, 3 tabs
-- Covers: TEXT, DATE, BOOLEAN, EMAIL, URL, FILE, TEXTAREA
-- Filters: CONTAINS, RANGE, BOOLEAN, EXACT

CREATE TABLE author (
    id             BIGSERIAL    PRIMARY KEY,
    first_name     VARCHAR(80)  NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    birth_date     VARCHAR(255),
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    email          VARCHAR(200) UNIQUE,
    website        VARCHAR(300),
    photo_url      VARCHAR(500),
    bio            TEXT,
    internal_notes TEXT
);

-- ─── Książka (Book) ──────────────────────────────────────────────────────────
-- auditLog, 3 tabs, @PortalSecurity, 3x @PortalAction
-- Covers: TEXT, SELECT, BOOLEAN, COLOR, TEXTAREA, NUMBER, DECIMAL, DATE,
--         MULTI_SELECT, JSON, RELATION (×2)
-- Filters: EXACT, CONTAINS, IN, BOOLEAN, RANGE

CREATE TABLE book (
    id             BIGSERIAL        PRIMARY KEY,
    isbn           VARCHAR(20)      NOT NULL UNIQUE,
    title          VARCHAR(255)     NOT NULL,
    status         VARCHAR(20)      NOT NULL DEFAULT 'AVAILABLE',
    is_active      BOOLEAN          NOT NULL DEFAULT TRUE,
    -- Kolor okładki; DB default zgodny z defaultValue w @PortalField
    cover_color    VARCHAR(7)       DEFAULT '#E5E7EB',
    description    TEXT,
    page_count     INTEGER          NOT NULL DEFAULT 0,
    daily_price    DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    published_date VARCHAR(255),
    tags           VARCHAR(255),
    metadata       TEXT,
    author_id      BIGINT           REFERENCES author (id),
    genre_id       BIGINT           REFERENCES genre (id)
);

-- ─── Czytelnik (Member) ────────────────────────────────────��─────────────────
-- softDelete, auditLog, 4 tabs, @PortalSecurity, 3x @PortalAction
-- Covers: TEXT, SELECT, BOOLEAN, EMAIL, FILE, PASSWORD, DATE, NUMBER,
--         MULTI_SELECT, TEXTAREA, RELATION_LIST
-- Filters: CONTAINS, EXACT, STARTS_WITH, RANGE, IN, BOOLEAN

CREATE TABLE member (
    id                BIGSERIAL    PRIMARY KEY,
    first_name        VARCHAR(80)  NOT NULL,
    last_name         VARCHAR(100) NOT NULL,
    membership_type   VARCHAR(20)  NOT NULL DEFAULT 'STANDARD',
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    email             VARCHAR(200) NOT NULL UNIQUE,
    phone             VARCHAR(20),
    avatar_url        VARCHAR(500),
    password_hash     VARCHAR(200),
    registration_date VARCHAR(255),
    expiry_date       VARCHAR(255),
    max_loans         INTEGER      NOT NULL DEFAULT 3,
    tags              VARCHAR(255),
    notes             TEXT,
    -- Wymagane przez softDelete = true w @PortalEntity
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE
);

-- ─── Wypożyczenie (Loan) ─────────────────────────────────────────────────────
-- auditLog, @PortalSecurity, 2x @PortalAction (returnBook, extendLoan)
-- Covers: RELATION (×3), SELECT, DATE, NUMBER, TEXTAREA, DATETIME
-- @Formula: book_title (SELECT b.title ... WHERE b.id = book_id)
--           member_name (SELECT ... m.first_name, m.last_name ... WHERE m.id = member_id)
-- Filters: EXACT, RANGE, IN, NONE

CREATE TABLE loan (
    id            BIGSERIAL    PRIMARY KEY,
    member_id     BIGINT       NOT NULL REFERENCES member (id),
    book_id       BIGINT       NOT NULL REFERENCES book (id),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    loan_date     VARCHAR(255) NOT NULL,
    due_date      VARCHAR(255) NOT NULL,
    return_date   VARCHAR(255),
    renewal_count INTEGER      NOT NULL DEFAULT 0,
    overdue_notes TEXT,
    created_at    VARCHAR(255)
);

CREATE INDEX idx_loan_member_id ON loan (member_id);
CREATE INDEX idx_loan_book_id   ON loan (book_id);
CREATE INDEX idx_loan_status    ON loan (status);

