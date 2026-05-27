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
6. [@PortalRelation + @PortalLookup — relations](#6-portalrelation--portallookup--relations)
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
| `RELATION` | ManyToOne / OneToOne lookup picker | Requires `@PortalRelation` + `@PortalLookup` |
| `RELATION_LIST` | OneToMany / ManyToMany inline list | Requires `@PortalRelation` + `@PortalLookup` |
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

## 6. `@PortalRelation` + `@PortalLookup` — relations

---

### How it works — data flow

Both annotations **must be placed together** on the same field. At server startup, `MetadataService` merges them into a single `RelationMetadata` object, which is sent to the frontend as part of the field's metadata.

```
JPA entity
  @PortalField(renderer = RELATION)    ← tells frontend "render a picker"
  @PortalRelation(targetEntity = ...)   ← describes how to display and which entity to link
  @PortalLookup(labelField = ...)       ← describes how to call /lookup endpoint
  var countryId: Long? = null
         │
         ▼ MetadataService (startup)
         │
  RelationMetadata {
    targetEntity  = "Country"           ← simple class name
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
  Table:  RelationCell  →  GET /api/portal/data/Country/{id}
                            displays the value of the "name" field of the selected record
         │
  Form:   RelationRenderer  →  GET /api/portal/data/Country/lookup?q=pol&labelField=name&valueField=id
                                autocomplete dropdown with search results
```

---

### Two rendering modes

| Renderer | When to use | Field in entity |
|---|---|---|
| `RendererType.RELATION` | ManyToOne, OneToOne — stores a **single** foreign key | `var xyzId: Long? = null` |
| `RendererType.RELATION_LIST` | OneToMany, ManyToMany — list of related entities | `@Transient var items: List<Entity>? = null` |

> **Important for `RELATION_LIST`:** The field must be annotated with `@Transient` — it is not a database column. It exists solely to carry metadata to the frontend. The backend dynamically loads related records based on `RelationMetadata`.

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
    val actions: Array<RelationRowAction> = []
)
```

#### `targetEntity: KClass<*> = Unit::class`

The target JPA entity class. When set (other than `Unit::class`), the framework uses its simple name (`simpleName`) as the entity identifier when calling endpoints.

**When can it be omitted?** The framework will try to infer the target entity:
- For collections (`List<T>`) — from the generic type argument `T`
- For reference fields (`var xyzId: Long?`) — from the field name (convention `xyzId` → `Xyz`)

In practice, always explicitly set `targetEntity` to avoid ambiguity.

```kotlin
// ✅ Explicit target entity — recommended
@PortalRelation(targetEntity = DemoCountry::class, ...)

// ⚠️ Without targetEntity — framework tries to infer from List<DemoOrderItem>
@PortalRelation(displayFields = ["productId", "quantity"])
var items: List<DemoOrderItem>? = null
```

---

#### `displayFields: Array<String> = []`

Target entity fields shown as **columns in the table** in `RELATION_LIST` mode, or as **additional info** in the `RELATION` picker.

```kotlin
// Picker shows "John Smith (j.smith@example.com)"
@PortalRelation(
    targetEntity = DemoCustomer::class,
    displayFields = ["name", "email"],   // both shown as columns in the list
    searchFields = ["name", "email"]
)
var customerId: Long? = null
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

When `false`, the picker is locked (read-only in the form). Useful e.g. for the `orderId` field on an order item — the parent order ID should not be changed from within the child.

```kotlin
// Order field — read-only (parent reference)
@PortalRelation(
    targetEntity = DemoOrder::class,
    editable = false,            // picker is locked
    displayFields = ["orderNumber"],
    searchFields = ["orderNumber"]
)
var orderId: Long? = null
```

---

#### `inlineEdit: Boolean = false`

`RELATION_LIST` only. When `true`, related records can be edited directly in the embedded table inside the parent form, without opening a separate modal.

```kotlin
@PortalRelation(
    targetEntity = DemoOrderItem::class,
    editable = true,
    inlineEdit = true,           // edit directly in the items table
    displayFields = ["productId", "quantity", "unitPrice"],
    maxItems = 100
)
var items: List<DemoOrderItem>? = null
```

---

#### `createAllowed: Boolean = false`

When `true`, the picker shows a **"Create new"** option. The user can open the target entity's create form directly from within the picker, without navigating away.

```kotlin
@PortalRelation(
    targetEntity = DemoSupplier::class,
    displayFields = ["name"],
    searchFields = ["name"],
    createAllowed = true         // "Add new supplier" in the picker
)
var supplierId: Long? = null
```

---

#### `cascadeDelete: Boolean = false`

