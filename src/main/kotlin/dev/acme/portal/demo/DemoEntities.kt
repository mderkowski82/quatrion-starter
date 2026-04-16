package dev.acme.portal.demo

import dev.quatrion.portal.annotation.*
import jakarta.persistence.*
import dev.quatrion.portal.annotation.Regex

// ─────────────────────────────────────────────────────────────
//  Tab enums
// ─────────────────────────────────────────────────────────────

enum class CustomerTab(
    override val label: String,
    override val icon: String,
    override val order: Int
) : PortalTab {
    BASIC("Podstawowe", "user", 0),
    CONTACT("Kontakt", "phone", 1),
    FINANCIAL("Finansowe", "dollar-sign", 2),
    SYSTEM("System", "settings", 3)
}

enum class OrderTab(
    override val label: String,
    override val icon: String,
    override val order: Int
) : PortalTab {
    INFO("Zamówienie", "shopping-cart", 0),
    ITEMS("Pozycje", "list", 1)
}

enum class ProductTab(
    override val label: String,
    override val icon: String,
    override val order: Int
) : PortalTab {
    INFO("Informacje", "info", 0),
    DETAILS("Szczegóły", "list", 1),
    MEDIA("Media", "image", 2)
}

// ─────────────────────────────────────────────────────────────
//  Domain enums – used as SELECT / MULTI_SELECT field types
// ─────────────────────────────────────────────────────────────

enum class Continent(val label: String) {
    AFRICA("Africa"),
    ASIA("Asia"),
    EUROPE("Europe"),
    NORTH_AMERICA("North America"),
    SOUTH_AMERICA("South America"),
    AUSTRALIA("Australia"),
    ANTARCTICA("Antarctica");

    override fun toString() = label
}

enum class CustomerType(val label: String) {
    VIP("VIP"),
    PREMIUM("Premium"),
    REGULAR("Regular"),
    NEW("New"),
    BUSINESS("Business");

    override fun toString() = label
}

enum class CustomerTag {
    VIP, NEW, REGULAR, PREMIUM, BUSINESS
}

enum class OrderStatus(val label: String) {
    DRAFT("Draft"),
    ACTIVE("Active"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    override fun toString() = label
}

enum class ProductTag {
    ELEKTRONIKA, PREMIUM, NOWE, WYPRZEDAZ, POPULARNE
}

// ─────────────────────────────────────────────────────────────
//  DemoCountry  –  flat entity (no tabs)
//  Covers: TEXT, SELECT, BOOLEAN
//  Filters: CONTAINS, EXACT, IN, BOOLEAN
// ─────────────────────────────────────────────────────────────

@Entity
@Table(
    indexes = [
        jakarta.persistence.Index(name = "idx_demo_country_code",      columnList = "code"),
        jakarta.persistence.Index(name = "idx_demo_country_continent", columnList = "continent"),
        jakarta.persistence.Index(name = "idx_demo_country_is_eu",     columnList = "is_eu")
    ]
)
@PortalEntity(
    label = "Kraj",
    module = "CRM",
    icon = "globe",
    order = 1,
    description = "Słownik krajów z podstawowymi informacjami"
)
class DemoCountry {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PortalField(label = "ID", order = 0, readonly = true, showInFilter = false)
    var id: Long = 0

    @Column(nullable = false)
    @PortalField(label = "Nazwa", order = 1, required = true, filterType = FilterType.CONTAINS)
    var name: String = ""

    @Column(length = 3, nullable = false)
    @Regex(
        pattern = """^[A-Za-z]{2,3}$""",
        message = "Kod ISO musi zawierać 2 lub 3 litery"
    )
    @PortalField(
        label = "Kod ISO",
        order = 2,
        required = true,
        renderer = RendererType.TEXT,
        filterType = FilterType.EXACT,
        placeholder = "np. PL",
        tooltip = "Dwu- lub trzyliterowy kod ISO 3166",
        width = 80
    )
    var code: String = ""

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    @PortalField(
        label = "Kontynent",
        order = 3,
        renderer = RendererType.SELECT,
        filterType = FilterType.IN,
        selectEnum = _root_ide_package_.dev.acme.portal.demo.Continent::class
    )
    var continent: dev.acme.portal.demo.Continent? = null

