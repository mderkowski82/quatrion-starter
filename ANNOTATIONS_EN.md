# Quatrion Portal Annotation Reference — English Edition

> **Package:** `dev.quatrion.portal.annotation`
> **Applies to:** Quarkus backend (Kotlin), Quatrion Portal framework
> **Reference file:** `backend/quatrion-portal-demo/src/.../demo/DemoEntities.kt`

---

## Table of Contents

1. [Architecture — how annotations work](#1-architecture--how-annotations-work)
2. [@PortalEntity — entity registration](#2-portalentity--entity-registration)
3. [PortalTab — form tabs](#3-portaltab--form-tabs)
4. [@PortalField — UI fields](#4-portalfield--ui-fields)
   - [RendererType — renderer types](#renderertype--renderer-types)
   - [FilterType — filter strategies](#filtertype--filter-strategies)
5. [@Regex — pattern validation](#5-regex--pattern-validation)
6. [@PortalRelation — relations](#6-portalrelation--relations)
7. [@PortalDependency — conditional rules](#7-portaldependency--conditional-rules)
8. [@PortalAction + @PortalFormField — custom actions](#8-portalaction--portalformfield--custom-actions)
9. [@PortalSecurity — access control](#9-portalsecurity--access-control)
10. [Registering entities in PortalModuleConfig](#10-registering-entities-in-portalmoduleconfig)
11. [Complete example — Customer entity](#11-complete-example--customer-entity)
12. [Common patterns and FAQ](#12-common-patterns-and-faq)
13. [RowColor — row coloring](#13-rowcolor--row-coloring)
14. [portal.ui configuration — application.properties](#14-portalui-configuration--applicationproperties)
15. [Full REST API endpoint reference](#15-full-rest-api-endpoint-reference)
16. [Annotation quick reference](#16-annotation-quick-reference)

---

## 1. Architecture — how annotations work

```
JPA class + Portal annotations
        │
        ▼
  MetadataService (startup)
        │  reads annotations via reflection
        ▼
  JSON → /api/portal/metadata
        │
        ▼
  Frontend (Next.js)
        │  dynamically generates: tables, forms, filters, actions
        ▼
  Full CRUD interface — zero hand-written React components
```

**How it works:**

1. Every JPA entity annotated with `@PortalEntity` is registered in `PortalModuleConfig`.
2. At backend startup, `MetadataService` scans all registered classes and builds a `PortalMetadata` JSON object.
3. The frontend fetches the JSON from `/api/portal/metadata` and dynamically renders the entire UI.
4. No per-entity React components are needed.

---

## 2. `@PortalEntity` — entity registration

**Class-level** annotation — registers a JPA entity with the portal and configures its appearance in the sidebar navigation.

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PortalEntity(
    val label: String,
    val labelKey: String = "",          // i18n key, e.g. "entity.customer"
    val module: String,
    val group: String = "",
    val groupKey: String = "",          // i18n key for sidebar group heading
    val icon: String = "table",
    val order: Int = 0,
    val description: String = "",
    val descriptionKey: String = "",    // i18n key for description
    val tabs: KClass<out PortalTab> = NoTabs::class,
    val allowCreate: Boolean = true,
    val allowDelete: Boolean = true,
    val allowEdit: Boolean = true,
    val pageSize: Int = 25,
    val softDelete: Boolean = false,
    val auditLog: Boolean = false
)
```

> **`NoTabs`** — a framework-internal sentinel enum (package `dev.quatrion.portal.annotation`).
> Represents a flat single-page form with no tab navigation. No manual import required.

### Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `label` | `String` | — | Human-readable entity name shown in the sidebar, page headers, and breadcrumbs |
| `labelKey` | `String` | `""` | i18n key for `label`, e.g. `"entity.customer"`. When non-empty, replaces `label` in the UI |
| `module` | `String` | — | Module name (must match `ModuleDef.name` in the configuration) |
| `group` | `String` | `""` | Optional sidebar group name — entities with the same group are collapsed under a heading |
| `groupKey` | `String` | `""` | i18n key for the sidebar group heading, e.g. `"group.catalog"` |
| `icon` | `String` | `"table"` | Lucide icon name used in the sidebar and entity page header (e.g. `"users"`, `"package"`) |
| `order` | `Int` | `0` | Numeric sort position within the module / group — lower values appear first |
| `description` | `String` | `""` | Optional longer description shown as a subtitle or tooltip |
| `descriptionKey` | `String` | `""` | i18n key for `description`, e.g. `"entity.customer.description"` |
| `tabs` | `KClass<out PortalTab>` | `NoTabs::class` | Enum implementing `PortalTab` that defines the form tabs for this entity |
| `allowCreate` | `Boolean` | `true` | Whether the portal shows a "Create" button for this entity |
| `allowDelete` | `Boolean` | `true` | Whether the portal shows a "Delete" action for this entity |
| `allowEdit` | `Boolean` | `true` | Whether the portal shows an "Edit" button / inline edit |
| `pageSize` | `Int` | `25` | Default number of rows shown per page in the entity list table |
| `softDelete` | `Boolean` | `false` | When `true`, delete operations set `deleted = true` instead of physically removing the row. The entity class **must** have a `deleted: Boolean = false` field |
| `auditLog` | `Boolean` | `false` | When `true`, CRUD operations are recorded in the audit log |

### Examples

**Simple entity without tabs:**
```kotlin
@Entity
@Table(name = "country")
@PortalEntity(
    label = "Country",
    module = "CRM",
    icon = "globe",
    order = 1,
    description = "Country dictionary"
)
class Country { ... }
```

**Entity with tabs, group, and pagination:**
```kotlin
@Entity
@Table(name = "customer")
@PortalEntity(
    label = "Customer",
    module = "CRM",
    group = "Sales",
    icon = "users",
    order = 1,
    tabs = CustomerTab::class,
    pageSize = 50
)
class Customer { ... }
```

**Entity with soft-delete and audit log:**
```kotlin
@Entity
@Table(name = "invoice")
@PortalEntity(
    label = "Invoice",
    module = "Finance",
    icon = "file-text",
    softDelete = true,
    auditLog = true
)
class Invoice {
    // ...
    var deleted: Boolean = false  // REQUIRED when softDelete = true
}
```

**Read-only entity (no create or delete):**
```kotlin
@Entity
@Table(name = "audit_log")
@PortalEntity(
    label = "Audit Log",
    module = "System",
    icon = "list",
    allowCreate = false,
    allowEdit = false,
    allowDelete = false
)
class AuditLog { ... }
```

---

## 3. `PortalTab` — form tabs

`PortalTab` is an interface that must be implemented by an `enum class`. Each enum constant represents one tab in the form. Tabs are registered via the `tabs` parameter of `@PortalEntity`.

```kotlin
interface PortalTab {
    val label: String
    val labelKey: String get() = ""     // i18n key for the tab label
    val icon: String get() = ""
    val order: Int get() = 0
}
```

### How to define tabs

```kotlin
enum class CustomerTab(
    override val label: String,
    override val icon: String,
    override val order: Int
) : PortalTab {
    BASIC("Basic Info",   "user",         0),
    CONTACT("Contact",    "phone",         1),
    FINANCIAL("Financial","dollar-sign",   2),
    SYSTEM("System",      "settings",      3)
}
```

### How to assign fields to tabs

In `@PortalField`, set the `tab` parameter to the **enum constant name** (in uppercase):

```kotlin
@PortalField(label = "Full Name", tab = "BASIC", order = 1, required = true)
var name: String = ""

@PortalField(label = "Email", tab = "CONTACT", order = 1, renderer = RendererType.EMAIL)
var email: String = ""
```

### Entity without tabs

The default value `tabs = NoTabs::class` means a flat, single-page form. Fields without a `tab` parameter are rendered directly.

---

## 4. `@PortalField` — UI fields

**Field or function-level** annotation — declares an entity property as a UI field visible in the table, form, or filter panel.

```kotlin
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PortalField(
    val label: String,
    val labelKey: String = "",          // i18n key, e.g. "field.customer.name"
    val tab: String = "",
    val renderer: RendererType = RendererType.AUTO,
    val order: Int = 0,
    val readonly: Boolean = false,
    val hidden: Boolean = false,
    val showInTable: Boolean = true,
    val showInFilter: Boolean = true,
    val required: Boolean = false,
    val placeholder: String = "",
    val tooltip: String = "",
    val tooltipKey: String = "",        // i18n key for tooltip
    val width: Int = 0,
    val group: String = "",
    val displayExpression: String = "",
    val filterType: FilterType = FilterType.AUTO,
    val selectOptions: Array<String> = [],
    val selectEnum: KClass<*> = Unit::class,
    val min: Double = Double.NaN,
    val max: Double = Double.NaN,
    val defaultValue: String = ""
)
```

> **`selectEnum` — how option values are determined:**
> When set, SELECT/MULTI_SELECT options are built by calling `.toString()` on each enum constant.
> If the enum does **not** override `toString()`, the constant's name is used (e.g. `"VIP"`).
> If the enum overrides `toString()` (e.g. `override fun toString() = label`), that value is used.
> **The `value` string in `@PortalDependency` must match the same `toString()` result.**

### Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `label` | `String` | — | Column / form field label shown in the portal UI |
| `labelKey` | `String` | `""` | i18n key for `label`, e.g. `"field.customer.name"` |
| `tab` | `String` | `""` | Name of the `PortalTab` enum constant (e.g. `"BASIC"`) |
| `renderer` | `RendererType` | `AUTO` | UI component type for rendering the field value |
| `order` | `Int` | `0` | Sort order within the same tab / group — lower values appear first |
| `readonly` | `Boolean` | `false` | Field is always shown as read-only, even in edit mode |
| `hidden` | `Boolean` | `false` | Field is excluded from both the table and form (for internal/system fields) |
| `showInTable` | `Boolean` | `true` | Whether the field appears as a column in the entity list table |
| `showInFilter` | `Boolean` | `true` | Whether the field appears in the filter panel |
| `required` | `Boolean` | `false` | Validation: field must be non-empty before saving |
| `placeholder` | `String` | `""` | Placeholder text shown inside empty input fields |
| `tooltip` | `String` | `""` | Short help text displayed near the input field |
| `tooltipKey` | `String` | `""` | i18n key for `tooltip`, e.g. `"tooltip.customer.email"` |
| `width` | `Int` | `0` | Preferred column width in pixels for the table view (`0` = auto) |
| `group` | `String` | `""` | Groups related fields visually within a tab |
| `displayExpression` | `String` | `""` | Template expression `${fieldName}` for computing display values |
| `filterType` | `FilterType` | `AUTO` | Filtering strategy applied when the user enters a filter value |
| `selectOptions` | `Array<String>` | `[]` | Explicit list of options for `SELECT`/`MULTI_SELECT` (when `selectEnum` is not set) |
| `selectEnum` | `KClass<*>` | `Unit::class` | Enum class whose constants define options — value is each constant's `toString()` |
| `min` | `Double` | `NaN` | Minimum numeric value for `NUMBER`/`DECIMAL` fields |
| `max` | `Double` | `NaN` | Maximum numeric value for `NUMBER`/`DECIMAL` fields |
| `defaultValue` | `String` | `""` | Default value when creating a new record |

### `RendererType` — renderer types

| Value | Description | Notes |
|---|---|---|
| `AUTO` | Framework infers renderer from JPA/Kotlin type | Default value |
| `TEXT` | Single-line text input | — |
| `TEXTAREA` | Multi-line text area | Set `@Column(columnDefinition = "TEXT")` |
| `NUMBER` | Integer numeric input | Kotlin types: `Int`, `Long` |
| `DECIMAL` | Floating-point numeric input | Kotlin types: `Double`, `BigDecimal` |
| `DATE` | Date picker (ISO-8601 `YYYY-MM-DD`) | — |
| `DATETIME` | Date and time picker (ISO-8601 `YYYY-MM-DDTHH:mm`) | — |
| `BOOLEAN` | Checkbox / toggle | Kotlin type: `Boolean` |
| `SELECT` | Single-value dropdown | Requires `selectOptions` or `selectEnum` |
| `MULTI_SELECT` | Multi-value dropdown | Stored as comma-separated values in the database |
| `RELATION` | ManyToOne / OneToOne lookup picker | Requires a JPA association + `@PortalRelation` |
| `RELATION_LIST` | OneToMany inline list | Requires `@OneToMany(mappedBy = ...)` + `@PortalRelation` |
| `PASSWORD` | Password input (value masked) | Does not appear in the table |
| `EMAIL` | Email input with format validation | — |
| `URL` | URL input with format validation | — |
| `COLOR` | Color picker (hex string storage, e.g. `#FF5733`) | Column length: `length = 7` |
| `FILE` | File / image upload | Stores path or base64 |
| `JSON` | Raw JSON editor | Set `@Column(columnDefinition = "TEXT")` |
| `CUSTOM` | Custom renderer registered in the frontend | — |

### `FilterType` — filter strategies

| Value | Description | Example SQL |
|---|---|---|
| `AUTO` | Strategy inferred from field type | — |
| `EXACT` | Equality match | `field = :value` |
| `CONTAINS` | Case-insensitive substring search | `LOWER(field) LIKE %value%` |
| `STARTS_WITH` | Prefix search | `LOWER(field) LIKE value%` |
| `RANGE` | Numeric or date range | `field BETWEEN :from AND :to` |
| `IN` | Value-set membership | `field IN (:values)` |
| `BOOLEAN` | Boolean equality | `field = true/false` |
| `NONE` | Field is not filterable | — |

### Field examples

**Text field with validation:**
```kotlin
@Column(length = 100, nullable = false)
@PortalField(
    label = "Full Name",
    tab = "BASIC",
    order = 1,
    required = true,
    renderer = RendererType.TEXT,
    filterType = FilterType.CONTAINS,
    placeholder = "Enter full name"
)
var name: String = ""
```

**SELECT field with enum:**
```kotlin
enum class Status { ACTIVE, INACTIVE, PENDING }

@Column(length = 20)
@Enumerated(EnumType.STRING)
@PortalField(
    label = "Status",
    order = 2,
    renderer = RendererType.SELECT,
    filterType = FilterType.IN,
    selectEnum = Status::class
)
var status: Status? = null
```

**SELECT field with explicit options (no enum):**
```kotlin
@Column(length = 20)
@PortalField(
    label = "Priority",
    order = 3,
    renderer = RendererType.SELECT,
    filterType = FilterType.IN,
    selectOptions = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]
)
var priority: String = ""
```

**MULTI_SELECT field:**
```kotlin
enum class Tag { VIP, NEW, PREMIUM, BUSINESS }

@Column
@PortalField(
    label = "Tags",
    order = 4,
    renderer = RendererType.MULTI_SELECT,
    filterType = FilterType.IN,
    showInTable = false,
    tooltip = "Comma-separated values",
    selectEnum = Tag::class
)
var tags: String = ""
```

**DECIMAL field with range constraint:**
```kotlin
@Column
@PortalField(
    label = "Price",
    order = 5,
    renderer = RendererType.DECIMAL,
    filterType = FilterType.RANGE,
    min = 0.0,
    max = 99999.99,
    placeholder = "0.00"
)
var price: Double = 0.0
```

**DATE field:**
```kotlin
@Column
@PortalField(
    label = "Date of Birth",
    order = 6,
    renderer = RendererType.DATE,
    filterType = FilterType.RANGE,
    showInTable = false,
    tooltip = "Format: YYYY-MM-DD"
)
var birthDate: String = ""
```

**Read-only field (ID):**
```kotlin
@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
@PortalField(label = "ID", order = 0, readonly = true, showInFilter = false)
var id: Long = 0
```

**Hidden field:**
```kotlin
@Column
@PortalField(label = "Internal Token", hidden = true)
var internalToken: String = ""
```

**Field with displayExpression:**
```kotlin
@PortalField(
    label = "Full Name",
    order = 7,
    displayExpression = "\${firstName} \${lastName}",
    showInTable = true,
    readonly = true
)
var fullName: String = ""
```

**Field with default value:**
```kotlin
@Column
@PortalField(
    label = "Active",
    order = 8,
    renderer = RendererType.BOOLEAN,
    defaultValue = "true"
)
var isActive: Boolean = true
```

---

## 5. `@Regex` — pattern validation

**Field-level** annotation — attaches a regular expression that is propagated to the frontend as client-side validation.

```kotlin
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Regex(
    val pattern: String,
    val message: String = "The value does not match the required format"
)
```

### Parameters

| Parameter | Type | Description |
|---|---|---|
| `pattern` | `String` | Regular expression that the field value must match |
| `message` | `String` | Error message displayed when the value does not match the pattern |

### Examples

**Phone number:**
```kotlin
@Column(length = 20)
@Regex(
    pattern = """^\+?[\d\s\-]{7,20}$""",
    message = "Phone number may contain digits, spaces, hyphens, and an optional leading +"
)
@PortalField(
    label = "Phone",
    order = 2,
    renderer = RendererType.TEXT,
    placeholder = "+1 555 123 4567"
)
var phone: String = ""
```

**Country ISO code:**
```kotlin
@Column(length = 3)
@Regex(
    pattern = """^[A-Za-z]{2,3}$""",
    message = "ISO code must contain 2 or 3 letters"
)
@PortalField(label = "ISO Code", order = 2, required = true, placeholder = "e.g. US")
var isoCode: String = ""
```

**VAT number:**
```kotlin
@Column(length = 20)
@Regex(
    pattern = """^[A-Z]{2}\d{8,12}$""",
    message = "VAT number must start with 2 letters followed by 8–12 digits"
)
@PortalField(label = "VAT Number", order = 3, required = true, renderer = RendererType.TEXT)
var vatNumber: String = ""
```

> **Note:** `@Regex` works only as client-side (frontend) validation. It does not replace backend validation — add that separately (e.g. using Bean Validation `@Pattern`).

---

## 6. `@PortalRelation` — relations

Every panel relation exists as a **JPA association** — the single source of truth (R1). `@PortalRelation` declares **presentation only** (plus a consistent target override and an enforced delete marker); it never creates a relation by itself. Placing it on a raw numeric key or a `@Transient` list without an association is illegal and fails the build.

---

### How it works — data flow

```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "country_id")
@PortalField(renderer = RendererType.RELATION, ...)   ← tells frontend "render a picker"
@PortalRelation(                                       ← presentation: labels, picker filters
    displayFields = ["name", "code"],
    searchFields = ["name", "code"],
    labelField = "name"
)
var country: DemoCountry? = null                      ← JPA association = truth
         │
         ▼ MetadataService (startup)
         │
  RelationMetadata {
    targetEntity  = "DemoCountry"     ← derived from the association type
    labelField    = "name"
    valueField    = "id"
    displayFields = ["name", "code"]
    searchFields  = ["name", "code"]
    filterQuery   = ""
    dependsOn     = ""
    ...
  }
         │
         ▼ JSON → /api/portal/metadata → frontend
         │
  Table:  RelationCell  →  GET /api/portal/data/DemoCountry/{id}
                            displays the value of the "name" field of the selected record
         │
  Form:   RelationRenderer  →  GET /api/portal/data/DemoCountry/lookup?q=pol&labelField=name&valueField=id
                                autocomplete dropdown with search results
```

On the wire the association travels as the target id scalar (`"country": 42`); binding by id never exposes session mechanics (R6). The form binds a relation choice through the target identifier.

---

### Two rendering modes

| Renderer | When to use | Field in entity |
|---|---|---|
| `RendererType.RELATION` | ManyToOne, OneToOne — a **single** related record (owning side, holds the FK) | `@ManyToOne var country: DemoCountry? = null` + `@JoinColumn` |
| `RendererType.RELATION_LIST` | OneToMany — list of related entities (inverse side, derived from the owning mapping) | `@OneToMany(mappedBy = "customer") var orders: List<DemoOrder>? = null` |

> **Important for `RELATION_LIST`:** The field must be a `@OneToMany` collection with `mappedBy` pointing at the owning association — never `@Transient`. The child list is derived from the inverse side, not from a manual parent key. Many-to-many is modelled only through an explicit link entity with two mandatory `@ManyToOne` associations (no implicit `@JoinTable`).

---

### `@PortalRelation` — detailed parameter reference

```kotlin
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class PortalRelation(
    val targetEntity: KClass<*> = Unit::class,
    val editable: Boolean = true,
    val inlineEdit: Boolean = false,
    val displayFields: Array<String> = [],
    val searchFields: Array<String> = [],
    val createAllowed: Boolean = false,
    val cascadeDelete: Boolean = false,
    val orderBy: String = "",
    val maxItems: Int = 0,
    val downloadAction: String = "",
    val actions: Array<RelationRowAction> = [],
    // --- picker presentation ---
    val labelField: String = "name",
    val valueField: String = "id",
    val filterQuery: String = "",
    val dependsOn: String = "",
    val maxResults: Int = 100,
    val parentField: String = ""
)
```

#### `targetEntity: KClass<*> = Unit::class`

Consistent override of the relation target **only**. The target and the relation type are derived from the JPA association; setting this to a concrete entity class is allowed solely when it matches the association type (a mismatch fails the build).

**When can it be omitted?** The framework derives the target:
- For collections (`List<T>`) — from the generic type argument `T`
- For to-one associations — from the field type itself

In practice, setting `targetEntity` explicitly documents intent; keeping it means keeping it consistent.

```kotlin
// ✅ Explicit target entity matching the association — recommended
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "country_id")
@PortalRelation(targetEntity = DemoCountry::class, ...)
var country: DemoCountry? = null

// ✅ Derived from the collection element type
@OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
@PortalRelation(displayFields = ["product", "quantity"])
var items: List<DemoOrderItem>? = null
```

---

#### `displayFields: Array<String> = []`

Target entity fields shown as **columns in the table** in `RELATION_LIST` mode, or as **additional info** in the `RELATION` picker.

```kotlin
// Picker shows "John Smith (j.smith@example.com)"
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "customer_id")
@PortalRelation(
    targetEntity = DemoCustomer::class,
    displayFields = ["name", "email"],   // both shown as columns in the list
    searchFields = ["name", "email"]
)
var customer: DemoCustomer? = null
```

When `displayFields = []` (default), the frontend selects visible columns based on `showInTable` from the target entity's metadata.

---

#### `searchFields: Array<String> = []`

Target entity fields searched when the user **types text** in the picker. The backend executes:
```sql
LOWER(CAST(e.{searchField} AS string)) LIKE %phrase%
```

Provide the fields that make sense for searching (typically `name`, `code`, `email`). Does not affect table columns — that is controlled by `displayFields`.

```kotlin
@PortalRelation(
    targetEntity = DemoProduct::class,
    displayFields = ["name", "sku"],    // visible columns
    searchFields  = ["name", "sku"]     // fields searched when user types
)
```

---

#### `editable: Boolean = true`

When `false`, the picker is locked (read-only in the form). Useful e.g. for the `order` field on an order item — the parent order should not be changed from within the child.

```kotlin
// Order field — read-only (parent reference)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "order_id")
@PortalRelation(
    targetEntity = DemoOrder::class,
    editable = false,            // picker is locked
    displayFields = ["orderNumber"],
    searchFields = ["orderNumber"]
)
var order: DemoOrder? = null
```

---

#### `inlineEdit: Boolean = false`

`RELATION_LIST` only. When `true`, related records can be edited directly in the embedded table inside the parent form, without opening a separate modal.

```kotlin
@OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
@PortalRelation(
    targetEntity = DemoOrderItem::class,
    editable = true,
    inlineEdit = true,           // edit directly in the items table
    displayFields = ["product", "quantity", "unitPrice"],
    maxItems = 100
)
var items: List<DemoOrderItem>? = null
```

---

#### `createAllowed: Boolean = false`

When `true`, the picker shows a **"Create new"** option. The user can open the target entity's create form directly from within the picker, without navigating away.

```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "supplier_id")
@PortalRelation(
    targetEntity = DemoSupplier::class,
    displayFields = ["name"],
    searchFields = ["name"],
    createAllowed = true         // "Add new supplier" in the picker
)
var supplier: DemoSupplier? = null
```

---

#### `cascadeDelete: Boolean = false`

Enforced delete marker, not an informational hint. The effective delete semantics are derived from the domain association: without a cascade declaration, deleting a parent with related rows is **blocked** (`409 Conflict`); set this to `true` only to declare cascade intent, which must be backed by the JPA mapping (`cascade = REMOVE`/`ALL` or orphan removal) and database constraints. A mismatch between this marker and the JPA mapping fails the build, as does a self-cascade.

```kotlin
// Member with cascading loans (JPA cascade REQUIRED alongside the marker)
@OneToMany(mappedBy = "member", fetch = FetchType.LAZY, cascade = [CascadeType.REMOVE])
@PortalRelation(
    targetEntity = Loan::class,
    cascadeDelete = true,        // backed by cascade = REMOVE above
    ...
)
var loans: List<Loan>? = null
```

---

#### `orderBy: String = ""`

HQL `ORDER BY` fragment (without the `ORDER BY` keyword) applied when loading the relation list. The entity alias is `e`.

```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "category_id")
@PortalRelation(
    targetEntity = DemoCategory::class,
    displayFields = ["name"],
    orderBy = "name ASC"         // category list sorted alphabetically
)
var category: DemoCategory? = null
```

When empty, the backend sorts by `labelField` ascending.

---

#### `maxItems: Int = 0`

Maximum number of items in a `RELATION_LIST`. When `0` (default) — no limit. The frontend displays a warning when the limit is reached.

---

### Picker presentation — `labelField`, `valueField`, `filterQuery`, `dependsOn`, `maxResults`, `parentField`

The remaining `@PortalRelation` parameters describe how the `/lookup` endpoint is called. The deprecated standalone fallback annotation was removed — every lookup attribute must be declared on `@PortalRelation`.

#### `labelField: String = "name"`

The target entity field displayed as the **human-readable label** in the picker and in the table cell.

- In the table: `RelationCell` fetches the record via `GET /api/portal/data/{targetEntity}/{id}` and displays `record[labelField]`
- In the form picker: the label of each option in the dropdown

```kotlin
// Table shows the value of the "name" field, e.g. "Poland"
@PortalRelation(labelField = "name", valueField = "id")

// Table shows the "orderNumber" value, e.g. "ORD-2024-001"
@PortalRelation(labelField = "orderNumber", valueField = "id")
```

---

#### `valueField: String = "id"`

The target entity field whose **value is stored** when a relation item is selected (typically the primary key). Defaults to `"id"` — rarely needs to be changed unless the relation is keyed by a unique field other than the primary key.

```kotlin
// Lookup stores the ISO "code" value instead of numeric "id"
@PortalRelation(labelField = "name", valueField = "code")
var country: DemoCountry? = null
```

---

#### `filterQuery: String = ""`

An additional HQL `WHERE` fragment that **permanently narrows** lookup results. The entity alias is **`e`** (without the `WHERE` keyword). Applied regardless of the user's search phrase.

```
Internal HQL query:
  FROM Country e
  WHERE LOWER(CAST(e.name AS string)) LIKE :q   ← from user input
  AND e.isActive = true                          ← from filterQuery
  ORDER BY e.name
```

```kotlin
// Only active categories
@PortalRelation(filterQuery = "e.isActive = true")

// Only European countries
@PortalRelation(filterQuery = "e.continent = 'EUROPE'")

// Only products in stock
@PortalRelation(filterQuery = "e.quantity > 0")

// Multiple conditions (AND)
@PortalRelation(filterQuery = "e.isActive = true AND e.isVerified = true")
```

> **Important:** The entity alias in `filterQuery` must always be `e`. Do not add the `WHERE` keyword. Picker-scoping only: `filterQuery` narrows the picker, never the write path — rules that must hold on bind become named domain invariants instead. Every referenced `e.field` must exist on the target entity (validated at build time).

---

#### `dependsOn: String = ""`

The name of **another field on the same form** whose current value is automatically passed as a filter to the `/lookup` endpoint. Enables **cascading dropdowns** — e.g. selecting a genre restricts the available books.

**How it works technically:**

1. User selects a value in the `genre` helper field on the Loan form (e.g. `7`)
2. Frontend re-calls `/api/portal/data/Book/lookup?dependsOnField=genre&dependsOnValue=7`
3. Backend adds to HQL: `AND e.genre.id = :depVal` (association-aware: `.id` is appended for association fields)
4. Only books assigned to the genre with `id = 7` appear in the dropdown

```kotlin
// Loan form: helper picker (value is NOT persisted)
@Transient
@PortalField(label = "Filter by genre (helper)", order = 2, renderer = RendererType.RELATION, ...)
@PortalRelation(targetEntity = Genre::class, searchFields = ["name"])
var genre: Long? = null          // same name as the Book association below

// Loan form: book picker filtered by the selected genre
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "book_id", nullable = false)
@PortalField(label = "Book", order = 3, renderer = RendererType.RELATION)
@PortalRelation(
    targetEntity = Book::class,
    searchFields = ["title", "isbn"],
    dependsOn = "genre"          // form field on THIS form AND association on Book
)
var book: Book? = null
```

> **Note:** `dependsOn` names a field on the **current form** (the helper above) that must share its name with the **association on the target entity** (`Book.genre`). The backend filters `e.{dependsOn}.id = :depVal` for associations, `e.{dependsOn} = :depVal` otherwise. A helper `@Transient` field carrying `@PortalRelation` is legal only as such a `dependsOn` source. `dependsOn` must reference an existing field of the current entity (validated at build time).

---

#### `maxResults: Int = 100`

Maximum number of options returned from the `/lookup` endpoint per request. Decrease for very large tables, increase when users need a wider selection without typing.

```kotlin
// Small dictionary table — show all options immediately
@PortalRelation(labelField = "name", valueField = "id", maxResults = 500)

// Large customer table — limit autocomplete suggestions
@PortalRelation(labelField = "name", valueField = "id", maxResults = 20)
```

---

#### `parentField: String = ""`

For `RELATION_LIST` fields only: name of the field in the **target** entity that holds the owning-side association back to the parent entity (e.g. `"member"` on `Loan` when the list is placed on `Member`). When set, the frontend auto-fetches related records by querying `GET /api/portal/data/{targetEntity}?filter[parentField][eq]={parentId}` instead of relying on the parent entity's `getById` response to include the list. Empty string means no auto-fetch.

```kotlin
// Member form: loan history auto-fetched via Loan.member
@OneToMany(mappedBy = "member", fetch = FetchType.LAZY)
@PortalRelation(
    targetEntity = Loan::class,
    displayFields = ["bookTitle", "loanDate", "status"],
    parentField = "member"     // owning association on Loan
)
var loans: List<Loan>? = null
```

---

### The `/lookup` endpoint — how the frontend calls it

```
GET /api/portal/data/{targetEntity}/lookup
  ?q={search_phrase}
  &labelField={labelField}
  &valueField={valueField}
  &filterQuery={filterQuery}
  &dependsOnField={dependsOn}
  &dependsOnValue={value_of_dependent_field}
  &orderBy={orderBy}
  &max={maxResults}
```

Returns a list of `LookupOption`:
```json
[
  { "value": 1, "label": "Poland" },
  { "value": 2, "label": "Germany" },
  { "value": 3, "label": "France" }
]
```

---

### Parameter reference — summary tables

#### `@PortalRelation`

| Parameter | Type | Default | Description |
|---|---|---|---|
| `targetEntity` | `KClass<*>` | `Unit::class` | Target JPA entity class. Explicit declaration eliminates ambiguity |
| `editable` | `Boolean` | `true` | Whether the relation field can be modified in the form |
| `inlineEdit` | `Boolean` | `false` | `RELATION_LIST` only: edit items directly in the embedded table |
| `displayFields` | `Array<String>` | `[]` | Columns shown in `RELATION_LIST` table or additional info in the picker |
| `searchFields` | `Array<String>` | `[]` | Fields searched when the user types text in the picker |
| `createAllowed` | `Boolean` | `false` | Picker shows "Create new" option |
| `cascadeDelete` | `Boolean` | `false` | Enforced delete marker: `true` declares cascade intent backed by the JPA mapping and DB constraints (mismatch fails the build); `false` (default) blocks deletion when related rows exist |
| `orderBy` | `String` | `""` | HQL `ORDER BY` fragment (without keyword), alias `e` |
| `maxItems` | `Int` | `0` | Item limit in `RELATION_LIST` (0 = unlimited) |
| `downloadAction` | `String` | `""` | Name of a `@PortalAction` on the target entity that triggers a file download. When non-empty, a download icon button is rendered for each row in the `RELATION_LIST` |
| `actions` | `Array<RelationRowAction>` | `[]` | Per-row action buttons in the `RELATION_LIST` table (see `RelationRowAction`) |
| `labelField` | `String` | `"name"` | Target entity field shown as label in picker and table cell |
| `valueField` | `String` | `"id"` | Target entity field stored as value when an item is selected |
| `filterQuery` | `String` | `""` | Permanent HQL WHERE filter (alias `e.`), e.g. `"e.isActive = true"` — picker-scoping only |
| `dependsOn` | `String` | `""` | Name of another form field — enables cascading dropdown (must also name the target association) |
| `maxResults` | `Int` | `100` | Max options returned by `/lookup` per request |
| `parentField` | `String` | `""` | `RELATION_LIST` only: owning association field on the target entity back to the parent (e.g. `"member"` on `Loan`); enables frontend auto-fetch |

#### `RelationRowAction` — predefined per-row actions

```kotlin
enum class RelationRowAction(val actionName: String) {
    DOWNLOAD("download")  // calls the "download" action on the target entity
}
```

| Value | Action name | Description |
|---|---|---|
| `DOWNLOAD` | `"download"` | Calls `@PortalAction(name = "download")` on the target entity and triggers a browser file download |

**Example — file list with download button:**
```kotlin
@OneToMany(mappedBy = "taskRun", fetch = FetchType.LAZY)
@PortalField(label = "Files", renderer = RendererType.RELATION_LIST, showInFilter = false, showInTable = false)
@PortalRelation(
    targetEntity = TaskRunFile::class,
    editable = false,
    displayFields = ["fileName", "fileSizeBytes"],
    actions = [RelationRowAction.DOWNLOAD],   // download button on each row
    labelField = "fileName",
    valueField = "id",
    parentField = "taskRun"
)
var files: List<TaskRunFile>? = null
```

### Differences: `displayFields` vs `searchFields` vs `labelField`

| Property | What it does |
|---|---|
| `labelField` | Field shown as label in the table cell and dropdown option |
| `displayFields` | Columns shown in `RELATION_LIST` table / extra info alongside the label |
| `searchFields` | Fields used for text search when user types in the picker |

Typical pattern — all three can be different:
```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "customer_id")
@PortalRelation(
    targetEntity = DemoCustomer::class,
    displayFields = ["name", "email", "phone"],   // 3 columns in the relation list
    searchFields  = ["name", "email"],            // search by name and email
    labelField = "name",                          // table cell shows only the name
    valueField = "id"
)
var customer: DemoCustomer? = null
```

---

### Complete examples by scenario

**1. Simple ManyToOne relation (customer's country):**
```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "country_id")
@PortalField(
    label = "Country",
    tab = "CONTACT",
    order = 6,
    renderer = RendererType.RELATION,
    filterType = FilterType.EXACT,
    showInTable = false
)
@PortalRelation(
    targetEntity = DemoCountry::class,
    editable = true,
    displayFields = ["name", "code"],
    searchFields = ["name", "code"],
    labelField = "name",
    valueField = "id"
)
var country: DemoCountry? = null
```

**2. Read-only relation (order on an order item):**
```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "order_id")
@PortalField(
    label = "Order",
    order = 1,
    renderer = RendererType.RELATION,
    filterType = FilterType.EXACT
)
@PortalRelation(
    targetEntity = DemoOrder::class,
    editable = false,                              // picker locked
    displayFields = ["orderNumber"],
    searchFields = ["orderNumber"],
    labelField = "orderNumber",
    valueField = "id"
)
var order: DemoOrder? = null
```

**3. Relation with filter (active categories only):**
```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "category_id")
@PortalField(label = "Category", order = 3, renderer = RendererType.RELATION)
@PortalRelation(
    targetEntity = DemoCategory::class,
    displayFields = ["name"],
    searchFields = ["name"],
    labelField = "name",
    valueField = "id",
    filterQuery = "e.isActive = true"             // permanent HQL filter
)
var category: DemoCategory? = null
```

**4. Cascading dropdowns (genre → book):**
```kotlin
// Helper field on the Loan form (value NOT persisted)
@Transient
@PortalField(label = "Filter by genre (helper)", order = 2, renderer = RendererType.RELATION)
@PortalRelation(targetEntity = Genre::class, searchFields = ["name"])
var genre: Long? = null                          // same name as the Book association

// Dependent field — filtered by the genre value;
// backend filters: e.genre.id = 7
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "book_id", nullable = false)
@PortalField(label = "Book", order = 3, renderer = RendererType.RELATION)
@PortalRelation(
    targetEntity = Book::class,
    searchFields = ["title", "isbn"],
    dependsOn = "genre"
)
var book: Book? = null
```

**5. Read-only `RELATION_LIST` (customer's orders):**
```kotlin
@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
@PortalField(
    label = "Orders",
    tab = "SYSTEM",
    order = 6,
    renderer = RendererType.RELATION_LIST,
    filterType = FilterType.NONE,
    showInTable = false,
    showInFilter = false,
    tooltip = "Orders linked to this customer"
)
@PortalRelation(
    targetEntity = DemoOrder::class,
    editable = false,                              // read-only list
    displayFields = ["orderNumber", "orderDate", "totalAmount", "status"],
    searchFields = ["orderNumber"],
    labelField = "orderNumber",
    valueField = "id"
)
var orders: List<DemoOrder>? = null
```

**6. Inline-editable `RELATION_LIST` with limit (order items):**
```kotlin
@OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
@PortalField(
    label = "Order Items",
    tab = "ITEMS",
    order = 1,
    renderer = RendererType.RELATION_LIST,
    filterType = FilterType.NONE,
    showInTable = false,
    showInFilter = false
)
@PortalRelation(
    targetEntity = DemoOrderItem::class,
    editable = true,
    inlineEdit = true,                             // edit directly in the embedded table
    displayFields = ["product", "quantity", "unitPrice"],
    maxItems = 100,                                // max 100 items
    orderBy = "id ASC",
    labelField = "id",
    valueField = "id"
)
var items: List<DemoOrderItem>? = null
```

**7. Relation with on-the-fly record creation:**
```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "supplier_id")
@PortalField(label = "Supplier", order = 5, renderer = RendererType.RELATION)
@PortalRelation(
    targetEntity = DemoSupplier::class,
    displayFields = ["name"],
    searchFields = ["name"],
    createAllowed = true,                          // "Add new supplier" in the picker
    labelField = "name",
    valueField = "id"
)
var supplier: DemoSupplier? = null
```

**8. Relation with a non-ID key:**
```kotlin
// Lookup stores the ISO "code" instead of numeric id
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "country_id")
@PortalField(label = "Country (code)", order = 4, renderer = RendererType.RELATION)
@PortalRelation(
    targetEntity = DemoCountry::class,
    displayFields = ["name"],
    searchFields = ["name", "code"],
    labelField = "name",
    valueField = "code"                           // stores ISO code, not id
)
var country: DemoCountry? = null
```

**9. Self-referencing relation (parent category):**
```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parent_id")
@PortalField(
    label = "Parent Category",
    order = 5,
    renderer = RendererType.RELATION,
    filterType = FilterType.EXACT,
    showInTable = false
)
@PortalRelation(
    targetEntity = DemoCategory::class,           // same class!
    editable = true,
    displayFields = ["name"],
    searchFields = ["name"],
    labelField = "name",
    valueField = "id"
)
var parent: DemoCategory? = null
```

---

### Required annotation order on a field

```kotlin
@ManyToOne(...) / @OneToMany(...)   // 1. JPA association (source of truth)
@JoinColumn(...)                    // 1b. FK mapping (to-one owning side)
@PortalField(                       // 2. UI field declaration
    renderer = RendererType.RELATION,
    ...
)
@PortalRelation(                    // 3. Relation presentation + delete marker
    targetEntity = ...,
    ...
)
var country: DemoCountry? = null
```

---

### Common mistakes

| Mistake | Effect | Fix |
|---|---|---|
| `@PortalRelation` on a raw numeric key without an association | Build fails (legality table: raw key without JPA association) | Model a `@ManyToOne`/`@OneToOne` association; put the annotations on it |
| `RELATION_LIST` on `@Transient` instead of `@OneToMany` | Build fails (no inverse-side mapping) | Use `@OneToMany(mappedBy = "...")` pointing at the owning association |
| `RELATION_LIST` `@OneToMany` without `mappedBy` | Build fails (child list must derive from the inverse side) | Add `mappedBy` with the owning field name |
| `cascadeDelete = true` without JPA cascade | Build fails (marker must be backed by `cascade = REMOVE`/`ALL` or orphan removal) | Add the JPA cascade — or drop the marker and accept blocking (default) |
| `filterQuery` using an alias other than `e` | HQL runtime error | Always use `e.fieldName` |
| `filterQuery` referencing a non-existent target field | Build fails (unknown field in picker scope) | Reference an existing field of the target entity |
| `dependsOn` refers to a non-existent form field | Build fails | Double-check the exact field name (case-sensitive); it must also name the target association |
| `showInFilter = true` on `RELATION_LIST` | Relation lists cannot be filtered — nonsensical | Set `showInFilter = false` |
| `targetEntity` inconsistent with the association type | Build fails (override must match the domain model) | Fix the override or drop it and let the framework derive the target |

---

## 7. `@PortalDependency` — conditional rules

**Field-level** annotation (repeatable) — defines conditional rules controlling field visibility, available options, and numeric range based on the values of other form fields.

```kotlin
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class PortalDependency(
    val field: String = "",
    val operator: DependencyOperator = DependencyOperator.UNSPECIFIED,
    val value: String = "",
    val values: Array<String> = [],
    val condition: String = "",
    val visibility: DependencyVisibility = DependencyVisibility.NONE,
    val allowedValues: Array<String> = [],
    val min: String = "",
    val max: String = "",
    val message: String = "",
    val clearOnHide: Boolean = true
)
```

### Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `field` | `String` | `""` | Name of the field this rule depends on |
| `operator` | `DependencyOperator` | `UNSPECIFIED` | Comparison operator for a simple leaf condition |
| `value` | `String` | `""` | Value to compare against (single value) |
| `values` | `Array<String>` | `[]` | Set of values for `IN` / `NOT_IN` operators |
| `condition` | `String` | `""` | Complex condition as a JSON AST (for `allOf`/`anyOf`/`not` logic) |
| `visibility` | `DependencyVisibility` | `NONE` | Visibility effect: `SHOW`, `HIDE`, `NONE` |
| `allowedValues` | `Array<String>` | `[]` | When set, restricts available SELECT options to this list |
| `min` | `String` | `""` | Minimum numeric value; a literal (`"10"`) or field reference (`"$creditLimit"`) |
| `max` | `String` | `""` | Maximum numeric value; a literal or field reference |
| `message` | `String` | `""` | Message shown to the user when the rule is active |
| `clearOnHide` | `Boolean` | `true` | Whether to clear the field value when it becomes hidden |

### `DependencyVisibility`

| Value | Description |
|---|---|
| `NONE` | Rule does not affect visibility — only restricts options or range |
| `SHOW` | Field is visible **only** when the condition is met |
| `HIDE` | Field is hidden when the condition is met |

### `DependencyOperator`

| Value | Wire value | Description |
|---|---|---|
| `UNSPECIFIED` | `""` | Default sentinel — no leaf operator. Use when the condition is supplied as JSON in `condition` |
| `EQ` | `"eq"` | Equality |
| `NEQ` | `"neq"` | Inequality |
| `IN` | `"in"` | Value is in the set |
| `NOT_IN` | `"notIn"` | Value is not in the set |
| `CONTAINS` | `"contains"` | Contains substring |
| `NOT_CONTAINS` | `"notContains"` | Does not contain substring |
| `IS_EMPTY` | `"isEmpty"` | Value is empty |
| `IS_NOT_EMPTY` | `"isNotEmpty"` | Value is not empty |
| `GT` | `"gt"` | Greater than |
| `GTE` | `"gte"` | Greater than or equal |
| `LT` | `"lt"` | Less than |
| `LTE` | `"lte"` | Less than or equal |

> **`UNSPECIFIED`** — when using the `condition` parameter (JSON AST) instead of `field`/`operator`/`value`,
> leave `operator` at its default `UNSPECIFIED`. The framework detects this mode and parses the condition
> from JSON without applying any leaf operator.

### Examples

**Conditional visibility (SHOW):**
```kotlin
// "VIP Discount" visible only for VIP customers
@Column
@PortalField(label = "VIP Discount (%)", order = 5, renderer = RendererType.DECIMAL)
@PortalDependency(
    field = "customerType",
    operator = DependencyOperator.EQ,
    value = "VIP",
    visibility = DependencyVisibility.SHOW,
    message = "VIP discount is available for VIP customers only"
)
var vipDiscount: Double = 0.0
```

**Conditional visibility (HIDE):**
```kotlin
// "Cancellation reason" hidden until status == "CANCELLED"
@Column
@PortalField(label = "Cancellation Reason", order = 8, renderer = RendererType.TEXTAREA)
@PortalDependency(
    field = "status",
    operator = DependencyOperator.NEQ,
    value = "CANCELLED",
    visibility = DependencyVisibility.HIDE
)
var cancellationReason: String = ""
```

**Restricting allowed options (allowedValues):**
```kotlin
// New customers can only be assigned the NEW tag
@Column
@PortalField(label = "Tags", order = 4, renderer = RendererType.MULTI_SELECT, selectEnum = Tag::class)
@PortalDependency(
    field = "customerType",
    operator = DependencyOperator.EQ,
    value = "New",
    allowedValues = ["NEW"],
    message = "New customers can only have the NEW tag"
)
@PortalDependency(
    field = "customerType",
    operator = DependencyOperator.EQ,
    value = "Premium",
    allowedValues = ["PREMIUM", "REGULAR", "NEW"]
)
var tags: String = ""
```

**Numeric range constraint:**
```kotlin
// Credit limit depends on customer type
@Column
@PortalField(label = "Credit Limit", order = 3, renderer = RendererType.DECIMAL)
@PortalDependency(
    field = "customerType",
    operator = DependencyOperator.EQ,
    value = "New",
    max = "5000",
    message = "New customers can have a credit limit of at most $5,000"
)
@PortalDependency(
    field = "customerType",
    operator = DependencyOperator.EQ,
    value = "VIP",
    min = "5000",
    max = "500000"
)
var creditLimit: Double = 0.0
```

**Range using a field reference (`$` prefix):**
```kotlin
// Sale price cannot exceed the list price
@Column
@PortalField(label = "Sale Price", order = 5, renderer = RendererType.DECIMAL)
@PortalDependency(
    field = "isDiscounted",
    operator = DependencyOperator.EQ,
    value = "true",
    max = "\$listPrice"  // max = value of the listPrice field
)
var salePrice: Double = 0.0
```

**Complex JSON condition (anyOf/allOf):**
```kotlin
@Column
@PortalField(label = "Special Field", order = 9, renderer = RendererType.TEXT)
@PortalDependency(
    condition = """
    {
      "anyOf": [
        {"field": "customerType", "operator": "eq", "value": "VIP"},
        {
          "allOf": [
            {"field": "isActive", "operator": "eq", "value": "true"},
            {"field": "loyaltyPoints", "operator": "gte", "value": "1000"}
          ]
        }
      ]
    }
    """,
    visibility = DependencyVisibility.SHOW
)
var specialField: String = ""
```

**IN operator (multiple values):**
```kotlin
@Column
@PortalField(label = "Priority Service", order = 10, renderer = RendererType.BOOLEAN)
@PortalDependency(
    field = "customerType",
    operator = DependencyOperator.IN,
    values = ["VIP", "PREMIUM", "BUSINESS"],
    visibility = DependencyVisibility.SHOW
)
var priorityService: Boolean = false
```

---

## 8. `@PortalAction` + `@PortalFormField` — custom actions

### `@PortalAction`

**Class-level** annotation (repeatable) — declares a custom action button on an entity. Actions appear as buttons in the entity list table (per-row and optionally in bulk) and are executed via `/api/portal/data/{entity}/{id}/action/{name}`.

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class PortalAction(
    val name: String,
    val label: String,
    val labelKey: String = "",           // i18n key for the button label
    val icon: String = "play",
    val handler: KClass<*>,
    val formModel: KClass<*> = Void::class,
    val confirmMessage: String = "",
    val confirmMessageKey: String = "",  // i18n key for the confirmation dialog message
    val bulkAllowed: Boolean = false,
    val order: Int = 0,
    val variant: String = "default"
)
```

| Parameter | Type | Default | Description |
|---|---|---|---|
| `name` | `String` | — | Unique action identifier within the entity, used as a URL path segment |
| `label` | `String` | — | Human-readable button label shown in the UI |
| `labelKey` | `String` | `""` | i18n key for `label`, e.g. `"action.activate"` |
| `icon` | `String` | `"play"` | Lucide icon name displayed on the action button |
| `handler` | `KClass<*>` | — | Handler class — must be a CDI bean `@ApplicationScoped @Unremovable` |
| `formModel` | `KClass<*>` | `Void::class` | Optional data class as the action's input form model. When set, the UI shows a modal before executing the action |
| `confirmMessage` | `String` | `""` | Confirmation dialog message shown before executing. Empty string = no confirmation |
| `confirmMessageKey` | `String` | `""` | i18n key for `confirmMessage` |
| `bulkAllowed` | `Boolean` | `false` | Whether the action can be applied to multiple selected rows at once |
| `order` | `Int` | `0` | Sort position in the action button bar |
| `variant` | `String` | `"default"` | Visual button style: `"default"`, `"destructive"`, `"outline"`, `"secondary"`, `"ghost"` |

### Implementing an action handler

> **Important:** `ActionHandler` is **not an interface**. Handlers are plain CDI beans discovered via Kotlin reflection.
> The framework finds `validate`, `execute`, and optionally `executeBulk` methods by name.

The handler **must** be a CDI bean annotated with `@ApplicationScoped @Unremovable`:

```kotlin
import dev.quatrion.portal.model.ActionResult
import dev.quatrion.portal.model.EntityData
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@io.quarkus.arc.Unremovable
class ActivateCustomerHandler {

    val actionName = "activate"

    suspend fun validate(entity: EntityData, formData: EntityData?): String? {
        // Return an error message or null if validation passes
        val isActive = entity["isActive"] as? Boolean ?: false
        return if (isActive) "Customer is already active" else null
    }

    suspend fun execute(entity: EntityData, formData: EntityData?): ActionResult {
        // ✅ Just set fields — framework auto-merges after execute() returns
        // ❌ Do NOT call entity.persist() / merge() — causes session conflicts
        val id = entity["id"]
        return ActionResult.Success("Customer $id activated.", refreshTable = true)
    }

    // Optional bulk implementation
    suspend fun executeBulk(
        entities: List<EntityData>,
        formData: EntityData?
    ): ActionResult {
        return ActionResult.Success("Activated ${entities.size} customers.", refreshTable = true)
    }
}
```

**`EntityData`** — a class representing entity data as named fields. Behaves like a map, serialized by Jackson as a flat JSON object:

```kotlin
// Reading fields
val name = entity["name"] as? String ?: "Unknown"
val id   = entity["id"]
val ok   = "status" in entity   // check for key presence
```

**`ActionResult` — possible return types:**

```kotlin
// Navigation link shown after a successful action
data class ResultLink(
    val label: String,
    val entityName: String,
    val module: String,
    val entityId: Long
)
```

| Type | Description |
|---|---|
| `ActionResult.Success(message, data?, refreshTable, links)` | Success. `refreshTable` defaults to **`true`**. `links` — optional navigation buttons |
| `ActionResult.Error(message, details?)` | Error with an optional field-level details map |
| `ActionResult.Redirect(url)` | Redirects the user to the given URL |
| `ActionResult.Download(fileName, contentType, data)` | Triggers a file download |

```kotlin
// Success with a navigation link to a related record
return ActionResult.Success(
    message = "Task started.",
    refreshTable = true,
    links = listOf(ResultLink("Go to TaskRun", "TaskRun", "System", taskRunId))
)
```

### `@PortalFormField`

**Field-level** annotation on a data class — describes a single field in an action's input form.

> **Important:** Use the `@field:` use-site target on Kotlin data class properties so the annotation ends up on the JVM backing field and can be read by Java reflection.

```kotlin
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class PortalFormField(
    val label: String,
    val labelKey: String = "",           // i18n key for the field label
    val renderer: RendererType = RendererType.TEXT,
    val required: Boolean = false,
    val placeholder: String = "",
    val tooltip: String = "",
    val selectOptions: Array<String> = [],
    val selectEnum: KClass<*> = Unit::class,
    val order: Int = 0
)
```

### Example — action with form

**1. Form model:**
```kotlin
data class ProcessOrderForm(
    @field:PortalFormField(
        label = "Priority",
        renderer = RendererType.SELECT,
        selectOptions = ["NORMAL", "HIGH", "URGENT"],
        required = true,
        order = 1
    )
    val priority: String = "NORMAL",

    @field:PortalFormField(
        label = "Operator Notes",
        renderer = RendererType.TEXTAREA,
        placeholder = "Enter notes...",
        order = 2
    )
    val notes: String = "",

    @field:PortalFormField(
        label = "Scheduled Date",
        renderer = RendererType.DATE,
        required = true,
        order = 3
    )
    val scheduledDate: String = ""
)
```

**2. Handler:**
```kotlin
@ApplicationScoped
@io.quarkus.arc.Unremovable
class ProcessOrderHandler {

    val actionName = "processOrder"

    suspend fun validate(entity: EntityData, formData: EntityData?): String? {
        val status = entity["status"] as? String
        return if (status == "CANCELLED") "Cannot process a cancelled order" else null
    }

    suspend fun execute(entity: EntityData, formData: EntityData?): ActionResult {
        val priority = formData?.get("priority") as? String ?: "NORMAL"
        val orderId = entity["id"]
        // business logic...
        return ActionResult.Success("Order $orderId processed with priority $priority.")
    }
}
```

**3. Annotation on the entity:**
```kotlin
@PortalAction(
    name = "processOrder",
    label = "Process Order",
    icon = "play",
    handler = ProcessOrderHandler::class,
    formModel = ProcessOrderForm::class,
    confirmMessage = "Are you sure you want to process this order?",
    order = 1
)
@PortalEntity(label = "Order", module = "CRM", tabs = OrderTab::class)
@Entity
class Order { ... }
```

### More action examples

**Destructive action with confirmation:**
```kotlin
@PortalAction(
    name = "cancelOrder",
    label = "Cancel",
    icon = "x-circle",
    handler = CancelOrderHandler::class,
    confirmMessage = "Are you sure you want to cancel this order? This cannot be undone.",
    variant = "destructive",
    order = 2
)
```

**Bulk action:**
```kotlin
@PortalAction(
    name = "sendEmail",
    label = "Send Email",
    icon = "mail",
    handler = SendEmailHandler::class,
    bulkAllowed = true,
    variant = "outline",
    order = 3
)
```

**File download action:**
```kotlin
@ApplicationScoped
@io.quarkus.arc.Unremovable
class ExportInvoiceHandler {
    val actionName = "exportPdf"
    suspend fun validate(entity: EntityData, formData: EntityData?) = null
    suspend fun execute(entity: EntityData, formData: EntityData?): ActionResult {
        val pdfBytes = generatePdf(entity)
        return ActionResult.Download("invoice-${entity["id"]}.pdf", "application/pdf", pdfBytes)
    }
}
```

---

## 9. `@PortalSecurity` — access control

**Class-level** annotation — configures role-based access control for a portal entity.

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PortalSecurity(
    val viewRoles: Array<String> = [],
    val editRoles: Array<String> = [],
    val deleteRoles: Array<String> = [],
    val actionRoles: Array<String> = [],
    val ownerField: String = "",
    val ownerRoles: Array<String> = []
)
```

| Parameter | Type | Description |
|---|---|---|
| `viewRoles` | `Array<String>` | Roles allowed to view / list this entity. Empty array = no restriction |
| `editRoles` | `Array<String>` | Roles allowed to create and update records |
| `deleteRoles` | `Array<String>` | Roles allowed to delete records |
| `actionRoles` | `Array<String>` | Roles allowed to execute `@PortalAction`s on this entity |
| `ownerField` | `String` | Name of the entity field storing the JWT `sub` of the record owner (e.g. `"createdBySub"`). When non-empty, enables **row-level security** for roles listed in `ownerRoles` |
| `ownerRoles` | `Array<String>` | Roles restricted to their own records (via `ownerField`). Users **not** in this list see all records |

> **Row-level security (`ownerField` + `ownerRoles`):**
> - **List / export**: users with a role in `ownerRoles` see only records where `ownerField == JWT.sub`
> - **Create**: the `ownerField` is automatically set to `JWT.sub`
> - **Update / delete**: users with a role in `ownerRoles` can only modify records they own

> Role names must match the values in the JWT/OIDC token attribute configured via `PortalUiConfig.SecurityConfig.rolesAttribute`.

### Examples

**Full security configuration:**
```kotlin
@PortalSecurity(
    viewRoles = ["user", "editor", "admin"],
    editRoles = ["editor", "admin"],
    deleteRoles = ["admin"],
    actionRoles = ["admin"]
)
@PortalEntity(label = "Customer", module = "CRM")
@Entity
class Customer { ... }
```

**Read-only for regular users:**
```kotlin
@PortalSecurity(
    viewRoles = ["user", "admin"],
    editRoles = ["admin"],
    deleteRoles = ["admin"],
    actionRoles = ["admin"]
)
@PortalEntity(label = "Audit Log", module = "System", allowCreate = false, allowEdit = false)
@Entity
class AuditLog { ... }
```

**Admin-only entity:**
```kotlin
@PortalSecurity(
    viewRoles = ["admin"],
    editRoles = ["admin"],
    deleteRoles = ["admin"],
    actionRoles = ["admin"]
)
@PortalEntity(label = "System Configuration", module = "System")
@Entity
class SystemConfig { ... }
```

**Row-level security (sales rep sees only their own records):**
```kotlin
@PortalSecurity(
    viewRoles = ["sales", "manager", "admin"],
    editRoles = ["sales", "manager", "admin"],
    deleteRoles = ["manager", "admin"],
    actionRoles = ["manager", "admin"],
    ownerField = "createdBySub",   // entity field storing JWT.sub of the owner
    ownerRoles = ["sales"]         // "sales" role sees only their own records
)
@PortalEntity(label = "Sales Leads", module = "CRM")
@Entity
class SalesLead {
    @Column(length = 100)
    @PortalField(label = "Owner (sub)", hidden = true)
    var createdBySub: String = ""  // set automatically from JWT on create
    // ...
}
```

---

## 10. Registering entities in `PortalModuleConfig`

Every entity annotated with `@PortalEntity` **must** be registered in a class that extends `PortalModuleConfig`. The CDI bean must be `@ApplicationScoped`.

### Configuration structure

```kotlin
@ApplicationScoped
class MyModuleConfig : PortalModuleConfig() {

    override fun modules() = listOf(
        ModuleDef(
            name = "MyModule",        // must match @PortalEntity.module
            label = "My Module",      // label shown in UI
            icon = "layers",           // Lucide icon
            order = 1,
            defaultEntity = MyEntity::class.java,
            entities = listOf(
                EntityRef(entityClass = MyEntity::class.java,       group = "Core",   order = 1),
                EntityRef(entityClass = AnotherEntity::class.java,  group = "Core",   order = 2),
                EntityRef(entityClass = UngroupedEntity::class.java, order = 10)  // no group
            )
        )
    )
}
```

### `ModuleDef` fields

| Field | Type | Default | Description |
|---|---|---|---|
| `name` | `String` | — | Module identifier (must match `@PortalEntity.module`) |
| `label` | `String` | — | Display name shown in the navigation |
| `labelKey` | `String` | `""` | i18n key for `label`, e.g. `"module.crm"` |
| `icon` | `String` | `"folder"` | Lucide icon for the module |
| `order` | `Int` | `0` | Sort position for the module |
| `defaultEntity` | `Class<*>` | — | Entity opened when the module is clicked |
| `entities` | `List<EntityRef>` | `[]` | List of entities in the module |

### `EntityRef` fields

| Field | Type | Default | Description |
|---|---|---|---|
| `entityClass` | `Class<*>` | — | JPA entity class |
| `group` | `String` | `""` | Group name in the sidebar menu (empty = no group) |
| `order` | `Int` | `0` | Sort position within the group / module |

### Multiple modules

```kotlin
@ApplicationScoped
class AppModuleConfig : PortalModuleConfig() {

    override fun modules() = listOf(crmModule(), catalogModule(), systemModule())

    private fun crmModule() = ModuleDef(
        name = "CRM", label = "CRM", icon = "users", order = 1,
        defaultEntity = Customer::class.java,
        entities = listOf(
            EntityRef(Customer::class.java, group = "Customers", order = 1),
            EntityRef(Lead::class.java,     group = "Customers", order = 2),
            EntityRef(Country::class.java,  group = "Dictionaries", order = 1),
        )
    )

    private fun catalogModule() = ModuleDef(
        name = "Catalog", label = "Catalog", icon = "package", order = 2,
        defaultEntity = Product::class.java,
        entities = listOf(
            EntityRef(Product::class.java,  group = "Products",     order = 1),
            EntityRef(Category::class.java, group = "Dictionaries", order = 1),
            EntityRef(Supplier::class.java, order = 10)
        )
    )

    private fun systemModule() = ModuleDef(
        name = "System", label = "System", icon = "settings", order = 99,
        defaultEntity = AuditLog::class.java,
        entities = listOf(
            EntityRef(AuditLog::class.java,    order = 1),
            EntityRef(SystemConfig::class.java, order = 2)
        )
    )
}
```

---

## 11. Complete example — Customer entity

A complete example using all discussed annotations:

```kotlin
// ─── Tabs ───────────────────────────────────────────────────────────────────
enum class CustomerTab(
    override val label: String,
    override val icon: String,
    override val order: Int
) : PortalTab {
    BASIC("Basic Info",   "user",         0),
    CONTACT("Contact",    "phone",         1),
    FINANCIAL("Financial","dollar-sign",   2)
}

// ─── Status enum ────────────────────────────────────────────────────────────
enum class CustomerType(val label: String) {
    NEW("New"), REGULAR("Regular"), PREMIUM("Premium"), VIP("VIP");
    override fun toString() = label
}

enum class CustomerTag { VIP, NEW, REGULAR, PREMIUM }

// ─── Action form model ───────────────────────────────────────────────────────
data class SendEmailForm(
    @field:PortalFormField(
        label = "Subject",
        renderer = RendererType.TEXT,
        required = true,
        order = 1
    )
    val subject: String = "",

    @field:PortalFormField(
        label = "Body",
        renderer = RendererType.TEXTAREA,
        required = true,
        order = 2
    )
    val body: String = ""
)

// ─── Action handlers ─────────────────────────────────────────────────────────
@ApplicationScoped
@io.quarkus.arc.Unremovable
class ActivateCustomerHandler {
    val actionName = "activate"
    suspend fun validate(entity: EntityData, formData: EntityData?) =
        if (entity["isActive"] as? Boolean == true) "Customer is already active" else null
    suspend fun execute(entity: EntityData, formData: EntityData?) =
        ActionResult.Success("Customer activated.", refreshTable = true)
}

@ApplicationScoped
@io.quarkus.arc.Unremovable
class SendEmailHandler {
    val actionName = "sendEmail"
    suspend fun validate(entity: EntityData, formData: EntityData?) = null
    suspend fun execute(entity: EntityData, formData: EntityData?): ActionResult {
        val subject = formData?.get("subject") as? String ?: ""
        val email = entity["email"] as? String ?: ""
        // send logic...
        return ActionResult.Success("Email '$subject' sent to $email.")
    }
    suspend fun executeBulk(entities: List<EntityData>, formData: EntityData?) =
        ActionResult.Success("Email sent to ${entities.size} customers.")
}

// ─── Entity ──────────────────────────────────────────────────────────────────
@Entity
@Table(name = "customer")
@PortalEntity(
    label = "Customer",
    module = "CRM",
    group = "Customers",
    icon = "users",
    order = 1,
    description = "Company customers — main CRM dictionary",
    tabs = CustomerTab::class,
    pageSize = 25,
    softDelete = true,
    auditLog = true
)
@PortalSecurity(
    viewRoles = ["user", "admin"],
    editRoles = ["editor", "admin"],
    deleteRoles = ["admin"],
    actionRoles = ["editor", "admin"]
)
@PortalAction(
    name = "activate",
    label = "Activate",
    icon = "check-circle",
    handler = ActivateCustomerHandler::class,
    confirmMessage = "Are you sure you want to activate this customer?",
    order = 1
)
@PortalAction(
    name = "sendEmail",
    label = "Send Email",
    icon = "mail",
    handler = SendEmailHandler::class,
    formModel = SendEmailForm::class,
    bulkAllowed = true,
    order = 2,
    variant = "outline"
)
class Customer {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PortalField(label = "ID", tab = "BASIC", order = 0, readonly = true, showInFilter = false)
    var id: Long = 0

    @Column(length = 100, nullable = false)
    @PortalField(
        label = "Full Name",
        tab = "BASIC", order = 1,
        required = true,
        renderer = RendererType.TEXT,
        filterType = FilterType.CONTAINS
    )
    var name: String = ""

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    @PortalField(
        label = "Customer Type",
        tab = "BASIC", order = 2,
        renderer = RendererType.SELECT,
        filterType = FilterType.IN,
        selectEnum = CustomerType::class
    )
    var customerType: CustomerType? = null

    @Column
    @PortalField(
        label = "Active",
        tab = "BASIC", order = 3,
        renderer = RendererType.BOOLEAN,
        filterType = FilterType.BOOLEAN
    )
    var isActive: Boolean = true

    @Column(unique = true)
    @PortalField(
        label = "Email",
        tab = "CONTACT", order = 1,
        renderer = RendererType.EMAIL,
        filterType = FilterType.EXACT
    )
    var email: String = ""

    @Column(length = 20)
    @Regex(
        pattern = """^\+?[\d\s\-]{7,20}$""",
        message = "Invalid phone number format"
    )
    @PortalField(
        label = "Phone",
        tab = "CONTACT", order = 2,
        renderer = RendererType.TEXT,
        filterType = FilterType.STARTS_WITH,
        placeholder = "+1 555 …"
    )
    var phone: String = ""

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    @PortalField(
        label = "Country",
        tab = "CONTACT", order = 3,
        renderer = RendererType.RELATION,
        filterType = FilterType.EXACT,
        showInTable = false
    )
    @PortalRelation(
        targetEntity = Country::class,
        editable = true,
        displayFields = ["name", "code"],
        searchFields = ["name", "code"],
        labelField = "name",
        valueField = "id"
    )
    var country: Country? = null

    @Column
    @PortalField(
        label = "Credit Limit",
        tab = "FINANCIAL", order = 1,
        renderer = RendererType.DECIMAL,
        filterType = FilterType.RANGE,
        placeholder = "0.00"
    )
    @PortalDependency(
        field = "customerType",
        operator = DependencyOperator.EQ,
        value = "NEW",
        max = "5000",
        message = "New customers can have a credit limit of at most \$5,000"
    )
    @PortalDependency(
        field = "customerType",
        operator = DependencyOperator.EQ,
        value = "VIP",
        min = "5000",
        max = "500000"
    )
    var creditLimit: Double = 0.0

    @Column
    @PortalField(
        label = "Tags",
        tab = "FINANCIAL", order = 2,
        renderer = RendererType.MULTI_SELECT,
        filterType = FilterType.IN,
        selectEnum = CustomerTag::class,
        showInTable = false
    )
    @PortalDependency(
        field = "customerType",
        operator = DependencyOperator.EQ,
        value = "NEW",
        allowedValues = ["NEW"],
        message = "New customers can only have the NEW tag"
    )
    @PortalDependency(
        field = "customerType",
        operator = DependencyOperator.EQ,
        value = "VIP",
        allowedValues = ["VIP", "PREMIUM"]
    )
    var tags: String = ""

    // Soft-delete — required when softDelete = true in @PortalEntity
    @Column
    @PortalField(label = "Deleted", hidden = true, showInTable = false, showInFilter = false)
    var deleted: Boolean = false
}
```

---

## 12. Common patterns and FAQ

### Which renderer should I use?

| Kotlin/JPA type | Recommended renderer |
|---|---|
| `String` (short) | `TEXT` or `EMAIL`, `URL`, `PASSWORD`, `COLOR` |
| `String` (long) | `TEXTAREA` |
| `String` (JSON) | `JSON` |
| `Int`, `Long` | `NUMBER` |
| `Double`, `BigDecimal` | `DECIMAL` |
| `Boolean` | `BOOLEAN` |
| `Enum` | `SELECT` with `selectEnum` |
| Comma-separated enum list | `MULTI_SELECT` with `selectEnum` |
| To-one association (`@ManyToOne`) | `RELATION` with `@PortalRelation` |
| Entity collection (`@OneToMany`) | `RELATION_LIST` with `@PortalRelation` |
| `String` (file path) | `FILE` |

### How to hide the ID field?

```kotlin
@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
@PortalField(label = "ID", order = 0, readonly = true, showInFilter = false)
var id: Long = 0
// showInTable = true by default — ID is still visible in the table
// To fully hide it: use hidden = true
```

### How to show a field only in the form, not in the table?

```kotlin
@PortalField(label = "Description", order = 5, renderer = RendererType.TEXTAREA, showInTable = false)
var description: String = ""
```

### How to show a field only in the table, not filterable?

```kotlin
@PortalField(label = "Name", order = 1, filterType = FilterType.NONE)
var name: String = ""
```

### How to set a default value?

```kotlin
@PortalField(label = "Active", order = 3, renderer = RendererType.BOOLEAN, defaultValue = "true")
var isActive: Boolean = true

@PortalField(label = "Status", order = 4, renderer = RendererType.SELECT,
             selectEnum = Status::class, defaultValue = "ACTIVE")
var status: Status? = null
```

### How to visually group fields within a tab?

```kotlin
// Fields with the same `group` value are rendered together in a section/card
@PortalField(label = "First Name", tab = "BASIC", order = 1, group = "Personal Details")
var firstName: String = ""

@PortalField(label = "Last Name", tab = "BASIC", order = 2, group = "Personal Details")
var lastName: String = ""

@PortalField(label = "Date of Birth", tab = "BASIC", order = 3, group = "Personal Details")
var birthDate: String = ""
```

### Why does `@PortalFormField` require `@field:`?

Kotlin places annotations on the property level, but Java Reflection reads them on the JVM field level. Without the `@field:` use-site target, `MetadataService` cannot find the annotation via reflection.

```kotlin
// ✅ Correct
data class MyForm(
    @field:PortalFormField(label = "Name", required = true)
    val name: String = ""
)

// ❌ Wrong — annotation will not be read
data class MyForm(
    @PortalFormField(label = "Name", required = true)
    val name: String = ""
)
```

### How does soft-delete work?

1. Add `softDelete = true` to `@PortalEntity`
2. Add a `deleted: Boolean = false` field to the entity
3. Optionally mark it with `@PortalField(label = "Deleted", hidden = true)` to hide it from the UI

The framework automatically filters out records with `deleted = true` in all list queries.

### How to configure cascading dropdowns?

Use `@PortalRelation(dependsOn = "...")` together with a helper picker on the same form. The frontend automatically passes the helper's current value as a filter parameter when fetching options from the `/lookup` endpoint. The helper name must match the **association on the target entity** (backend filters `e.{dependsOn}.id`).

```kotlin
// Genre helper on the Loan form (value NOT persisted)
@Transient
@PortalField(label = "Filter by genre", renderer = RendererType.RELATION)
@PortalRelation(targetEntity = Genre::class, searchFields = ["name"])
var genre: Long? = null

// Book (depends on genre) — backend adds: AND e.genre.id = :depVal
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "book_id", nullable = false)
@PortalField(label = "Book", renderer = RendererType.RELATION)
@PortalRelation(
    targetEntity = Book::class,
    searchFields = ["title", "isbn"],
    dependsOn = "genre"
)
var book: Book? = null
```

### Recommended annotation order on a field

For readability and consistency:

```kotlin
@ManyToOne(...) / @OneToMany(...) // JPA association (if applicable)
@JoinColumn(...)         // FK mapping (to-one owning side, if applicable)
@Enumerated(...)         // JPA (optional)
@Regex(...)              // Pattern validation
@PortalField(...)        // UI field declaration
@PortalRelation(...)     // Relation presentation (if applicable)
@PortalDependency(...)   // Conditional rules (if applicable, repeatable)
var fieldName: Type = defaultValue
```

---

## 13. `RowColor` — row coloring

Entities can implement the `RowColorProvider` interface to control the background color of table rows. The framework automatically appends the color to entity data as the `_rowColor` field.

```kotlin
enum class RowColor {
    NONE, SUCCESS, WARNING, DANGER, INFO, MUTED
}

interface RowColorProvider {
    fun currentStatus(): RowColor?
}
```

### Color to CSS class mapping

| Value | Color | CSS class |
|---|---|---|
| `NONE` | — (default) | none |
| `SUCCESS` | green | `qp-tr-success` |
| `WARNING` | yellow | `qp-tr-warning` |
| `DANGER` | red | `qp-tr-danger` |
| `INFO` | blue | `qp-tr-info` |
| `MUTED` | grey | `qp-tr-muted` |

### How to implement

```kotlin
@Entity
@Table(name = "task_run")
@PortalEntity(label = "Task Runs", module = "System")
class TaskRun : RowColorProvider {

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    @PortalField(label = "Status", order = 2, renderer = RendererType.SELECT, selectEnum = TaskRunStatus::class)
    var status: TaskRunStatus = TaskRunStatus.RUNNING

    override fun currentStatus(): RowColor? = when (status) {
        TaskRunStatus.RUNNING   -> RowColor.INFO     // blue — in progress
        TaskRunStatus.COMPLETED -> RowColor.SUCCESS  // green — done
        TaskRunStatus.ERROR     -> RowColor.DANGER   // red — failed
        TaskRunStatus.CANCELLED -> RowColor.WARNING  // yellow — cancelled
    }
}
```

> Returning `null` from `currentStatus()` is equivalent to `RowColor.NONE` — the row is not coloured.
> Entity metadata automatically includes `rowColorField = "_rowColor"` to signal the frontend.

---

## 14. `portal.ui` configuration — `application.properties`

The framework reads UI configuration via SmallRye Config with the prefix `portal.ui`. All properties have defaults — override only what you need.

```properties
# Browser tab title
portal.ui.title=Quatrion Portal

# Logo path (optional)
# portal.ui.logo=/assets/logo.png

# ── Layout ────────────────────────────────────────────────────────────────────
portal.ui.layout.sidebar.width=256
portal.ui.layout.sidebar.collapsible=true
portal.ui.layout.sidebar.default-collapsed=false
portal.ui.layout.content.max-width=1600
portal.ui.layout.top-bar.height=56
portal.ui.layout.top-bar.show-module-selector=true
portal.ui.layout.top-bar.show-user-menu=true
portal.ui.layout.top-bar.show-search=false

# ── Theme (colors) ────────────────────────────────────────────────────────────
portal.ui.theme.primary-color=#2563eb
portal.ui.theme.accent-color=#3b82f6
portal.ui.theme.sidebar-bg=#1e293b
portal.ui.theme.sidebar-text=#e2e8f0
portal.ui.theme.header-bg=#ffffff

# ── Table ─────────────────────────────────────────────────────────────────────
portal.ui.table.default-page-size=25
portal.ui.table.show-row-numbers=false
portal.ui.table.enable-export=false
portal.ui.table.sticky-header=true

# ── Form ──────────────────────────────────────────────────────────────────────
portal.ui.form.modal-width=lg           # sm | md | lg | xl | 2xl
portal.ui.form.nested-modal-width=md
portal.ui.form.show-tab-icons=true
portal.ui.form.auto-save-interval=0     # seconds; 0 = disabled

# ── Filters ───────────────────────────────────────────────────────────────────
portal.ui.filter.position=modal         # modal | sidebar | inline
portal.ui.filter.remember-filters=true
portal.ui.filter.max-filter-fields=20

# ── Security ──────────────────────────────────────────────────────────────────
portal.ui.security.provider=none        # none | keycloak | oidc
portal.ui.security.roles-attribute=realm_access.roles

# ── Export ────────────────────────────────────────────────────────────────────
portal.export.max-rows=50000            # max rows per export request
```

### Key properties

| Property | Default | Description |
|---|---|---|
| `portal.ui.security.provider` | `"none"` | Security provider: `"none"` (no auth), `"keycloak"`, `"oidc"` |
| `portal.ui.security.roles-attribute` | `"realm_access.roles"` | JSON path in the JWT token where user roles are read from. Must match role names in `@PortalSecurity` |
| `portal.ui.table.enable-export` | `false` | When `true`, an export button (CSV/XLSX/JSON/PDF) appears in the table toolbar |
| `portal.ui.form.auto-save-interval` | `0` | Form auto-save interval in seconds. `0` = disabled |
| `portal.export.max-rows` | `50000` | Max rows exported per request. Excess returns HTTP 413 |

---

## 15. Full REST API endpoint reference

All endpoints require authentication (`@Authenticated`). Base path: `/api/portal`.

### Metadata

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/portal/metadata` | Full portal metadata (entities, fields, actions, UI config). Supports ETag/304 |

### Entity CRUD (`/api/portal/data/{entityName}`)

| Method | Path | Description |
|---|---|---|
| `GET` | `/{entityName}` | List records — pagination, sorting, filtering via query params |
| `GET` | `/{entityName}/{id}` | Get record by ID |
| `POST` | `/{entityName}` | Create record. Returns `201 Created` |
| `PUT` | `/{entityName}/{id}` | Update record. Supports optimistic locking (`409 Conflict`) |
| `DELETE` | `/{entityName}/{id}` | Delete record. Returns `204 No Content` |
| `DELETE` | `/{entityName}/bulk` | Delete multiple records. Body: `{"ids": [1, 2, 3]}` |
| `PUT` | `/{entityName}/bulk-update` | Update a single field on multiple records. Body: `{"ids": [1,2], "field": "status", "value": "ACTIVE"}` |

### Lookup and search

| Method | Path | Description |
|---|---|---|
| `GET` | `/{entityName}/lookup` | Picker options for relation fields. Params: `q`, `labelField`, `valueField`, `max`, `filterQuery`, `dependsOnField`, `dependsOnValue`, `orderBy` |
| `GET` | `/{entityName}/search` | Full-text search across TEXT/TEXTAREA/EMAIL/URL fields. Params: `q` (min. 2 chars), `page`, `size` |
| `GET` | `/{entityName}/count` | Record count. Returns `{"count": 42}` |
| `GET` | `/{entityName}/stats` | Statistics for numeric fields (min/max/avg/sum) |

### Soft-delete

| Method | Path | Description |
|---|---|---|
| `GET` | `/{entityName}/deleted` | List soft-deleted records (entity must have `softDelete = true`) |
| `POST` | `/{entityName}/{id}/restore` | Restore a soft-deleted record |

### Actions and history

| Method | Path | Description |
|---|---|---|
| `POST` | `/{entityName}/{id}/action/{actionName}` | Execute an action. Body: `EntityData` (form data), optional |
| `GET` | `/{entityName}/{id}/history` | Change history (entity must have `auditLog = true`). Params: `page`, `size` |

### Export and import

| Method | Path | Description |
|---|---|---|
| `GET` | `/{entityName}/export/csv` | Export to CSV. Applies active filters from query params. Limit: `portal.export.max-rows` |
| `GET` | `/{entityName}/export/xlsx` | Export to XLSX |
| `GET` | `/{entityName}/export/json` | Export to JSON |
| `GET` | `/{entityName}/export/pdf` | Export to PDF |
| `POST` | `/{entityName}/import` | Import from CSV. Body: `{"csv": "header1,header2\nval1,val2\n..."}` |
| `POST` | `/{entityName}/batch` | Batch-create from JSON array. Body: `[{...}, {...}]` |

### Filtering in list requests

Query parameters for `GET /{entityName}`:

```
?filter[fieldName][operator]=value
&sort=fieldName&order=asc
&page=0&size=25
```

Examples:
```
?filter[status][eq]=ACTIVE
?filter[name][contains]=smith
?filter[price][gte]=100&filter[price][lte]=1000
?filter[customerType][in]=VIP,PREMIUM
?sort=name&order=asc&page=0&size=50
```

---

## 16. Annotation quick reference

| Annotation | Target | Repeatable | Purpose |
|---|---|---|---|
| `@PortalEntity` | Class | No | Registers entity; sets label, module, icon, tabs, permissions, page size, soft-delete |
| `@PortalAction` | Class | **Yes** | Declares an action button with handler, optional form, confirmation dialog |
| `@PortalSecurity` | Class | No | Role-based access control for view/edit/delete/action + row-level ownership |
| `@PortalField` | Field/Function | No | Declares a UI field; sets renderer, filter type, validation constraints |
| `@PortalRelation` | Field | No | Presentation for a RELATION/RELATION_LIST association: target override, display options, picker filters, delete marker, per-row actions |
| `@PortalDependency` | Field/Function | **Yes** | Conditional visibility, allowed values, and numeric range rules |
| `@PortalFormField` | Field | No | Describes a field in an action form model (use `@field:` target) |
| `@Regex` | Field | No | Attaches a regex pattern for frontend client-side validation |
| `RowColorProvider` | Interface (class) | — | Implement to control table row background colour |