Informational only — does not configure actual JPA cascade behaviour. When `true`, the frontend may display a warning when the parent is deleted. **JPA cascade must be configured separately** via `cascade = CascadeType.REMOVE` in the entity mapping.

---

#### `orderBy: String = ""`

HQL `ORDER BY` fragment (without the `ORDER BY` keyword) applied when loading the relation list. The entity alias is `e`.

```kotlin
@PortalRelation(
    targetEntity = DemoCategory::class,
    displayFields = ["name"],
    orderBy = "name ASC"         // category list sorted alphabetically
)
var categoryId: Long? = null
```

When empty, the backend sorts by `labelField` (from `@PortalLookup`) ascending.

---

#### `maxItems: Int = 0`

Maximum number of items in a `RELATION_LIST`. When `0` (default) — no limit. The frontend displays a warning when the limit is reached.

---

### `@PortalLookup` — detailed parameter reference

```kotlin
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PortalLookup(
    val labelField: String = "name",
    val valueField: String = "id",
    val filterQuery: String = "",
    val dependsOn: String = "",
    val maxResults: Int = 100,
    val parentField: String = ""
)
```

#### `labelField: String = "name"`

The target entity field displayed as the **human-readable label** in the picker and in the table cell.

- In the table: `RelationCell` fetches the record via `GET /api/portal/data/{targetEntity}/{id}` and displays `record[labelField]`
- In the form picker: the label of each option in the dropdown

```kotlin
// Table shows the value of the "name" field, e.g. "Poland"
@PortalLookup(labelField = "name", valueField = "id")

// Table shows the "orderNumber" value, e.g. "ORD-2024-001"
@PortalLookup(labelField = "orderNumber", valueField = "id")
```

---

#### `valueField: String = "id"`

The target entity field whose **value is stored** in the parent entity's column (foreign key). Defaults to `"id"` — rarely needs to be changed unless the relation is keyed by a unique field other than the primary key.

```kotlin
// FK stores the ISO "code" value instead of numeric "id"
@PortalLookup(labelField = "name", valueField = "code")
var countryCode: String? = null
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
@PortalLookup(filterQuery = "e.isActive = true")

// Only European countries
@PortalLookup(filterQuery = "e.continent = 'EUROPE'")

// Only products in stock
@PortalLookup(filterQuery = "e.quantity > 0")

// Multiple conditions (AND)
@PortalLookup(filterQuery = "e.isActive = true AND e.isVerified = true")
```

> **Important:** The entity alias in `filterQuery` must always be `e`. Do not add the `WHERE` keyword.

---

#### `dependsOn: String = ""`

The name of **another field on the same form** whose current value is automatically passed as a filter to the `/lookup` endpoint. Enables **cascading dropdowns** — e.g. selecting a country restricts the available cities.

**How it works technically:**

1. User selects a value in the `countryId` field (e.g. `42`)
2. Frontend re-calls `/api/portal/data/City/lookup?dependsOnField=countryId&dependsOnValue=42`
3. Backend adds to HQL: `AND e.countryId = :depVal`
4. Only cities assigned to the country with `id = 42` appear in the dropdown

```kotlin
// Source field (country)
@Column
@PortalField(label = "Country", order = 5, renderer = RendererType.RELATION)
@PortalRelation(targetEntity = DemoCountry::class, searchFields = ["name"])
@PortalLookup(labelField = "name", valueField = "id")
var countryId: Long? = null

// Dependent field (city filtered by selected country)
@Column
@PortalField(label = "City", order = 6, renderer = RendererType.RELATION)
@PortalRelation(targetEntity = City::class, searchFields = ["name"])
@PortalLookup(
    labelField = "name",
    valueField = "id",
    dependsOn = "countryId"    // field name on the CURRENT form (not the table!)
)
var cityId: Long? = null
```

> **Note:** The field on the target entity used for filtering (e.g. `City.countryId`) must have the same name as the field referenced by `dependsOn`. The backend generates: `e.{dependsOn} = :depVal`.

---

#### `maxResults: Int = 100`

Maximum number of options returned from the `/lookup` endpoint per request. Decrease for very large tables, increase when users need a wider selection without typing.