    @Column
    @PortalField(
        label = "Strefa EU",
        order = 4,
        renderer = RendererType.BOOLEAN,
        filterType = FilterType.BOOLEAN
    )
    var isEu: Boolean = false
}

// ─────────────────────────────────────────────────────────────
//  DemoCategory  –  flat entity with self-referential RELATION
//  Covers: TEXT, TEXTAREA, COLOR, BOOLEAN, RELATION
//  Filters: CONTAINS, NONE, BOOLEAN, EXACT
// ─────────────────────────────────────────────────────────────

@Entity
@Table(
    indexes = [
        jakarta.persistence.Index(name = "idx_demo_category_is_active", columnList = "is_active"),
        jakarta.persistence.Index(name = "idx_demo_category_parent_id", columnList = "parent_id")
    ]
)
@PortalEntity(
    label = "Kategoria",
    module = "CRM",
    icon = "tag",
    order = 2,
    description = "Hierarchiczny słownik kategorii"
)
class DemoCategory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PortalField(label = "ID", order = 0, readonly = true, showInFilter = false)
    var id: Long = 0

    @Column(nullable = false)
    @PortalField(label = "Nazwa", order = 1, required = true, filterType = FilterType.CONTAINS)
    var name: String = ""

    @Column(columnDefinition = "TEXT")
    @PortalField(
        label = "Opis",
        order = 2,
        renderer = RendererType.TEXTAREA,
        filterType = FilterType.NONE,
        showInTable = false
    )
    var description: String = ""

    @Column(length = 7)
    @PortalField(label = "Kolor", order = 3, renderer = RendererType.COLOR, filterType = FilterType.NONE)
    var color: String = ""

    @Column
    @PortalField(
        label = "Aktywna",
        order = 4,
        renderer = RendererType.BOOLEAN,
        filterType = FilterType.BOOLEAN
    )
    var isActive: Boolean = true

    @Column
    @PortalField(
        label = "Kategoria nadrzędna",
        order = 5,
        renderer = RendererType.RELATION,
        filterType = FilterType.EXACT,
        showInTable = false
    )
    @PortalRelation(targetEntity = DemoCategory::class, editable = true, displayFields = ["name"], searchFields = ["name"])
    @PortalLookup(labelField = "name", valueField = "id")
    var parentId: Long? = null
}

// ─────────────────────────────────────────────────────────────
//  DemoCustomer  –  4 tabs, ALL renderer types
//  Covers: TEXT, TEXTAREA, NUMBER, DECIMAL, DATE, DATETIME,
//          BOOLEAN, SELECT, MULTI_SELECT, EMAIL, URL, PASSWORD,
//          COLOR, FILE, JSON, CUSTOM, RELATION (×2)
//  Filters: CONTAINS, EXACT, RANGE, IN, BOOLEAN, NONE, STARTS_WITH
// ─────────────────────────────────────────────────────────────

@Entity
@Table(
    indexes = [
        jakarta.persistence.Index(name = "idx_demo_customer_type",        columnList = "customer_type"),
        jakarta.persistence.Index(name = "idx_demo_customer_is_active",   columnList = "is_active"),
        jakarta.persistence.Index(name = "idx_demo_customer_registered",  columnList = "registered_at"),
        jakarta.persistence.Index(name = "idx_demo_customer_country_id",  columnList = "country_id"),
        jakarta.persistence.Index(name = "idx_demo_customer_category_id", columnList = "category_id")
    ]
)
@PortalEntity(
    label = "Klient",
    module = "CRM",
    icon = "users",
    order = 3,
    description = "Główna encja klientów — pokrywa wszystkie typy pól i relacji",
    tabs = _root_ide_package_.dev.acme.portal.demo.CustomerTab::class,
    pageSize = 20
)
@PortalAction(
    name = "activate",
    label = "Aktywuj",
    icon = "check-circle",
    handler = _root_ide_package_.dev.acme.portal.demo.ActivateCustomerHandler::class,
    confirmMessage = "Czy aktywować tego klienta?",
    order = 1,
    variant = "default"
)
@PortalAction(
    name = "deactivate",
    label = "Dezaktywuj",
    icon = "x-circle",
    handler = _root_ide_package_.dev.acme.portal.demo.DeactivateCustomerHandler::class,
    confirmMessage = "Czy dezaktywować tego klienta?",
    order = 2,
    variant = "destructive"
)
@PortalAction(
    name = "sendWelcomeEmail",
    label = "Wyślij e-mail powitalny",
    icon = "mail",
    handler = _root_ide_package_.dev.acme.portal.demo.SendEmailHandler::class,
    order = 3,
    variant = "outline",
    bulkAllowed = true
)
class DemoCustomer {

