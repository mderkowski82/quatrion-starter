-- =============================================================================
--  Quatrion Portal Demo — V7: więzy FK i indeksy dla relacji na prawdzie JPA
--
--  Relacje panelu istnieją jako asocjacje JPA (R1); baza egzekwuje integralność
--  (R7) wraz z indeksami wspierającymi joiny i kasowanie (KTD9).
--
--  Tabele z V1 (CRM/Katalog), V2 (Library) i V6 (task_run_file) miały już
--  klauzule REFERENCES — ta migracja domyka luki:
--    1. loan_history_csv_task.member_id → brakujący FK do member (id)
--       (opcjonalna asocjacja LoanHistoryCsvTask.member; kolumna nullable)
--    2. Brakujące indeksy na kolumnach FK używanych w joinach i planowaniu
--       kasowania: book.author_id, book.genre_id, genre.parent_id,
--       demo_product.country_id, demo_product.supplier_id
--
--  Nazwy indeksów są identyczne z @Table(indexes) w encjach, żeby schemat
--  z Flyway (prod) i z hibernate update (dev) był zgodny.
-- =============================================================================

-- ─── 1. FK: zadanie CSV → czytelnik (opcjonalny filtr członkowski) ────────────

ALTER TABLE loan_history_csv_task
    ADD CONSTRAINT fk_loan_csv_task_member
    FOREIGN KEY (member_id) REFERENCES member (id);

CREATE INDEX idx_loan_csv_task_member ON loan_history_csv_task (member_id);

-- ─── 1b. DashboardWidget: nazwa tabeli zgodna z modelem ───────────────────────
-- Encja nie ma @Table (mapuje się na dashboard_widget), a V3 utworzył
-- portal_dashboard_widget. Sekwencja ZOSTAJE (encja wskazuje jawnie
-- sequenceName = portal_dashboard_widget_seq); default nextval jest
-- przechowywany przez OID, więc rename jest przezroczysty.
ALTER TABLE portal_dashboard_widget RENAME TO dashboard_widget;

-- ─── 1c. PermissionMapping: nazwa tabeli zgodna z modelem ─────────────────────
-- Encja ma @Table bez name (mapuje się na permission_mapping), a V4 utworzył
-- iam_permission_mapping. Kolumny są zgodne 1:1 (w tym UNIQUE na parze).
ALTER TABLE iam_permission_mapping RENAME TO permission_mapping;

-- ─── 1d. TaskRun.lastStatus: brakująca kolumna ────────────────────────────────
-- Pole @Enumerated(STRING), opcjonalne; V6 nie zawiera kolumny.
-- VARCHAR(30) jak sąsiedni status (wartości: URUCHOMIONO/ZAKONCZONE/ERROR/ANULOWANE).
ALTER TABLE task_run ADD COLUMN last_status VARCHAR(30);

-- ─── 2. Indeksy join/delete na istniejących kolumnach FK ─────────────────────

-- Book.author / Book.genre (ManyToOne, V2 ma REFERENCES bez indeksów)
CREATE INDEX idx_book_author_id ON book (author_id);
CREATE INDEX idx_book_genre_id  ON book (genre_id);

-- Genre.parent (samoreferencja ManyToOne, V2 ma REFERENCES bez indeksu)
CREATE INDEX idx_genre_parent_id ON genre (parent_id);

-- DemoProduct.country / DemoProduct.supplier (ManyToOne, V1 ma REFERENCES;
-- category_id już indeksowane jako idx_demo_product_category_id)
CREATE INDEX idx_demo_product_country_id  ON demo_product (country_id);
CREATE INDEX idx_demo_product_supplier_id ON demo_product (supplier_id);

-- ─── 3. Kolumny audytowe z AuditableEntity (@MappedSuperclass) ────────────────
-- V2 nie zawiera kolumn audytowych, więc prod validate (strategia validate,
-- Flyway jedynym właścicielem schematu) zgłasza brak kolumn. Typy 1:1 z modelem:
-- createdAt/updatedAt VARCHAR(30), createdBy/updatedBy VARCHAR(100).

ALTER TABLE author ADD COLUMN created_at VARCHAR(30);
ALTER TABLE author ADD COLUMN updated_at VARCHAR(30);
ALTER TABLE author ADD COLUMN created_by VARCHAR(100);
ALTER TABLE author ADD COLUMN updated_by VARCHAR(100);

ALTER TABLE book ADD COLUMN created_at VARCHAR(30);
ALTER TABLE book ADD COLUMN updated_at VARCHAR(30);
ALTER TABLE book ADD COLUMN created_by VARCHAR(100);
ALTER TABLE book ADD COLUMN updated_by VARCHAR(100);

ALTER TABLE genre ADD COLUMN created_at VARCHAR(30);
ALTER TABLE genre ADD COLUMN updated_at VARCHAR(30);
ALTER TABLE genre ADD COLUMN created_by VARCHAR(100);
ALTER TABLE genre ADD COLUMN updated_by VARCHAR(100);

ALTER TABLE member ADD COLUMN created_at VARCHAR(30);
ALTER TABLE member ADD COLUMN updated_at VARCHAR(30);
ALTER TABLE member ADD COLUMN created_by VARCHAR(100);
ALTER TABLE member ADD COLUMN updated_by VARCHAR(100);

ALTER TABLE loan ADD COLUMN updated_at VARCHAR(30);
ALTER TABLE loan ADD COLUMN created_by VARCHAR(100);
ALTER TABLE loan ADD COLUMN updated_by VARCHAR(100);

-- loan.created_at istnieje od V2 jako VARCHAR(255); model wymaga VARCHAR(30)
-- (wartości to stemple ISO-8601, mieszczą się w 30 znakach).
ALTER TABLE loan ALTER COLUMN created_at TYPE VARCHAR(30);