```kotlin
// Small dictionary table — show all options immediately
@PortalLookup(labelField = "name", valueField = "id", maxResults = 500)

// Large customer table — limit autocomplete suggestions
@PortalLookup(labelField = "name", valueField = "id", maxResults = 20)
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
| `cascadeDelete` | `Boolean` | `false` | Informational: whether deleting the parent cascades to children |
| `orderBy` | `String` | `""` | HQL `ORDER BY` fragment (without keyword), alias `e` |
| `maxItems` | `Int` | `0` | Item limit in `RELATION_LIST` (0 = unlimited) |
| `downloadAction` | `String` | `""` | Name of a `@PortalAction` on the target entity that triggers a file download. When non-empty, a download icon button is rendered for each row in the `RELATION_LIST` |
| `actions` | `Array<RelationRowAction>` | `[]` | Per-row action buttons in the `RELATION_LIST` table (see `RelationRowAction`) |

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
@Transient
@PortalField(label = "Files", renderer = RendererType.RELATION_LIST, showInFilter = false, showInTable = false)
@PortalRelation(
    targetEntity = TaskRunFile::class,
    editable = false,
    displayFields = ["fileName", "fileSize"],
    actions = [RelationRowAction.DOWNLOAD]   // download button on each row
)
@PortalLookup(labelField = "fileName", valueField = "id", parentField = "taskRunId")
var files: List<TaskRunFile>? = null
```

#### `@PortalLookup`

| Parameter | Type | Default | Description |
|---|---|---|---|
| `labelField` | `String` | `"name"` | Target entity field shown as label in picker and table cell |
| `valueField` | `String` | `"id"` | Target entity field stored as value (foreign key) |
| `filterQuery` | `String` | `""` | Permanent HQL WHERE filter (alias `e.`), e.g. `"e.isActive = true"` |
| `dependsOn` | `String` | `""` | Name of another form field — enables cascading dropdown |
| `maxResults` | `Int` | `100` | Max options returned by `/lookup` per request |
| `parentField` | `String` | `""` | `RELATION_LIST` only: name of the field in the **target** entity holding the FK back to the parent (e.g. `"memberId"` on `Loan` when the list is on `Member`). When set, the frontend auto-fetches related records via `GET /api/portal/data/{target}?filter[parentField][eq]={parentId}` instead of relying on the parent's `getById` response |

---

### Differences: `displayFields` vs `searchFields` vs `labelField`

| Property | Annotation | What it does |
|---|---|---|
| `labelField` | `@PortalLookup` | Field shown as label in the table cell and dropdown option |
| `displayFields` | `@PortalRelation` | Columns shown in `RELATION_LIST` table / extra info alongside the label |
| `searchFields` | `@PortalRelation` | Fields used for text search when user types in the picker |

Typical pattern — all three can be different:
```kotlin
@PortalRelation(
    targetEntity = DemoCustomer::class,
    displayFields = ["name", "email", "phone"],   // 3 columns in the relation list
    searchFields  = ["name", "email"]             // search by name and email
)
@PortalLookup(
    labelField = "name",   // table cell shows only the name
    valueField = "id"
)
var customerId: Long? = null
```

---

### Complete examples by scenario