    // ── BASIC tab ──────────────────────────────────────────

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PortalField(label = "ID", tab = "BASIC", order = 0, readonly = true, showInFilter = false)
    var id: Long = 0

    @Column(nullable = false)
    @PortalField(
        label = "Imię i nazwisko",
        tab = "BASIC",
        order = 1,
        required = true,
        renderer = RendererType.TEXT,
        filterType = FilterType.CONTAINS
    )
    var name: String = ""

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    @PortalField(
        label = "Typ klienta",
        tab = "BASIC",
        order = 2,
        renderer = RendererType.SELECT,
        filterType = FilterType.IN,
        placeholder = "Wybierz typ",
        selectEnum = _root_ide_package_.dev.acme.portal.demo.CustomerType::class
    )
    var customerType: dev.acme.portal.demo.CustomerType? = null

    @Column
    @PortalField(
        label = "Aktywny",
        tab = "BASIC",
        order = 3,
        renderer = RendererType.BOOLEAN,
        filterType = FilterType.BOOLEAN
    )
    var isActive: Boolean = true

    @Column(length = 7)
    @PortalField(
        label = "Ulubiony kolor",
        tab = "BASIC",
        order = 4,
        renderer = RendererType.COLOR,
        filterType = FilterType.NONE,
        showInTable = false
    )
    @PortalDependency(
        field = "customerType",
        operator = DependencyOperator.EQ,
        value = "VIP",
        visibility = DependencyVisibility.SHOW,
        message = "Ulubiony kolor jest dostępny tylko dla klientów VIP lub Premium."
    )
    @PortalDependency(
        field = "customerType",
        operator = DependencyOperator.EQ,
        value = "Premium",
        visibility = DependencyVisibility.SHOW,
        message = "Ulubiony kolor jest dostępny tylko dla klientów VIP lub Premium."
    )
    var favoriteColor: String = ""

    // ── CONTACT tab ────────────────────────────────────────

    @Column(unique = true)
    @PortalField(
        label = "E-mail",
        tab = "CONTACT",
        order = 1,
        renderer = RendererType.EMAIL,
        filterType = FilterType.EXACT
    )
    var email: String = ""

    @Column(length = 20)
    @Regex(
        pattern = """^\+?[\d\s\-]{7,20}$""",
        message = "Numer telefonu może zawierać cyfry, spacje, myślniki i opcjonalny znak +"
    )
    @PortalField(
        label = "Telefon",
        tab = "CONTACT",
        order = 2,
        renderer = RendererType.TEXT,
        filterType = FilterType.STARTS_WITH,
        placeholder = "+48 …"
    )
    var phone: String = ""

    @Column
    @PortalField(
        label = "Strona WWW",
        tab = "CONTACT",
        order = 3,
        renderer = RendererType.URL,
        filterType = FilterType.NONE,
        showInTable = false
    )
    var website: String = ""

    // Dates are stored as ISO-8601 strings (YYYY-MM-DD / YYYY-MM-DDTHH:mm) because
    // GenericCrudService.coerceValue() handles primitive types only. Use LocalDate /
    // LocalDateTime and extend coerceValue() when proper type-safety is required.
    @Column
    @PortalField(
        label = "Data urodzenia",
        tab = "CONTACT",
        order = 4,
        renderer = RendererType.DATE,
        filterType = FilterType.RANGE,
        showInTable = false,
        tooltip = "Format: YYYY-MM-DD"
    )
    var birthDate: String = ""

    @Column
    @PortalField(
        label = "Data rejestracji",
        tab = "CONTACT",
        order = 5,
        renderer = RendererType.DATETIME,
        filterType = FilterType.RANGE,
        tooltip = "Format: YYYY-MM-DDTHH:mm"
    )
    var registeredAt: String = ""

    @Column
    @PortalField(
        label = "Kraj",
        tab = "CONTACT",
        order = 6,
        renderer = RendererType.RELATION,
        filterType = FilterType.EXACT,
        showInTable = false
    )
    @PortalRelation(targetEntity = _root_ide_package_.dev.acme.portal.demo.DemoCountry::class, editable = true, displayFields = ["name", "code"], searchFields = ["name", "code"])
    @PortalLookup(labelField = "name", valueField = "id")
    var countryId: Long? = null

    // ── FINANCIAL tab ──────────────────────────────────────

    @Column
    @PortalField(
        label = "Limit kredytowy",
        tab = "FINANCIAL",
        order = 1,
        renderer = RendererType.DECIMAL,
        filterType = FilterType.RANGE,
        placeholder = "0.00"
    )
    @PortalDependency(
        field = "customerType",
        operator = DependencyOperator.EQ,
        value = "New",
        max = "5000",
        message = "Nowy klient może mieć limit kredytowy maksymalnie 5000."
    )
    @PortalDependency(
        field = "customerType",
        operator = DependencyOperator.EQ,
        value = "Regular",
        max = "25000"
    )
    @PortalDependency(
        field = "customerType",
        operator = DependencyOperator.EQ,
        value = "Premium",
        min = "1000",
        max = "100000"
    )
    @PortalDependency(
        field = "customerType",
        operator = DependencyOperator.EQ,
        value = "VIP",
        min = "5000",
        max = "500000"
    )
    @PortalDependency(
        field = "customerType",
        operator = DependencyOperator.EQ,
        value = "Business",
        min = "5000",
        max = "500000"
    )
    var creditLimit: Double = 0.0

    @Column
    @PortalField(
        label = "Punkty lojalnościowe",
        tab = "FINANCIAL",
        order = 2,
        renderer = RendererType.NUMBER,
        filterType = FilterType.RANGE
    )
    var loyaltyPoints: Int = 0

    @Column
    @PortalField(
        label = "Tagi",
        tab = "FINANCIAL",
        order = 3,
        renderer = RendererType.MULTI_SELECT,
        filterType = FilterType.IN,
        showInTable = false,
        tooltip = "Wartości oddzielone przecinkiem, np. VIP,NEW,REGULAR",
        selectEnum = _root_ide_package_.dev.acme.portal.demo.CustomerTag::class
    )
    @PortalDependency(
        field = "customerType",
        operator = DependencyOperator.EQ,
        value = "New",
        allowedValues = ["NEW"],
        message = "Nowy klient może otrzymać wyłącznie tag NEW."
    )
    @PortalDependency(
        field = "customerType",
        operator = DependencyOperator.EQ,
        value = "Regular",
        allowedValues = ["REGULAR", "NEW"]
    )
    @PortalDependency(
        field = "customerType",
        operator = DependencyOperator.EQ,
        value = "Premium",
        allowedValues = ["PREMIUM", "REGULAR", "NEW"]
    )
    @PortalDependency(
        field = "customerType",
        operator = DependencyOperator.EQ,
        value = "VIP",
        allowedValues = ["VIP", "PREMIUM", "BUSINESS"]
    )
    @PortalDependency(
        field = "customerType",
        operator = DependencyOperator.EQ,
        value = "Business",
        allowedValues = ["BUSINESS", "PREMIUM"]
    )
    var tags: String = ""

    @Column
    @PortalField(
        label = "Kategoria",
        tab = "FINANCIAL",
        order = 4,
        renderer = RendererType.RELATION,
        filterType = FilterType.EXACT,
        showInTable = false
    )
    @PortalRelation(targetEntity = _root_ide_package_.dev.acme.portal.demo.DemoCategory::class, editable = true, displayFields = ["name"], searchFields = ["name"])
    @PortalLookup(labelField = "name", valueField = "id")
    var categoryId: Long? = null

    // ── SYSTEM tab ─────────────────────────────────────────

    @Column
    @PortalField(
        label = "Hasło",
        tab = "SYSTEM",
        order = 1,
        renderer = RendererType.PASSWORD,
        filterType = FilterType.NONE,
        showInTable = false,
        showInFilter = false
    )
    var password: String = ""

    @Column
    @PortalField(
        label = "Zdjęcie profilowe",
        tab = "SYSTEM",
        order = 2,
        renderer = RendererType.FILE,
        filterType = FilterType.NONE,
        showInTable = false,
        showInFilter = false
    )
    var avatar: String = ""

    @Column(columnDefinition = "TEXT")
    @PortalField(
        label = "Dane dodatkowe (JSON)",
        tab = "SYSTEM",
        order = 3,
        renderer = RendererType.JSON,
        filterType = FilterType.NONE,
        showInTable = false,
        showInFilter = false
    )
    var extraData: String = ""

    @Column(columnDefinition = "TEXT")
    @PortalField(
        label = "Notatki",
        tab = "SYSTEM",
        order = 4,
        renderer = RendererType.TEXTAREA,
        filterType = FilterType.NONE,
        showInTable = false
    )
    var notes: String = ""

    @Column
    @PortalField(
        label = "Pole niestandardowe",
        tab = "SYSTEM",
        order = 5,
        renderer = RendererType.CUSTOM,
        filterType = FilterType.NONE,
        showInTable = false,
        showInFilter = false,
        tooltip = "Demonstracja renderera CUSTOM"
    )
    var customField: String = ""