**1. Simple ManyToOne relation (customer's country):**
```kotlin
@Column
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
    searchFields = ["name", "code"]
)
@PortalLookup(labelField = "name", valueField = "id")
var countryId: Long? = null
```

**2. Read-only relation (orderId on an order item):**
```kotlin
@Column
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
    searchFields = ["orderNumber"]
)
@PortalLookup(labelField = "orderNumber", valueField = "id")
var orderId: Long? = null
```

**3. Relation with filter (active categories only):**
```kotlin
@Column
@PortalField(label = "Category", order = 3, renderer = RendererType.RELATION)
@PortalRelation(
    targetEntity = DemoCategory::class,
    displayFields = ["name"],
    searchFields = ["name"]
)
@PortalLookup(
    labelField = "name",
    valueField = "id",
    filterQuery = "e.isActive = true"             // permanent HQL filter
)
var categoryId: Long? = null
```

**4. Cascading dropdowns (country → region):**
```kotlin
// Source field
@Column
@PortalField(label = "Country", order = 5, renderer = RendererType.RELATION)
@PortalRelation(targetEntity = DemoCountry::class, searchFields = ["name"])
@PortalLookup(labelField = "name", valueField = "id")
var countryId: Long? = null

// Dependent field — filtered by countryId value
@Column
@PortalField(label = "Region", order = 6, renderer = RendererType.RELATION)
@PortalRelation(targetEntity = Region::class, searchFields = ["name"])
@PortalLookup(
    labelField = "name",
    valueField = "id",
    dependsOn = "countryId"   // when countryId = 42, backend filters: e.countryId = 42
)
var regionId: Long? = null
```

**5. Read-only `RELATION_LIST` (customer's orders):**
```kotlin
@Transient
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
    searchFields = ["orderNumber"]
)
@PortalLookup(labelField = "orderNumber", valueField = "id")
var orders: List<DemoOrder>? = null
```

**6. Inline-editable `RELATION_LIST` with limit (order items):**
```kotlin
@Transient
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
    displayFields = ["productId", "quantity", "unitPrice"],
    maxItems = 100,                                // max 100 items
    orderBy = "id ASC"
)
@PortalLookup(labelField = "productId", valueField = "id")
var items: List<DemoOrderItem>? = null
```

**7. Relation with on-the-fly record creation:**
```kotlin
@Column
@PortalField(label = "Supplier", order = 5, renderer = RendererType.RELATION)
@PortalRelation(
    targetEntity = DemoSupplier::class,
    displayFields = ["name"],
    searchFields = ["name"],
    createAllowed = true                          // "Add new supplier" in the picker
)
@PortalLookup(labelField = "name", valueField = "id")
var supplierId: Long? = null
```

**8. Relation with a non-ID key:**
```kotlin
// FK stores the ISO "code" instead of numeric id
@Column(length = 3)
@PortalField(label = "Country (code)", order = 4, renderer = RendererType.RELATION)
@PortalRelation(
    targetEntity = DemoCountry::class,
    displayFields = ["name"],
    searchFields = ["name", "code"]
)
@PortalLookup(
    labelField = "name",
    valueField = "code"                           // stores ISO code, not id
)
var countryCode: String? = null
```

**9. Self-referencing relation (parent category):**
```kotlin
@Column
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
    searchFields = ["name"]
)
@PortalLookup(labelField = "name", valueField = "id")
var parentId: Long? = null
```

---

### Required annotation order on a field

```kotlin
@Column(...)                    // 1. JPA
@PortalField(                   // 2. UI field declaration
    renderer = RendererType.RELATION,
    ...
)
@PortalRelation(                // 3. Relation configuration
    targetEntity = ...,
    ...
)
@PortalLookup(                  // 4. Lookup configuration
    labelField = "name",
    valueField = "id"
)
var xyzId: Long? = null
```

---

### Common mistakes

| Mistake | Effect | Fix |
|---|---|---|
| Missing `@PortalLookup` on a RELATION field | Frontend uses defaults `name`/`id` for label/value fields | Always add `@PortalLookup` |
| `RELATION_LIST` without `@Transient` | Hibernate tries to map the collection as a column — startup error | Add `@Transient` |
| `filterQuery` using an alias other than `e` | HQL runtime error | Always use `e.fieldName` |
| `dependsOn` refers to a non-existent field | Cascading filter silently does nothing | Double-check the exact field name (case-sensitive) |
| `showInFilter = true` on `RELATION_LIST` | Relation lists cannot be filtered — nonsensical | Set `showInFilter = false` |
| Missing `targetEntity` with ambiguous collection | Framework may infer the wrong class | Always explicitly set `targetEntity` |

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

    @Column
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
        searchFields = ["name", "code"]
    )
    @PortalLookup(labelField = "name", valueField = "id")
    var countryId: Long? = null

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
| Foreign key (`Long?`) | `RELATION` with `@PortalRelation` + `@PortalLookup` |
| Entity collection | `RELATION_LIST` with `@PortalRelation` + `@PortalLookup` |
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

Use `@PortalLookup(dependsOn = "parentFieldName")`. The frontend will automatically pass the parent field's current value as a filter parameter when fetching options from the `/lookup` endpoint.

```kotlin
// Country (source)
@PortalLookup(labelField = "name", valueField = "id")
var countryId: Long? = null

// City (depends on country)
@PortalLookup(labelField = "name", valueField = "id", dependsOn = "countryId")
var cityId: Long? = null
```

### Recommended annotation order on a field

For readability and consistency:

```kotlin
@Column(...)             // JPA
@Enumerated(...)         // JPA (optional)
@Regex(...)              // Pattern validation
@PortalField(...)        // UI field declaration
@PortalRelation(...)     // Relation configuration (if applicable)
@PortalLookup(...)       // Lookup configuration (if applicable)
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
| `@PortalRelation` | Field | No | Configures RELATION/RELATION_LIST target entity, display options, per-row actions |
| `@PortalLookup` | Field/Function | No | Configures lookup label/value fields, filter query, cascading dependency, parentField |
| `@PortalDependency` | Field/Function | **Yes** | Conditional visibility, allowed values, and numeric range rules |
| `@PortalFormField` | Field | No | Describes a field in an action form model (use `@field:` target) |
| `@Regex` | Field | No | Attaches a regex pattern for frontend client-side validation |
| `RowColorProvider` | Interface (class) | — | Implement to control table row background colour |