    @jakarta.persistence.Transient
    @PortalField(
        label = "Zamówienia",
        tab = "SYSTEM",
        order = 6,
        renderer = RendererType.RELATION_LIST,
        filterType = FilterType.NONE,
        showInTable = false,
        showInFilter = false,
        tooltip = "Lista zamówień powiązanych z klientem"
    )
    @PortalRelation(
        targetEntity = _root_ide_package_.dev.acme.portal.demo.DemoOrder::class,
        editable = false,
        displayFields = ["orderNumber", "orderDate", "totalAmount", "status"],
        searchFields = ["orderNumber"]
    )
    @PortalLookup(labelField = "orderNumber", valueField = "id")
    var orders: List<dev.acme.portal.demo.DemoOrder>? = null

    /**
     * JPA optimistic locking version — prevents lost-update race conditions.
     * Hibernate increments this automatically on every UPDATE.
     * columnDefinition includes DEFAULT 0 so that Hibernate hbm2ddl=update generates
     * `ALTER TABLE ... ADD COLUMN version bigint not null default 0` (no NPE on existing rows).
     */
    @Column(columnDefinition = "bigint not null default 0")
    @jakarta.persistence.Version
    var version: Long = 0
}

// ─────────────────────────────────────────────────────────────
//  DemoOrder  –  2 tabs
//  Covers: DATE, DATETIME, DECIMAL, SELECT, BOOLEAN, TEXTAREA,
//          RELATION (to Customer), RELATION_LIST (order items)
//  Filters: STARTS_WITH, RANGE, IN, BOOLEAN, NONE, EXACT
// ─────────────────────────────────────────────────────────────

@Entity
@Table(
    indexes = [
        jakarta.persistence.Index(name = "idx_demo_order_status",       columnList = "status"),
        jakarta.persistence.Index(name = "idx_demo_order_customer_id",  columnList = "customer_id"),
        jakarta.persistence.Index(name = "idx_demo_order_date",         columnList = "order_date"),
        jakarta.persistence.Index(name = "idx_demo_order_total_amount", columnList = "total_amount"),
        jakarta.persistence.Index(name = "idx_demo_order_is_priority",  columnList = "is_priority")
    ]
)
@PortalEntity(
    label = "Zamówienie",
    module = "CRM",
    icon = "shopping-cart",
    order = 4,
    description = "Zamówienia klientów z pozycjami",
    tabs = _root_ide_package_.dev.acme.portal.demo.OrderTab::class,
    pageSize = 15
)
@PortalAction(
    name = "processOrder",
    label = "Przetwórz zamówienie",
    icon = "play",
    handler = _root_ide_package_.dev.acme.portal.demo.ProcessOrderHandler::class,
    formModel = _root_ide_package_.dev.acme.portal.demo.ProcessOrderForm::class,
    confirmMessage = "Czy przetworzyć zamówienie?",
    order = 1
)
@PortalAction(
    name = "cancelOrder",
    label = "Anuluj zamówienie",
    icon = "x",
    handler = _root_ide_package_.dev.acme.portal.demo.CancelOrderHandler::class,
    confirmMessage = "Czy na pewno anulować zamówienie? Operacji nie można cofnąć.",
    order = 2,
    variant = "destructive"
)
class DemoOrder {

    // ── INFO tab ───────────────────────────────────────────

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PortalField(label = "ID", tab = "INFO", order = 0, readonly = true, showInFilter = false)
    var id: Long = 0

    @Column(nullable = false)
    @PortalField(
        label = "Numer zamówienia",
        tab = "INFO",
        order = 1,
        required = true,
        renderer = RendererType.TEXT,
        filterType = FilterType.STARTS_WITH
    )
    var orderNumber: String = ""

    // Dates stored as ISO-8601 strings for compatibility with GenericCrudService.coerceValue()
    @Column
    @PortalField(
        label = "Data zamówienia",
        tab = "INFO",
        order = 2,
        renderer = RendererType.DATE,
        filterType = FilterType.RANGE,
        tooltip = "Format: YYYY-MM-DD"
    )
    var orderDate: String = ""

    @Column
    @PortalField(
        label = "Planowana dostawa",
        tab = "INFO",
        order = 3,
        renderer = RendererType.DATETIME,
        filterType = FilterType.RANGE,
        showInTable = false,
        tooltip = "Format: YYYY-MM-DDTHH:mm"
    )
    var deliveryDate: String = ""

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    @PortalField(
        label = "Status",
        tab = "INFO",
        order = 4,
        renderer = RendererType.SELECT,
        filterType = FilterType.IN,
        selectEnum = _root_ide_package_.dev.acme.portal.demo.OrderStatus::class
    )
    var status: dev.acme.portal.demo.OrderStatus? = null

    @Column
    @PortalField(
        label = "Priorytetowe",
        tab = "INFO",
        order = 5,
        renderer = RendererType.BOOLEAN,
        filterType = FilterType.BOOLEAN
    )
    var isPriority: Boolean = false

    @Column
    @PortalField(
        label = "Wartość zamówienia",
        tab = "INFO",
        order = 6,
        renderer = RendererType.DECIMAL,
        filterType = FilterType.RANGE
    )
    var totalAmount: Double = 0.0

    @Column(columnDefinition = "TEXT")
    @PortalField(
        label = "Uwagi",
        tab = "INFO",
        order = 7,
        renderer = RendererType.TEXTAREA,
        filterType = FilterType.NONE,
        showInTable = false
    )
    var notes: String = ""

    @Column
    @PortalField(
        label = "Klient",
        tab = "INFO",
        order = 8,
        renderer = RendererType.RELATION,
        filterType = FilterType.EXACT
    )
    @PortalRelation(targetEntity = _root_ide_package_.dev.acme.portal.demo.DemoCustomer::class, editable = true, displayFields = ["name", "email"], searchFields = ["name", "email"])
    @PortalLookup(labelField = "name", valueField = "id")
    var customerId: Long? = null

    // ── ITEMS tab ──────────────────────────────────────────

    @jakarta.persistence.Transient
    @PortalField(
        label = "Pozycje zamówienia",
        tab = "ITEMS",
        order = 1,
        renderer = RendererType.RELATION_LIST,
        filterType = FilterType.NONE,
        showInTable = false,
        showInFilter = false
    )
    @PortalRelation(
        targetEntity = _root_ide_package_.dev.acme.portal.demo.DemoOrderItem::class,
        editable = true,
        inlineEdit = true,
        displayFields = ["productId", "quantity", "unitPrice"],
        maxItems = 100
    )
    var items: List<dev.acme.portal.demo.DemoOrderItem>? = null

    /** JPA optimistic locking — see DemoCustomer.version for details. */
    @Column(columnDefinition = "bigint not null default 0")
    @jakarta.persistence.Version
    var version: Long = 0
}

// ─────────────────────────────────────────────────────────────
//  DemoOrderItem  –  no tabs (flat, child of Order)
//  Covers: NUMBER, DECIMAL, TEXT, RELATION (to Product)
// ─────────────────────────────────────────────────────────────

@Entity
@Table(
    indexes = [
        jakarta.persistence.Index(name = "idx_demo_order_item_order_id",   columnList = "order_id"),
        jakarta.persistence.Index(name = "idx_demo_order_item_product_id", columnList = "product_id")
    ]
)
@PortalEntity(
    label = "Pozycja zamówienia",
    module = "CRM",
    icon = "list",
    order = 5,
    description = "Pozycje składające się na zamówienie",
    allowCreate = false
)
class DemoOrderItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PortalField(label = "ID", order = 0, readonly = true, showInFilter = false)
    var id: Long = 0

    @Column
    @PortalField(
        label = "Zamówienie",
        order = 1,
        renderer = RendererType.RELATION,
        filterType = FilterType.EXACT
    )
    @PortalRelation(targetEntity = _root_ide_package_.dev.acme.portal.demo.DemoOrder::class, editable = false, displayFields = ["orderNumber"], searchFields = ["orderNumber"])
    @PortalLookup(labelField = "orderNumber", valueField = "id")
    var orderId: Long? = null

    @Column
    @PortalField(
        label = "Produkt",
        order = 2,
        renderer = RendererType.RELATION,
        filterType = FilterType.EXACT
    )
    @PortalRelation(targetEntity = _root_ide_package_.dev.acme.portal.demo.DemoProduct::class, editable = true, displayFields = ["name", "sku"], searchFields = ["name", "sku"])
    @PortalLookup(labelField = "name", valueField = "id")
    var productId: Long? = null

    @Column
    @PortalField(
        label = "Ilość",
        order = 3,
        renderer = RendererType.NUMBER,
        filterType = FilterType.RANGE,
        required = true
    )
    var quantity: Int = 1

    @Column
    @PortalField(
        label = "Cena jednostkowa",
        order = 4,
        renderer = RendererType.DECIMAL,
        filterType = FilterType.RANGE
    )
    var unitPrice: Double = 0.0

    @Column
    @PortalField(
        label = "Wartość",
        order = 5,
        renderer = RendererType.DECIMAL,
        filterType = FilterType.RANGE,
        readonly = true
    )
    var lineTotal: Double = 0.0
}

// ─────────────────────────────────────────────────────────────
//  DemoProduct  –  3 tabs
//  Covers: TEXT, TEXTAREA, NUMBER, DECIMAL, BOOLEAN, SELECT,
//          MULTI_SELECT, URL, FILE, COLOR, JSON, RELATION (×3)
//  Filters: CONTAINS, EXACT, RANGE, IN, BOOLEAN, NONE
// ─────────────────────────────────────────────────────────────

@Entity
@Table(
    indexes = [
        jakarta.persistence.Index(name = "idx_demo_product_is_active",   columnList = "is_active"),
        jakarta.persistence.Index(name = "idx_demo_product_price",       columnList = "price"),
        jakarta.persistence.Index(name = "idx_demo_product_quantity",    columnList = "quantity"),
        jakarta.persistence.Index(name = "idx_demo_product_category_id", columnList = "category_id")
    ]
)
@PortalEntity(
    label = "Produkt",
    module = "Katalog",
    icon = "package",
    order = 1,
    description = "Produkty w katalogu — pokrywa dodatkowe typy pól",
    tabs = _root_ide_package_.dev.acme.portal.demo.ProductTab::class,
    pageSize = 30
)
class DemoProduct {

    // ── INFO tab ───────────────────────────────────────────

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PortalField(label = "ID", tab = "INFO", order = 0, readonly = true, showInFilter = false)
    var id: Long = 0

    @Column(nullable = false)
    @PortalField(
        label = "Nazwa produktu",
        tab = "INFO",
        order = 1,
        required = true,
        renderer = RendererType.TEXT,
        filterType = FilterType.CONTAINS
    )
    var name: String = ""

    @Column(unique = true, nullable = false)
    @PortalField(
        label = "SKU",
        tab = "INFO",
        order = 2,
        required = true,
        renderer = RendererType.TEXT,
        filterType = FilterType.EXACT,
        placeholder = "np. PROD-001"
    )
    var sku: String = ""

    @Column
    @PortalField(
        label = "Cena",
        tab = "INFO",
        order = 3,
        renderer = RendererType.DECIMAL,
        filterType = FilterType.RANGE,
        required = true
    )
    var price: Double = 0.0

    @Column
    @PortalField(
        label = "Stan magazynowy",
        tab = "INFO",
        order = 4,
        renderer = RendererType.NUMBER,
        filterType = FilterType.RANGE
    )
    var quantity: Int = 0

    @Column
    @PortalField(
        label = "Aktywny",
        tab = "INFO",
        order = 5,
        renderer = RendererType.BOOLEAN,
        filterType = FilterType.BOOLEAN
    )
    var isActive: Boolean = true

    @Column
    @PortalField(
        label = "Kategoria",
        tab = "INFO",
        order = 6,
        renderer = RendererType.RELATION,
        filterType = FilterType.EXACT
    )
    @PortalRelation(targetEntity = _root_ide_package_.dev.acme.portal.demo.DemoCategory::class, editable = true, displayFields = ["name"], searchFields = ["name"])
    @PortalLookup(labelField = "name", valueField = "id")
    var categoryId: Long? = null

    // ── DETAILS tab ────────────────────────────────────────

    @Column(columnDefinition = "TEXT")
    @PortalField(
        label = "Opis",
        tab = "DETAILS",
        order = 1,
        renderer = RendererType.TEXTAREA,
        filterType = FilterType.NONE,
        showInTable = false
    )
    var description: String = ""

    @Column
    @PortalField(
        label = "Waga (kg)",
        tab = "DETAILS",
        order = 2,
        renderer = RendererType.DECIMAL,
        filterType = FilterType.RANGE,
        showInTable = false
    )
    var weight: Double = 0.0

    @Column
    @PortalField(
        label = "Tagi",
        tab = "DETAILS",
        order = 3,
        renderer = RendererType.MULTI_SELECT,
        filterType = FilterType.IN,
        showInTable = false,
        tooltip = "Wartości oddzielone przecinkiem, np. ELEKTRONIKA,PREMIUM",
        selectEnum = _root_ide_package_.dev.acme.portal.demo.ProductTag::class
    )
    var tags: String = ""

    @Column
    @PortalField(
        label = "Kraj pochodzenia",
        tab = "DETAILS",
        order = 4,
        renderer = RendererType.RELATION,
        filterType = FilterType.EXACT,
        showInTable = false
    )
    @PortalRelation(targetEntity = _root_ide_package_.dev.acme.portal.demo.DemoCountry::class, editable = true, displayFields = ["name", "code"], searchFields = ["name"])
    @PortalLookup(labelField = "name", valueField = "id")
    var countryId: Long? = null

    @Column
    @PortalField(
        label = "Dostawca",
        tab = "DETAILS",
        order = 5,
        renderer = RendererType.RELATION,
        filterType = FilterType.EXACT,
        showInTable = false
    )
    @PortalRelation(targetEntity = _root_ide_package_.dev.acme.portal.demo.DemoSupplier::class, editable = true, displayFields = ["name"], searchFields = ["name"])
    @PortalLookup(labelField = "name", valueField = "id")
    var supplierId: Long? = null

    // ── MEDIA tab ──────────────────────────────────────────

    @Column
    @PortalField(
        label = "Zdjęcie produktu",
        tab = "MEDIA",
        order = 1,
        renderer = RendererType.FILE,
        filterType = FilterType.NONE,
        showInTable = false,
        showInFilter = false
    )
    var imageUrl: String = ""

    @Column
    @PortalField(
        label = "Strona produktu",
        tab = "MEDIA",
        order = 2,
        renderer = RendererType.URL,
        filterType = FilterType.NONE,
        showInTable = false
    )
    var productUrl: String = ""

    @Column(length = 7)
    @PortalField(
        label = "Kolor wiodący",
        tab = "MEDIA",
        order = 3,
        renderer = RendererType.COLOR,
        filterType = FilterType.NONE,
        showInTable = false
    )
    var color: String = ""

    @Column(columnDefinition = "TEXT")
    @PortalField(
        label = "Atrybuty (JSON)",
        tab = "MEDIA",
        order = 4,
        renderer = RendererType.JSON,
        filterType = FilterType.NONE,
        showInTable = false,
        showInFilter = false
    )
    var metadata: String = ""
}

// ─────────────────────────────────────────────────────────────
//  DemoSupplier  –  flat entity
//  Covers: TEXT, EMAIL, URL, NUMBER, BOOLEAN, TEXTAREA
//  Filters: CONTAINS, EXACT, RANGE, BOOLEAN, NONE
// ─────────────────────────────────────────────────────────────

@Entity
@Table(
    indexes = [
        jakarta.persistence.Index(name = "idx_demo_supplier_is_verified", columnList = "is_verified"),
        jakarta.persistence.Index(name = "idx_demo_supplier_rating",      columnList = "rating")
    ]
)
@PortalEntity(
    label = "Dostawca",
    module = "Katalog",
    icon = "truck",
    order = 2,
    description = "Dostawcy produktów"
)
class DemoSupplier {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PortalField(label = "ID", order = 0, readonly = true, showInFilter = false)
    var id: Long = 0

    @Column(nullable = false)
    @PortalField(label = "Nazwa", order = 1, required = true, filterType = FilterType.CONTAINS)
    var name: String = ""

    @Column
    @PortalField(
        label = "E-mail",
        order = 2,
        renderer = RendererType.EMAIL,
        filterType = FilterType.EXACT
    )
    var email: String = ""

    @Column
    @PortalField(
        label = "Strona WWW",
        order = 3,
        renderer = RendererType.URL,
        filterType = FilterType.NONE,
        showInTable = false
    )
    var website: String = ""

    @Column
    @PortalField(
        label = "Ocena (1–5)",
        order = 4,
        renderer = RendererType.NUMBER,
        filterType = FilterType.RANGE
    )
    var rating: Int = 0

    @Column
    @PortalField(
        label = "Zweryfikowany",
        order = 5,
        renderer = RendererType.BOOLEAN,
        filterType = FilterType.BOOLEAN
    )
    var isVerified: Boolean = false

    @Column(columnDefinition = "TEXT")
    @PortalField(
        label = "Notatki",
        order = 6,
        renderer = RendererType.TEXTAREA,
        filterType = FilterType.NONE,
        showInTable = false
    )
    var notes: String = ""

    /** JPA optimistic locking — see DemoCustomer.version for details. */
    @Column(columnDefinition = "bigint not null default 0")
    @jakarta.persistence.Version
    var version: Long = 0
}
