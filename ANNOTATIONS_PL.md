# Przewodnik po adnotacjach Quatrion Portal — wersja polska

> **Pakiet:** `dev.quatrion.portal.annotation`
> **Dotyczy:** Quarkus backend (Kotlin), framework Quatrion Portal
> **Plik referencyjny:** `backend/quatrion-portal-demo/src/.../demo/DemoEntities.kt`

---

## Spis treści

1. [Architektura — jak działają adnotacje](#1-architektura--jak-działają-adnotacje)
2. [@PortalEntity — rejestracja encji](#2-portalentity--rejestracja-encji)
3. [PortalTab — zakładki formularza](#3-portaltab--zakładki-formularza)
4. [@PortalField — pola UI](#4-portalfield--pola-ui)
   - [RendererType — typy rendererów](#rendererytype--typy-rendererów)
   - [FilterType — strategie filtrowania](#filtertype--strategie-filtrowania)
5. [@Regex — walidacja wzorcem](#5-regex--walidacja-wzorcem)
6. [@PortalRelation + @PortalLookup — relacje](#6-portalrelation--portallookup--relacje)
7. [@PortalDependency — reguły warunkowe](#7-portaldependency--reguły-warunkowe)
8. [@PortalAction + @PortalFormField — akcje niestandardowe](#8-portalaction--portalformfield--akcje-niestandardowe)
9. [@PortalSecurity — kontrola dostępu](#9-portalsecurity--kontrola-dostępu)
10. [Rejestracja encji w PortalModuleConfig](#10-rejestracja-encji-w-portalmoduleconfig)
11. [Kompletny przykład — encja Klient](#11-kompletny-przykład--encja-klient)
12. [Najczęstsze wzorce i FAQ](#12-najczęstsze-wzorce-i-faq)
13. [RowColor — kolorowanie wierszy tabeli](#13-rowcolor--kolorowanie-wierszy-tabeli)
14. [Konfiguracja portal.ui — application.properties](#14-konfiguracja-portalui--applicationproperties)
15. [Pełna lista endpointów REST API](#15-pełna-lista-endpointów-rest-api)
16. [Szybka referencja adnotacji](#16-szybka-referencja-adnotacji)

---

## 1. Architektura — jak działają adnotacje

```
Klasa JPA + adnotacje Portal
        │
        ▼
  MetadataService (startup)
        │  odczytuje adnotacje przez refleksję
        ▼
  JSON → /api/portal/metadata
        │
        ▼
  Frontend (Next.js)
        │  generuje dynamicznie: tabele, formularze, filtry, akcje
        ▼
  Gotowy interfejs CRUD — zero ręcznie pisanego React
```

**Zasada działania:**

1. Każda encja JPA oznaczona `@PortalEntity` jest rejestrowana w `PortalModuleConfig`.
2. Przy starcie backendu `MetadataService` skanuje wszystkie zarejestrowane klasy i buduje obiekt `PortalMetadata` (JSON).
3. Frontend pobiera JSON z `/api/portal/metadata` i dynamicznie renderuje cały interfejs.
4. Żadne komponenty React per-encja nie są potrzebne.

---

## 2. `@PortalEntity` — rejestracja encji

Adnotacja **klasy** — rejestruje encję JPA w portalu i konfiguruje jej wygląd w nawigacji bocznej.

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PortalEntity(
    val label: String,
    val labelKey: String = "",          // klucz i18n, np. "entity.customer"
    val module: String,
    val group: String = "",
    val groupKey: String = "",          // klucz i18n nagłówka grupy
    val icon: String = "table",
    val order: Int = 0,
    val description: String = "",
    val descriptionKey: String = "",    // klucz i18n opisu
    val tabs: KClass<out PortalTab> = NoTabs::class,
    val allowCreate: Boolean = true,
    val allowDelete: Boolean = true,
    val allowEdit: Boolean = true,
    val pageSize: Int = 25,
    val softDelete: Boolean = false,
    val auditLog: Boolean = false
)
```

> **`NoTabs`** — wewnętrzna klasa-sentinel frameworka (pakiet `dev.quatrion.portal.annotation`).
> Reprezentuje płaski formularz bez zakładek. Nie trzeba jej importować ręcznie.

### Parametry

| Parametr | Typ | Domyślna | Opis |
|---|---|---|---|
| `label` | `String` | — | Nazwa wyświetlana w pasku bocznym, nagłówkach i okruszkach chleba |
| `labelKey` | `String` | `""` | Klucz i18n dla `label`, np. `"entity.customer"`. Gdy niepuste, zastępuje `label` w UI |
| `module` | `String` | — | Nazwa modułu (musi odpowiadać `ModuleDef.name` w konfiguracji) |
| `group` | `String` | `""` | Opcjonalna grupa w nawigacji modułu — encje z tą samą grupą są zwinięte pod nagłówkiem |
| `groupKey` | `String` | `""` | Klucz i18n dla nagłówka grupy, np. `"group.catalog"` |
| `icon` | `String` | `"table"` | Nazwa ikony Lucide (np. `"users"`, `"package"`, `"shopping-cart"`) |
| `order` | `Int` | `0` | Pozycja sortowania wewnątrz grupy/modułu — niższe wartości pierwsze |
| `description` | `String` | `""` | Dłuższy opis pokazywany np. jako podtytuł strony |
| `descriptionKey` | `String` | `""` | Klucz i18n dla `description`, np. `"entity.customer.description"` |
| `tabs` | `KClass<out PortalTab>` | `NoTabs::class` | Enum implementujący `PortalTab` definiujący zakładki formularza |
| `allowCreate` | `Boolean` | `true` | Czy portal pokazuje przycisk "Utwórz" |
| `allowDelete` | `Boolean` | `true` | Czy portal pokazuje akcję "Usuń" |
| `allowEdit` | `Boolean` | `true` | Czy portal pokazuje przycisk "Edytuj" / inline edit |
| `pageSize` | `Int` | `25` | Domyślna liczba wierszy na stronę w tabeli |
| `softDelete` | `Boolean` | `false` | Gdy `true`, operacja usunięcia ustawia `deleted = true` zamiast fizycznie usuwać wiersz. Encja **musi** posiadać pole `deleted: Boolean = false` |
| `auditLog` | `Boolean` | `false` | Gdy `true`, operacje CRUD są zapisywane w logu audytu |

### Przykłady

**Prosta encja bez zakładek:**
```kotlin
@Entity
@Table(name = "country")
@PortalEntity(
    label = "Kraj",
    module = "CRM",
    icon = "globe",
    order = 1,
    description = "Słownik krajów"
)
class Country { ... }
```

**Encja z zakładkami, grupą i stronicowaniem:**
```kotlin
@Entity
@Table(name = "customer")
@PortalEntity(
    label = "Klient",
    module = "CRM",
    group = "Sprzedaż",
    icon = "users",
    order = 1,
    tabs = CustomerTab::class,
    pageSize = 50
)
class Customer { ... }
```

**Encja z soft-delete i logiem audytu:**
```kotlin
@Entity
@Table(name = "invoice")
@PortalEntity(
    label = "Faktura",
    module = "Finanse",
    icon = "file-text",
    softDelete = true,
    auditLog = true
)
class Invoice {
    // ...
    var deleted: Boolean = false  // WYMAGANE przy softDelete = true
}
```

**Encja tylko do odczytu (bez tworzenia i usuwania):**
```kotlin
@Entity
@Table(name = "audit_log")
@PortalEntity(
    label = "Log audytu",
    module = "System",
    icon = "list",
    allowCreate = false,
    allowEdit = false,
    allowDelete = false
)
class AuditLog { ... }
```

---

## 3. `PortalTab` — zakładki formularza

`PortalTab` to interfejs, który należy zaimplementować w klasie `enum`. Każda stała enum reprezentuje jedną zakładkę formularza. Zakładki rejestruje się przez parametr `tabs` adnotacji `@PortalEntity`.

```kotlin
interface PortalTab {
    val label: String
    val labelKey: String get() = ""     // klucz i18n dla etykiety zakładki
    val icon: String get() = ""
    val order: Int get() = 0
}
```

### Jak zdefiniować zakładki

```kotlin
enum class CustomerTab(
    override val label: String,
    override val icon: String,
    override val order: Int
) : PortalTab {
    BASIC("Podstawowe",  "user",         0),
    CONTACT("Kontakt",   "phone",         1),
    FINANCIAL("Finanse", "dollar-sign",   2),
    SYSTEM("System",     "settings",      3)
}
```

### Jak przypisać pola do zakładek

W `@PortalField` ustaw parametr `tab` na **nazwę stałej enum** (wielkimi literami):

```kotlin
@PortalField(label = "Imię i nazwisko", tab = "BASIC", order = 1, required = true)
var name: String = ""

@PortalField(label = "E-mail", tab = "CONTACT", order = 1, renderer = RendererType.EMAIL)
var email: String = ""
```

### Encja bez zakładek

Domyślna wartość `tabs = NoTabs::class` oznacza płaski, jednostronicowy formularz. Pola bez parametru `tab` są renderowane bezpośrednio.

---

## 4. `@PortalField` — pola UI

Adnotacja **pola lub funkcji** — deklaruje właściwość encji jako pole UI widoczne w tabeli, formularzu lub panelu filtrów.

```kotlin
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PortalField(
    val label: String,
    val labelKey: String = "",          // klucz i18n, np. "field.customer.name"
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
    val tooltipKey: String = "",        // klucz i18n dla tooltip
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

> **`selectEnum` — jak wyznaczane są wartości opcji:**
> Gdy ustawione, opcje SELECT/MULTI_SELECT są budowane z wywołania `.toString()` na każdej stałej enum.
> Jeśli enum **nie nadpisuje** `toString()`, wartością jest nazwa stałej (np. `"VIP"`).
> Jeśli enum nadpisuje `toString()` (np. `override fun toString() = label`), wartością jest wynik tej metody.
> **Wartość używana w `@PortalDependency.value` musi odpowiadać temu samemu wywołaniu `toString()`.**

### Parametry

| Parametr | Typ | Domyślna | Opis |
|---|---|---|---|
| `label` | `String` | — | Etykieta kolumny/pola w UI |
| `labelKey` | `String` | `""` | Klucz i18n dla `label`, np. `"field.customer.name"` |
| `tab` | `String` | `""` | Nazwa stałej enum `PortalTab` (np. `"BASIC"`) |
| `renderer` | `RendererType` | `AUTO` | Typ komponentu UI do renderowania wartości |
| `order` | `Int` | `0` | Pozycja sortowania w zakładce/grupie |
| `readonly` | `Boolean` | `false` | Pole zawsze tylko do odczytu, nawet w trybie edycji |
| `hidden` | `Boolean` | `false` | Pole ukryte zarówno w tabeli jak i formularzu (np. pola systemowe) |
| `showInTable` | `Boolean` | `true` | Czy pole jest kolumną w tabeli encji |
| `showInFilter` | `Boolean` | `true` | Czy pole pojawia się w panelu filtrów |
| `required` | `Boolean` | `false` | Walidacja: pole obowiązkowe przed zapisem |
| `placeholder` | `String` | `""` | Tekst placeholder w pustym polu input |
| `tooltip` | `String` | `""` | Krótka pomoc kontekstowa wyświetlana przy polu |
| `tooltipKey` | `String` | `""` | Klucz i18n dla `tooltip`, np. `"tooltip.customer.email"` |
| `width` | `Int` | `0` | Preferowana szerokość kolumny w pikselach (`0` = automatyczna) |
| `group` | `String` | `""` | Grupuje pola wewnątrz zakładki wizualnie |
| `displayExpression` | `String` | `""` | Wyrażenie szablonowe `${fieldName}` do obliczania wartości wyświetlanych |
| `filterType` | `FilterType` | `AUTO` | Strategia filtrowania w panelu filtrów |
| `selectOptions` | `Array<String>` | `[]` | Lista opcji dla `SELECT`/`MULTI_SELECT` (gdy `selectEnum` nie jest ustawione) |
| `selectEnum` | `KClass<*>` | `Unit::class` | Enum, którego stałe definiują opcje — wartością jest `toString()` każdej stałej |
| `min` | `Double` | `NaN` | Minimalna wartość dla pól `NUMBER`/`DECIMAL` |
| `max` | `Double` | `NaN` | Maksymalna wartość dla pól `NUMBER`/`DECIMAL` |
| `defaultValue` | `String` | `""` | Domyślna wartość przy tworzeniu nowego rekordu |

### `RendererType` — typy rendererów

| Wartość | Opis | Uwagi |
|---|---|---|
| `AUTO` | Framework sam dobiera renderer na podstawie typu JPA/Kotlin | Domyślna wartość |
| `TEXT` | Jednoliniowe pole tekstowe | — |
| `TEXTAREA` | Wieloliniowe pole tekstowe | Ustaw `@Column(columnDefinition = "TEXT")` |
| `NUMBER` | Pole liczbowe całkowite | Typ Kotlin: `Int`, `Long` |
| `DECIMAL` | Pole liczbowe zmiennoprzecinkowe | Typ Kotlin: `Double`, `BigDecimal` |
| `DATE` | Picker daty (ISO-8601 `YYYY-MM-DD`) | — |
| `DATETIME` | Picker daty i czasu (ISO-8601 `YYYY-MM-DDTHH:mm`) | — |
| `BOOLEAN` | Checkbox / toggle | Typ Kotlin: `Boolean` |
| `SELECT` | Dropdown z jedną wartością | Wymaga `selectOptions` lub `selectEnum` |
| `MULTI_SELECT` | Dropdown z wieloma wartościami | Wartości oddzielone przecinkiem w bazie |
| `RELATION` | Picker relacji ManyToOne / OneToOne | Wymaga `@PortalRelation` + `@PortalLookup` |
| `RELATION_LIST` | Lista inline OneToMany / ManyToMany | Wymaga `@PortalRelation` + `@PortalLookup` |
| `PASSWORD` | Pole hasła (wartość maskowana) | Nie pojawia się w tabeli |
| `EMAIL` | Pole e-mail z walidacją formatu | — |
| `URL` | Pole URL z walidacją formatu | — |
| `COLOR` | Picker koloru (zapis hex, np. `#FF5733`) | Długość kolumny: `length = 7` |
| `FILE` | Pole wgrywania pliku/obrazu | Zapis ścieżki lub base64 |
| `JSON` | Edytor surowego JSON | Ustaw `@Column(columnDefinition = "TEXT")` |
| `CUSTOM` | Niestandardowy renderer zarejestrowany we frontendzie | — |

### `FilterType` — strategie filtrowania

| Wartość | Opis | Przykład SQL |
|---|---|---|
| `AUTO` | Strategia dobierana z typu pola | — |
| `EXACT` | Dopasowanie równości | `field = :value` |
| `CONTAINS` | Wyszukiwanie podciągu (bez uwzględnienia wielkości liter) | `LOWER(field) LIKE %value%` |
| `STARTS_WITH` | Wyszukiwanie prefiksem | `LOWER(field) LIKE value%` |
| `RANGE` | Zakres numeryczny lub datowy | `field BETWEEN :from AND :to` |
| `IN` | Przynależność do zbioru wartości | `field IN (:values)` |
| `BOOLEAN` | Równość boole'owska | `field = true/false` |
| `NONE` | Pole niefiltrowalne | — |

### Przykłady pól

**Pole tekstowe z walidacją:**
```kotlin
@Column(length = 100, nullable = false)
@PortalField(
    label = "Imię i nazwisko",
    tab = "BASIC",
    order = 1,
    required = true,
    renderer = RendererType.TEXT,
    filterType = FilterType.CONTAINS,
    placeholder = "Wpisz imię i nazwisko"
)
var name: String = ""
```

**Pole SELECT z enumem:**
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

**Pole SELECT z listą opcji (bez enuma):**
```kotlin
@Column(length = 20)
@PortalField(
    label = "Priorytet",
    order = 3,
    renderer = RendererType.SELECT,
    filterType = FilterType.IN,
    selectOptions = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]
)
var priority: String = ""
```

**Pole MULTI_SELECT:**
```kotlin
enum class Tag { VIP, NEW, PREMIUM, BUSINESS }

@Column
@PortalField(
    label = "Tagi",
    order = 4,
    renderer = RendererType.MULTI_SELECT,
    filterType = FilterType.IN,
    showInTable = false,
    tooltip = "Wartości oddzielone przecinkiem",
    selectEnum = Tag::class
)
var tags: String = ""
```

**Pole DECIMAL z ograniczeniem zakresu:**
```kotlin
@Column
@PortalField(
    label = "Cena",
    order = 5,
    renderer = RendererType.DECIMAL,
    filterType = FilterType.RANGE,
    min = 0.0,
    max = 99999.99,
    placeholder = "0.00"
)
var price: Double = 0.0
```

**Pole DATE:**
```kotlin
@Column
@PortalField(
    label = "Data urodzenia",
    order = 6,
    renderer = RendererType.DATE,
    filterType = FilterType.RANGE,
    showInTable = false,
    tooltip = "Format: YYYY-MM-DD"
)
var birthDate: String = ""
```

**Pole tylko do odczytu (ID):**
```kotlin
@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
@PortalField(label = "ID", order = 0, readonly = true, showInFilter = false)
var id: Long = 0
```

**Pole ukryte:**
```kotlin
@Column
@PortalField(label = "Wewnętrzny token", hidden = true)
var internalToken: String = ""
```

**Pole z wyrażeniem displayExpression:**
```kotlin
@PortalField(
    label = "Pełna nazwa",
    order = 7,
    displayExpression = "\${firstName} \${lastName}",
    showInTable = true,
    readonly = true
)
var fullName: String = ""
```

**Pole z domyślną wartością:**
```kotlin
@Column
@PortalField(
    label = "Aktywny",
    order = 8,
    renderer = RendererType.BOOLEAN,
    defaultValue = "true"
)
var isActive: Boolean = true
```

---

## 5. `@Regex` — walidacja wzorcem

Adnotacja **pola** — dołącza wyrażenie regularne propagowane do frontendu jako walidacja po stronie klienta.

```kotlin
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Regex(
    val pattern: String,
    val message: String = "Wartość nie spełnia wymaganego formatu"
)
```

### Parametry

| Parametr | Typ | Opis |
|---|---|---|
| `pattern` | `String` | Wyrażenie regularne, które wartość musi spełnić |
| `message` | `String` | Komunikat błędu wyświetlany gdy wartość nie pasuje do wzorca |

### Przykłady

**Numer telefonu:**
```kotlin
@Column(length = 20)
@Regex(
    pattern = """^\+?[\d\s\-]{7,20}$""",
    message = "Numer telefonu może zawierać cyfry, spacje, myślniki i opcjonalny znak +"
)
@PortalField(
    label = "Telefon",
    order = 2,
    renderer = RendererType.TEXT,
    placeholder = "+48 123 456 789"
)
var phone: String = ""
```

**Kod ISO kraju:**
```kotlin
@Column(length = 3)
@Regex(
    pattern = """^[A-Za-z]{2,3}$""",
    message = "Kod ISO musi zawierać 2 lub 3 litery"
)
@PortalField(label = "Kod ISO", order = 2, required = true, placeholder = "np. PL")
var isoCode: String = ""
```

**NIP:**
```kotlin
@Column(length = 10)
@Regex(
    pattern = """^\d{10}$""",
    message = "NIP musi składać się z 10 cyfr"
)
@PortalField(label = "NIP", order = 3, required = true, renderer = RendererType.TEXT)
var nip: String = ""
```

> **Uwaga:** `@Regex` działa wyłącznie jako walidacja po stronie frontendu. Nie zastępuje walidacji backendowej — dodaj ją osobno (np. przez Bean Validation `@Pattern`).

---

## 6. `@PortalRelation` + `@PortalLookup` — relacje

---

### Jak to działa — przepływ danych

Obie adnotacje **muszą być umieszczone razem** na tym samym polu. Przy starcie serwera `MetadataService` łączy je w jeden obiekt `RelationMetadata`, który jest wysyłany do frontendu jako część metadanych pola.

```
Encja JPA
  @PortalField(renderer = RELATION)   ← mówi frontendowi "renderuj picker"
  @PortalRelation(targetEntity = ...)  ← mówi jak wyświetlać i jaką encję powiązać
  @PortalLookup(labelField = ...)      ← mówi jak wywołać endpoint /lookup
  var countryId: Long? = null
         │
         ▼ MetadataService (startup)
         │
  RelationMetadata {
    targetEntity  = "Country"          ← prosta nazwa klasy
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
  Tabela:  RelationCell  →  GET /api/portal/data/Country/{id}
                             wyświetla wartość pola "name" wybranego rekordu
         │
  Formularz: RelationRenderer  →  GET /api/portal/data/Country/lookup?q=pol&labelField=name&valueField=id
                                   dropdown z wynikami wyszukiwania
```

---

### Dwa tryby renderowania

| Renderer | Kiedy używać | Pole w encji |
|---|---|---|
| `RendererType.RELATION` | ManyToOne, OneToOne — przechowujesz **jeden** klucz obcy | `var xyzId: Long? = null` |
| `RendererType.RELATION_LIST` | OneToMany, ManyToMany — lista powiązanych encji | `@Transient var items: List<Entity>? = null` |

> **Ważne dla `RELATION_LIST`:** Pole musi być oznaczone `@Transient` — nie jest kolumną bazodanową. Służy wyłącznie do przekazania metadanych do frontendu. Backend dynamicznie ładuje powiązane rekordy na podstawie `RelationMetadata`.

---

### `@PortalRelation` — szczegółowy opis parametrów

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

Klasa docelowej encji JPA. Gdy ustawiona (inaczej niż `Unit::class`), framework używa jej prostej nazwy (`simpleName`) jako identyfikatora encji przy wywołaniach endpointów.

**Kiedy można pominąć?** Framework próbuje sam wywnioskować docelową encję:
- Dla kolekcji (`List<T>`) — z argumentu generycznego `T`
- Dla referencji (`var xyzId: Long?`) — z nazwy pola (konwencja `xyzId` → `Xyz`)

W praktyce zawsze lepiej jawnie podać `targetEntity`, aby uniknąć niejednoznaczności.

```kotlin
// ✅ Jawnie podana docelowa encja — zalecane
@PortalRelation(targetEntity = DemoCountry::class, ...)

// ⚠️ Bez targetEntity — framework próbuje wywnioskować z List<DemoOrderItem>
@PortalRelation(displayFields = ["productId", "quantity"])
var items: List<DemoOrderItem>? = null
```

---

#### `displayFields: Array<String> = []`

Pola docelowej encji wyświetlane jako **kolumny w tabeli** w trybie `RELATION_LIST`, lub jako **dodatkowe info** w pickerze `RELATION`.

```kotlin
// Picker pokaże "Marek Kowalski (mk@example.com)"
@PortalRelation(
    targetEntity = DemoCustomer::class,
    displayFields = ["name", "email"],  // obie kolumny w liście
    searchFields = ["name", "email"]
)
var customerId: Long? = null
```

Gdy `displayFields = []` (domyślnie), frontend sam dobierze widoczne kolumny na podstawie `showInTable` z metadanych docelowej encji.

---

#### `searchFields: Array<String> = []`

Pola docelowej encji przeszukiwane gdy użytkownik **wpisuje tekst** w pickerze. Backend wykonuje zapytanie:
```sql
LOWER(CAST(e.{searchField} AS string)) LIKE %fraza%
```

Podaj te pola, po których wyszukiwanie ma sens (typowo `name`, `code`, `email`). Nie wpływa na kolumny w tabeli — to robi `displayFields`.

```kotlin
@PortalRelation(
    targetEntity = DemoProduct::class,
    displayFields = ["name", "sku"],   // widoczne kolumny
    searchFields = ["name", "sku"]     // pola do wyszukiwania po wpisaniu tekstu
)
```

---

#### `editable: Boolean = true`

Gdy `false` — picker jest zablokowany (tylko do odczytu w formularzu). Przydatne np. dla pola `orderId` w pozycji zamówienia — ID zamówienia nie powinno być zmieniane z poziomu dziecka.

```kotlin
// Zamówienie - pole tylko do odczytu (nadrzędne względem pozycji)
@PortalRelation(
    targetEntity = DemoOrder::class,
    editable = false,          // blokuje picker
    displayFields = ["orderNumber"],
    searchFields = ["orderNumber"]
)
var orderId: Long? = null
```

---

#### `inlineEdit: Boolean = false`

Tylko dla `RELATION_LIST`. Gdy `true`, rekordy powiązane można edytować bezpośrednio w tabeli wewnątrz formularza rodzica, bez otwierania osobnego modalu.

```kotlin
@PortalRelation(
    targetEntity = DemoOrderItem::class,
    editable = true,
    inlineEdit = true,         // edycja bezpośrednio w tabeli pozycji
    displayFields = ["productId", "quantity", "unitPrice"],
    maxItems = 100
)
var items: List<DemoOrderItem>? = null
```

---

#### `createAllowed: Boolean = false`

Gdy `true`, picker pokazuje opcję **"Utwórz nowy"**. Użytkownik może otworzyć formularz tworzenia docelowej encji bezpośrednio z poziomu pickera, bez przechodzenia do oddzielnej strony.

```kotlin
@PortalRelation(
    targetEntity = DemoSupplier::class,
    displayFields = ["name"],
    searchFields = ["name"],
    createAllowed = true       // "Dodaj nowego dostawcę" w pickerze
)
var supplierId: Long? = null
```

---

#### `cascadeDelete: Boolean = false`

Wyłącznie informacyjny — nie konfiguruje rzeczywistego kaskadowania. Gdy `true`, frontend może wyświetlić ostrzeżenie przy usuwaniu rodzica. **Kaskadę JPA należy skonfigurować osobno** w mapowaniu JPA (`cascade = CascadeType.REMOVE`).

---

#### `orderBy: String = ""`

Fragment HQL `ORDER BY` (bez słowa kluczowego `ORDER BY`) stosowany przy ładowaniu listy relacji. Alias encji to `e`.

```kotlin
@PortalRelation(
    targetEntity = DemoCategory::class,
    displayFields = ["name"],
    orderBy = "name ASC"       // lista kategorii posortowana alfabetycznie
)
var categoryId: Long? = null
```

Gdy puste, backend sortuje po `labelField` (z `@PortalLookup`) rosnąco.

---

#### `maxItems: Int = 0`

Maksymalna liczba elementów w `RELATION_LIST`. Gdy `0` (domyślnie) — bez ograniczeń. Frontend wyświetla ostrzeżenie gdy limit jest osiągnięty.

---

### `@PortalLookup` — szczegółowy opis parametrów

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

Pole docelowej encji wyświetlane jako **czytelna etykieta** w pickerze i w komórce tabeli.

- W tabeli: `RelationCell` pobiera rekord przez `GET /api/portal/data/{targetEntity}/{id}` i wyświetla `record[labelField]`
- W pickerze: etykieta każdej opcji w dropdownie

```kotlin
// Tabela pokaże wartość pola "name" kraju, np. "Polska"
@PortalLookup(labelField = "name", valueField = "id")

// Tabela pokaże wartość pola "orderNumber", np. "ORD-2024-001"
@PortalLookup(labelField = "orderNumber", valueField = "id")
```

---

#### `valueField: String = "id"`

Pole docelowej encji, którego **wartość jest przechowywana** w kolumnie encji rodzica (klucz obcy). Domyślnie `"id"` — zazwyczaj nie trzeba zmieniać, chyba że relacja jest po innym polu unikalnym.

```kotlin
// FK przechowuje wartość pola "code" zamiast "id"
@PortalLookup(labelField = "name", valueField = "code")
var countryCode: String? = null
```

---

#### `filterQuery: String = ""`

Dodatkowy fragment HQL `WHERE` **stale zawężający** listę wyników lookup. Alias encji to **`e`** (bez prefiksu `WHERE`). Stosowany niezależnie od wpisanej przez użytkownika frazy wyszukiwania.

```
Wewnętrzny HQL zapytania:
  FROM Country e
  WHERE LOWER(CAST(e.name AS string)) LIKE :q  ← z frazy użytkownika
  AND e.isActive = true                         ← z filterQuery
  ORDER BY e.name
```

```kotlin
// Tylko aktywne kategorie
@PortalLookup(filterQuery = "e.isActive = true")

// Tylko kraje z kontynentu europejskiego
@PortalLookup(filterQuery = "e.continent = 'EUROPE'")

// Tylko produkty na stanie
@PortalLookup(filterQuery = "e.quantity > 0")

// Wielokrotne warunki (AND)
@PortalLookup(filterQuery = "e.isActive = true AND e.isVerified = true")
```

> **Ważne:** Alias encji w `filterQuery` musi być zawsze `e`. Nie dodawaj `WHERE` na początku.

---

#### `dependsOn: String = ""`

Nazwa **innego pola tego samego formularza**, którego bieżąca wartość jest automatycznie przekazywana jako filtr do endpointu `/lookup`. Umożliwia tworzenie **kaskadowych dropdownów** — np. wybór kraju ogranicza listę dostępnych miast.

**Jak to działa technicznie:**

1. Użytkownik wybiera wartość w polu `countryId` (np. `42`)
2. Frontend re-wywołuje `/api/portal/data/City/lookup?dependsOnField=countryId&dependsOnValue=42`
3. Backend dodaje do HQL: `AND e.countryId = :depVal`
4. Tylko miasta przypisane do kraju o `id = 42` trafiają do dropdownu

```kotlin
// Pole źródłowe (kraj)
@Column
@PortalField(label = "Kraj", order = 5, renderer = RendererType.RELATION)
@PortalRelation(targetEntity = DemoCountry::class, searchFields = ["name"])
@PortalLookup(labelField = "name", valueField = "id")
var countryId: Long? = null

// Pole zależne (miasto filtrowane po wybranym kraju)
@Column
@PortalField(label = "Miasto", order = 6, renderer = RendererType.RELATION)
@PortalRelation(targetEntity = City::class, searchFields = ["name"])
@PortalLookup(
    labelField = "name",
    valueField = "id",
    dependsOn = "countryId"    // nazwa pola na BIEŻĄCYM formularzu (nie tabeli!)
)
var cityId: Long? = null
```

> **Uwaga:** Pole docelowej encji filtrowanej (np. `City.countryId`) musi mieć taką samą nazwę jak pole wskazywane przez `dependsOn`. Backend generuje: `e.{dependsOn} = :depVal`.

---

#### `maxResults: Int = 100`

Maksymalna liczba opcji zwracanych z endpointu `/lookup` w jednym żądaniu. Zmniejsz dla bardzo dużych tabel, zwiększ gdy użytkownicy potrzebują szerszego wyboru bez wpisywania.

```kotlin
// Mała tabela słownikowa — pokaż wszystkie opcje od razu
@PortalLookup(labelField = "name", valueField = "id", maxResults = 500)

// Duża tabela klientów — ogranicz wyniki podpowiedzi
@PortalLookup(labelField = "name", valueField = "id", maxResults = 20)
```

---

### Endpoint `/lookup` — jak go wywołuje frontend

```
GET /api/portal/data/{targetEntity}/lookup
  ?q={fraza_wyszukiwania}
  &labelField={labelField}
  &valueField={valueField}
  &filterQuery={filterQuery}
  &dependsOnField={dependsOn}
  &dependsOnValue={wartość_pola_zależnego}
  &orderBy={orderBy}
  &max={maxResults}
```

Zwraca listę `LookupOption`:
```json
[
  { "value": 1, "label": "Polska" },
  { "value": 2, "label": "Niemcy" },
  { "value": 3, "label": "Francja" }
]
```

---

### Kompletna tabela parametrów — zestawienie

#### `@PortalRelation`

| Parametr | Typ | Domyślna | Opis |
|---|---|---|---|
| `targetEntity` | `KClass<*>` | `Unit::class` | Klasa docelowej encji JPA. Jawne podanie eliminuje niejednoznaczności |
| `editable` | `Boolean` | `true` | Czy pole relacji można modyfikować w formularzu |
| `inlineEdit` | `Boolean` | `false` | Tylko `RELATION_LIST`: edycja elementów bezpośrednio w tabeli w formularzu rodzica |
| `displayFields` | `Array<String>` | `[]` | Kolumny wyświetlane w tabeli `RELATION_LIST` lub dodatkowe info w pickerze |
| `searchFields` | `Array<String>` | `[]` | Pola przeszukiwane gdy użytkownik wpisuje tekst w pickerze |
| `createAllowed` | `Boolean` | `false` | Picker pokazuje opcję "Utwórz nowy" |
| `cascadeDelete` | `Boolean` | `false` | Informacyjny: czy usuwanie rodzica kaskaduje na dzieci |
| `orderBy` | `String` | `""` | Fragment HQL `ORDER BY` (bez słowa kluczowego), alias `e` |
| `maxItems` | `Int` | `0` | Limit elementów w `RELATION_LIST` (0 = bez limitu) |
| `downloadAction` | `String` | `""` | Nazwa `@PortalAction` na encji docelowej wywołującej pobieranie pliku. Gdy niepuste, renderowany jest przycisk ikony pobierania przy każdym wierszu `RELATION_LIST` |
| `actions` | `Array<RelationRowAction>` | `[]` | Przyciski akcji per-wiersz w tabeli `RELATION_LIST` (patrz `RelationRowAction`) |

#### `RelationRowAction` — predefiniowane akcje per-wiersz

```kotlin
enum class RelationRowAction(val actionName: String) {
    DOWNLOAD("download")  // wywołuje akcję "download" na encji docelowej
}
```

| Wartość | Nazwa akcji | Opis |
|---|---|---|
| `DOWNLOAD` | `"download"` | Wywołuje akcję `@PortalAction(name = "download")` na encji docelowej i wyzwala pobieranie pliku w przeglądarce |

**Przykład — lista plików z przyciskiem pobierania:**
```kotlin
@Transient
@PortalField(label = "Pliki", renderer = RendererType.RELATION_LIST, showInFilter = false, showInTable = false)
@PortalRelation(
    targetEntity = TaskRunFile::class,
    editable = false,
    displayFields = ["fileName", "fileSize"],
    actions = [RelationRowAction.DOWNLOAD]   // przycisk pobierania przy każdym pliku
)
@PortalLookup(labelField = "fileName", valueField = "id", parentField = "taskRunId")
var files: List<TaskRunFile>? = null
```

#### `@PortalLookup`

| Parametr | Typ | Domyślna | Opis |
|---|---|---|---|
| `labelField` | `String` | `"name"` | Pole docelowej encji wyświetlane jako etykieta w pickerze i tabeli |
| `valueField` | `String` | `"id"` | Pole docelowej encji przechowywane jako wartość (klucz obcy) |
| `filterQuery` | `String` | `""` | Stały filtr HQL WHERE (alias `e.`), np. `"e.isActive = true"` |
| `dependsOn` | `String` | `""` | Nazwa pola tego samego formularza — kaskadowy dropdown |
| `maxResults` | `Int` | `100` | Maks. liczba opcji zwracanych przez `/lookup` |
| `parentField` | `String` | `""` | Tylko `RELATION_LIST`: nazwa pola w encji **docelowej**, które przechowuje FK do rodzica (np. `"memberId"` w `Loan` gdy lista jest na `Member`). Gdy ustawione, frontend automatycznie pobiera powiązane rekordy przez `GET /api/portal/data/{targetEntity}?filter[parentField][eq]={parentId}` zamiast polegać na odpowiedzi `getById` |

---

### Różnice: `displayFields` vs `searchFields` vs `labelField`

| Właściwość | Adnotacja | Co robi |
|---|---|---|
| `labelField` | `@PortalLookup` | Pole pokazywane jako etykieta w komórce tabeli i opcji dropdownu |
| `displayFields` | `@PortalRelation` | Kolumny wyświetlane w tabeli `RELATION_LIST` / dodatkowe info obok etykiety |
| `searchFields` | `@PortalRelation` | Pola, po których działa wyszukiwanie po wpisaniu tekstu w pickerze |

Typowy wzorzec — wszystkie trzy mogą być różne:
```kotlin
@PortalRelation(
    targetEntity = DemoCustomer::class,
    displayFields = ["name", "email", "phone"],   // 3 kolumny w liście relacji
    searchFields  = ["name", "email"]             // szukaj po imieniu i emailu
)
@PortalLookup(
    labelField = "name",   // w komórce tabeli pokaż tylko imię
    valueField = "id"
)
var customerId: Long? = null
```

---

### Kompletne przykłady ze scenariuszami

**1. Prosta relacja ManyToOne (kraj klienta):**
```kotlin
@Column
@PortalField(
    label = "Kraj",
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

**2. Relacja tylko do odczytu (orderId w pozycji zamówienia):**
```kotlin
@Column
@PortalField(
    label = "Zamówienie",
    order = 1,
    renderer = RendererType.RELATION,
    filterType = FilterType.EXACT
)
@PortalRelation(
    targetEntity = DemoOrder::class,
    editable = false,                              // zablokowany picker
    displayFields = ["orderNumber"],
    searchFields = ["orderNumber"]
)
@PortalLookup(labelField = "orderNumber", valueField = "id")
var orderId: Long? = null
```

**3. Relacja z filtrowaniem (tylko aktywne kategorie):**
```kotlin
@Column
@PortalField(label = "Kategoria", order = 3, renderer = RendererType.RELATION)
@PortalRelation(
    targetEntity = DemoCategory::class,
    displayFields = ["name"],
    searchFields = ["name"]
)
@PortalLookup(
    labelField = "name",
    valueField = "id",
    filterQuery = "e.isActive = true"             // stały filtr HQL
)
var categoryId: Long? = null
```

**4. Kaskadowe dropdowny (kraj → kategoria powiązana z krajem):**
```kotlin
// Pole źródłowe
@Column
@PortalField(label = "Kraj", order = 5, renderer = RendererType.RELATION)
@PortalRelation(targetEntity = DemoCountry::class, searchFields = ["name"])
@PortalLookup(labelField = "name", valueField = "id")
var countryId: Long? = null

// Pole zależne — filtrowane po wartości countryId
@Column
@PortalField(label = "Region", order = 6, renderer = RendererType.RELATION)
@PortalRelation(targetEntity = Region::class, searchFields = ["name"])
@PortalLookup(
    labelField = "name",
    valueField = "id",
    dependsOn = "countryId"   // gdy countryId = 42, backend filtruje: e.countryId = 42
)
var regionId: Long? = null
```

**5. `RELATION_LIST` tylko do odczytu (zamówienia klienta):**
```kotlin
@Transient
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
    targetEntity = DemoOrder::class,
    editable = false,                              // lista tylko do odczytu
    displayFields = ["orderNumber", "orderDate", "totalAmount", "status"],
    searchFields = ["orderNumber"]
)
@PortalLookup(labelField = "orderNumber", valueField = "id")
var orders: List<DemoOrder>? = null
```

**6. `RELATION_LIST` edytowalny inline z limitem (pozycje zamówienia):**
```kotlin
@Transient
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
    targetEntity = DemoOrderItem::class,
    editable = true,
    inlineEdit = true,                             // edycja bezpośrednio w tabeli
    displayFields = ["productId", "quantity", "unitPrice"],
    maxItems = 100,                                // max 100 pozycji
    orderBy = "id ASC"
)
@PortalLookup(labelField = "productId", valueField = "id")
var items: List<DemoOrderItem>? = null
```

**7. Relacja z tworzeniem nowego rekordu w locie:**
```kotlin
@Column
@PortalField(label = "Dostawca", order = 5, renderer = RendererType.RELATION)
@PortalRelation(
    targetEntity = DemoSupplier::class,
    displayFields = ["name"],
    searchFields = ["name"],
    createAllowed = true                          // "Dodaj nowego dostawcę" w pickerze
)
@PortalLookup(labelField = "name", valueField = "id")
var supplierId: Long? = null
```

**8. Relacja z niestandardowym kluczem (nie `id`):**
```kotlin
// Relacja po kodzie ISO zamiast numerycznego id
@Column(length = 3)
@PortalField(label = "Kraj (kod)", order = 4, renderer = RendererType.RELATION)
@PortalRelation(
    targetEntity = DemoCountry::class,
    displayFields = ["name"],
    searchFields = ["name", "code"]
)
@PortalLookup(
    labelField = "name",
    valueField = "code"                           // zapisuje kod ISO, nie id
)
var countryCode: String? = null
```

**9. Samoreferencja (kategoria nadrzędna):**
```kotlin
@Column
@PortalField(
    label = "Kategoria nadrzędna",
    order = 5,
    renderer = RendererType.RELATION,
    filterType = FilterType.EXACT,
    showInTable = false
)
@PortalRelation(
    targetEntity = DemoCategory::class,           // ta sama klasa!
    editable = true,
    displayFields = ["name"],
    searchFields = ["name"]
)
@PortalLookup(labelField = "name", valueField = "id")
var parentId: Long? = null
```

---

### Wymagana kolejność adnotacji na polu

```kotlin
@Column(...)                    // 1. JPA
@PortalField(                   // 2. Deklaracja pola UI
    renderer = RendererType.RELATION,
    ...
)
@PortalRelation(                // 3. Konfiguracja relacji
    targetEntity = ...,
    ...
)
@PortalLookup(                  // 4. Konfiguracja lookup
    labelField = "name",
    valueField = "id"
)
var xyzId: Long? = null
```

---

### Najczęstsze błędy

| Błąd | Skutek | Rozwiązanie |
|---|---|---|
| Brak `@PortalLookup` na polu z `RELATION` | Frontend nie zna `labelField`/`valueField`, używa domyślnych `name`/`id` | Zawsze dodaj `@PortalLookup` |
| `RELATION_LIST` bez `@Transient` | Hibernate próbuje mapować kolekcję jako kolumnę — błąd startu | Dodaj `@Transient` |
| `filterQuery` z aliasem innym niż `e` | Błąd HQL w runtime | Zawsze używaj `e.nazwaPolaDocelowego` |
| `dependsOn` wskazuje na nieistniejące pole | Filtr kaskadowy nie działa, brak błędu | Sprawdź dokładną nazwę pola (case-sensitive) |
| `showInFilter = true` na `RELATION_LIST` | Nie ma sensu — listy relacji nie filtrujemy | Ustaw `showInFilter = false` |
| Brak `targetEntity` przy niejednoznacznej kolekcji | Framework może wywnioskować złą klasę | Zawsze jawnie podaj `targetEntity` |

---

## 7. `@PortalDependency` — reguły warunkowe

Adnotacja **pola** (powtarzalna) — definiuje warunkowe reguły kontrolujące widoczność, dostępne opcje i zakres numeryczny pola w zależności od wartości innych pól formularza.

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

### Parametry

| Parametr | Typ | Domyślna | Opis |
|---|---|---|---|
| `field` | `String` | `""` | Nazwa pola, od którego zależy ta reguła |
| `operator` | `DependencyOperator` | `UNSPECIFIED` | Operator porównania dla prostego warunku liściowego |
| `value` | `String` | `""` | Wartość do porównania (dla jednej wartości) |
| `values` | `Array<String>` | `[]` | Zbiór wartości dla operatorów `IN` / `NOT_IN` |
| `condition` | `String` | `""` | Złożony warunek jako JSON AST (dla logiki `allOf`/`anyOf`/`not`) |
| `visibility` | `DependencyVisibility` | `NONE` | Efekt widoczności: `SHOW`, `HIDE`, `NONE` |
| `allowedValues` | `Array<String>` | `[]` | Gdy podane — ogranicza dostępne opcje pola SELECT do tej listy |
| `min` | `String` | `""` | Minimalna wartość numeryczna; liczba (`"10"`) lub referencja do pola (`"$creditLimit"`) |
| `max` | `String` | `""` | Maksymalna wartość numeryczna; liczba lub referencja do pola |
| `message` | `String` | `""` | Komunikat pokazywany użytkownikowi gdy reguła jest aktywna |
| `clearOnHide` | `Boolean` | `true` | Czy wyczyścić wartość pola gdy zostaje ukryte |

### `DependencyVisibility`

| Wartość | Opis |
|---|---|
| `NONE` | Reguła nie wpływa na widoczność — tylko ogranicza wartości lub zakres |
| `SHOW` | Pole jest widoczne **tylko** gdy warunek jest spełniony |
| `HIDE` | Pole jest ukryte gdy warunek jest spełniony |

### `DependencyOperator`

| Wartość | Wire value | Opis |
|---|---|---|
| `UNSPECIFIED` | `""` | Wartość domyślna — brak operatora liściowego. Używana gdy warunek jest podany jako JSON w `condition` |
| `EQ` | `"eq"` | Równość |
| `NEQ` | `"neq"` | Nierówność |
| `IN` | `"in"` | Wartość w zbiorze |
| `NOT_IN` | `"notIn"` | Wartość poza zbiorem |
| `CONTAINS` | `"contains"` | Zawiera podciąg |
| `NOT_CONTAINS` | `"notContains"` | Nie zawiera podciągu |
| `IS_EMPTY` | `"isEmpty"` | Wartość jest pusta |
| `IS_NOT_EMPTY` | `"isNotEmpty"` | Wartość nie jest pusta |
| `GT` | `"gt"` | Większe niż |
| `GTE` | `"gte"` | Większe lub równe |
| `LT` | `"lt"` | Mniejsze niż |
| `LTE` | `"lte"` | Mniejsze lub równe |

> **`UNSPECIFIED`** — gdy używasz parametru `condition` (JSON AST) zamiast `field`/`operator`/`value`,
> pozostaw `operator` na domyślnym `UNSPECIFIED`. Framework wykrywa ten tryb i nie stosuje żadnego
> operatora liściowego — parsuje warunek z JSON.

### Przykłady

**Widoczność warunkowa (SHOW):**
```kotlin
// Pole "Rabat VIP" widoczne tylko dla klientów VIP
@Column
@PortalField(label = "Rabat VIP (%)", order = 5, renderer = RendererType.DECIMAL)
@PortalDependency(
    field = "customerType",
    operator = DependencyOperator.EQ,
    value = "VIP",
    visibility = DependencyVisibility.SHOW,
    message = "Rabat VIP dostępny tylko dla klientów VIP"
)
var vipDiscount: Double = 0.0
```

**Widoczność warunkowa (HIDE):**
```kotlin
// Pole "Powód anulowania" ukryte dopóki status != "CANCELLED"
@Column
@PortalField(label = "Powód anulowania", order = 8, renderer = RendererType.TEXTAREA)
@PortalDependency(
    field = "status",
    operator = DependencyOperator.NEQ,
    value = "CANCELLED",
    visibility = DependencyVisibility.HIDE
)
var cancellationReason: String = ""
```

**Ograniczenie dostępnych opcji (allowedValues):**
```kotlin
// Dla nowego klienta tylko tag NEW jest dozwolony
@Column
@PortalField(label = "Tagi", order = 4, renderer = RendererType.MULTI_SELECT, selectEnum = Tag::class)
@PortalDependency(
    field = "customerType",
    operator = DependencyOperator.EQ,
    value = "New",
    allowedValues = ["NEW"],
    message = "Nowy klient może mieć tylko tag NEW"
)
@PortalDependency(
    field = "customerType",
    operator = DependencyOperator.EQ,
    value = "Premium",
    allowedValues = ["PREMIUM", "REGULAR", "NEW"]
)
var tags: String = ""
```

**Ograniczenie zakresu numerycznego:**
```kotlin
// Limit kredytowy zależny od typu klienta
@Column
@PortalField(label = "Limit kredytowy", order = 3, renderer = RendererType.DECIMAL)
@PortalDependency(
    field = "customerType",
    operator = DependencyOperator.EQ,
    value = "New",
    max = "5000",
    message = "Nowy klient może mieć limit maksymalnie 5000 zł"
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

**Zakres na podstawie innego pola (referencja `$`):**
```kotlin
// Cena sprzedaży nie może być wyższa niż cena katalogowa
@Column
@PortalField(label = "Cena sprzedaży", order = 5, renderer = RendererType.DECIMAL)
@PortalDependency(
    field = "isDiscounted",
    operator = DependencyOperator.EQ,
    value = "true",
    max = "\$listPrice"  // max = wartość pola listPrice
)
var salePrice: Double = 0.0
```

**Złożony warunek JSON (anyOf/allOf):**
```kotlin
@Column
@PortalField(label = "Pole specjalne", order = 9, renderer = RendererType.TEXT)
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

**Operator IN (wiele wartości):**
```kotlin
@Column
@PortalField(label = "Priorytetowa obsługa", order = 10, renderer = RendererType.BOOLEAN)
@PortalDependency(
    field = "customerType",
    operator = DependencyOperator.IN,
    values = ["VIP", "PREMIUM", "BUSINESS"],
    visibility = DependencyVisibility.SHOW
)
var priorityService: Boolean = false
```

---

## 8. `@PortalAction` + `@PortalFormField` — akcje niestandardowe

### `@PortalAction`

Adnotacja **klasy** (powtarzalna) — deklaruje niestandardowy przycisk akcji na encji. Akcje są wyświetlane w tabeli (per-wiersz i opcjonalnie masowo) i wykonywane przez endpoint `/api/portal/data/{entity}/{id}/action/{name}`.

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class PortalAction(
    val name: String,
    val label: String,
    val labelKey: String = "",           // klucz i18n dla etykiety przycisku
    val icon: String = "play",
    val handler: KClass<*>,
    val formModel: KClass<*> = Void::class,
    val confirmMessage: String = "",
    val confirmMessageKey: String = "",  // klucz i18n dla komunikatu potwierdzenia
    val bulkAllowed: Boolean = false,
    val order: Int = 0,
    val variant: String = "default"
)
```

| Parametr | Typ | Domyślna | Opis |
|---|---|---|---|
| `name` | `String` | — | Unikalny identyfikator akcji w encji, używany jako segment URL |
| `label` | `String` | — | Etykieta przycisku w UI |
| `labelKey` | `String` | `""` | Klucz i18n dla `label`, np. `"action.activate"` |
| `icon` | `String` | `"play"` | Nazwa ikony Lucide na przycisku |
| `handler` | `KClass<*>` | — | Klasa handlera — musi być benem CDI `@ApplicationScoped @Unremovable` |
| `formModel` | `KClass<*>` | `Void::class` | Opcjonalna data class jako model formularza wejściowego. Gdy ustawiona, UI pokazuje modal przed wykonaniem akcji |
| `confirmMessage` | `String` | `""` | Komunikat potwierdzenia przed wykonaniem. Puste = brak potwierdzenia |
| `confirmMessageKey` | `String` | `""` | Klucz i18n dla `confirmMessage` |
| `bulkAllowed` | `Boolean` | `false` | Czy akcja może być wykonana na wielu zaznaczonych wierszach jednocześnie |
| `order` | `Int` | `0` | Pozycja sortowania na pasku akcji |
| `variant` | `String` | `"default"` | Styl wizualny przycisku: `"default"`, `"destructive"`, `"outline"`, `"secondary"`, `"ghost"` |

### Implementacja handlera akcji

> **Ważne:** `ActionHandler` **nie jest interfejsem**. Handlery to zwykłe beny CDI wykrywane przez refleksję Kotlin.
> Framework wyszukuje metody `validate`, `execute` i opcjonalnie `executeBulk` z odpowiednimi sygnaturami.

Handler **musi** być benem CDI `@ApplicationScoped @Unremovable`:

```kotlin
import dev.quatrion.portal.model.ActionResult
import dev.quatrion.portal.model.EntityData
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@io.quarkus.arc.Unremovable
class ActivateCustomerHandler {

    val actionName = "activate"

    suspend fun validate(entity: EntityData, formData: EntityData?): String? {
        // Zwróć komunikat błędu lub null jeśli OK
        val isActive = entity["isActive"] as? Boolean ?: false
        return if (isActive) "Klient jest już aktywny" else null
    }

    suspend fun execute(entity: EntityData, formData: EntityData?): ActionResult {
        // ✅ Po prostu zmień pola encji — framework auto-merguje po execute()
        // ❌ NIE wywołuj entity.persist() / merge() — spowoduje błąd sesji
        val id = entity["id"]
        return ActionResult.Success("Klient $id aktywowany.", refreshTable = true)
    }

    // Opcjonalna implementacja masowa
    suspend fun executeBulk(
        entities: List<EntityData>,
        formData: EntityData?
    ): ActionResult {
        return ActionResult.Success("Aktywowano ${entities.size} klientów.", refreshTable = true)
    }
}
```

**`EntityData`** — klasa reprezentująca dane encji jako nazwane pola. Zachowuje się jak mapa, serializowana przez Jackson jako płaski obiekt JSON:

```kotlin
// Odczyt pól
val name = entity["name"] as? String ?: "Unknown"
val id   = entity["id"]
val isOk = "status" in entity  // sprawdzenie istnienia klucza
```

**`ActionResult` — możliwe wyniki:**

```kotlin
// Link nawigacyjny pokazywany po sukcesie
data class ResultLink(
    val label: String,
    val entityName: String,
    val module: String,
    val entityId: Long
)
```

| Typ | Opis |
|---|---|
| `ActionResult.Success(message, data?, refreshTable, links)` | Sukces. `refreshTable` domyślnie **`true`** — odświeża tabelę. `links` — opcjonalne przyciski nawigacyjne |
| `ActionResult.Error(message, details?)` | Błąd z opcjonalną mapą szczegółów pól |
| `ActionResult.Redirect(url)` | Przekierowanie na podany URL |
| `ActionResult.Download(fileName, contentType, data)` | Pobranie pliku |

```kotlin
// Sukces z linkiem nawigacyjnym do powiązanego rekordu
return ActionResult.Success(
    message = "Uruchomiono zadanie.",
    refreshTable = true,
    links = listOf(ResultLink("Przejdź do TaskRun", "TaskRun", "System", taskRunId))
)
```

### `@PortalFormField`

Adnotacja **pola** data class — opisuje jedno pole w formularzu wejściowym akcji.

> **Ważne:** Używaj use-site target `@field:` na właściwościach Kotlin data class, aby adnotacja trafiła na pole JVM i była odczytywalna przez refleksję Java.

```kotlin
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class PortalFormField(
    val label: String,
    val labelKey: String = "",           // klucz i18n dla etykiety pola
    val renderer: RendererType = RendererType.TEXT,
    val required: Boolean = false,
    val placeholder: String = "",
    val tooltip: String = "",
    val selectOptions: Array<String> = [],
    val selectEnum: KClass<*> = Unit::class,
    val order: Int = 0
)
```

### Przykład — akcja z formularzem

**1. Model formularza:**
```kotlin
data class ProcessOrderForm(
    @field:PortalFormField(
        label = "Priorytet",
        renderer = RendererType.SELECT,
        selectOptions = ["NORMAL", "HIGH", "URGENT"],
        required = true,
        order = 1
    )
    val priority: String = "NORMAL",

    @field:PortalFormField(
        label = "Uwagi operatora",
        renderer = RendererType.TEXTAREA,
        placeholder = "Wpisz uwagi...",
        order = 2
    )
    val notes: String = "",

    @field:PortalFormField(
        label = "Data realizacji",
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
        return if (status == "CANCELLED") "Nie można przetworzyć anulowanego zamówienia" else null
    }

    suspend fun execute(entity: EntityData, formData: EntityData?): ActionResult {
        val priority = formData?.get("priority") as? String ?: "NORMAL"
        val notes = formData?.get("notes") as? String ?: ""
        val orderId = entity["id"]
        // logika biznesowa...
        return ActionResult.Success("Zamówienie $orderId przetworzone z priorytetem $priority.")
    }
}
```

**3. Adnotacja na encji:**
```kotlin
@PortalAction(
    name = "processOrder",
    label = "Przetwórz zamówienie",
    icon = "play",
    handler = ProcessOrderHandler::class,
    formModel = ProcessOrderForm::class,
    confirmMessage = "Czy przetworzyć to zamówienie?",
    order = 1
)
@PortalEntity(label = "Zamówienie", module = "CRM", tabs = OrderTab::class)
@Entity
class Order { ... }
```

### Przykłady akcji

**Akcja niszcząca z potwierdzeniem:**
```kotlin
@PortalAction(
    name = "cancelOrder",
    label = "Anuluj",
    icon = "x-circle",
    handler = CancelOrderHandler::class,
    confirmMessage = "Czy na pewno anulować zamówienie? Operacji nie można cofnąć.",
    variant = "destructive",
    order = 2
)
```

**Akcja masowa:**
```kotlin
@PortalAction(
    name = "sendEmail",
    label = "Wyślij e-mail",
    icon = "mail",
    handler = SendEmailHandler::class,
    bulkAllowed = true,
    variant = "outline",
    order = 3
)
```

**Akcja pobierania pliku:**
```kotlin
@ApplicationScoped
@io.quarkus.arc.Unremovable
class ExportInvoiceHandler {
    val actionName = "exportPdf"
    suspend fun validate(entity: EntityData, formData: EntityData?) = null
    suspend fun execute(entity: EntityData, formData: EntityData?): ActionResult {
        val pdfBytes = generatePdf(entity)
        return ActionResult.Download("faktura-${entity["id"]}.pdf", "application/pdf", pdfBytes)
    }
}
```

---

## 9. `@PortalSecurity` — kontrola dostępu

Adnotacja **klasy** — konfiguruje kontrolę dostępu opartą na rolach dla encji portalu.

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

| Parametr | Typ | Opis |
|---|---|---|
| `viewRoles` | `Array<String>` | Role uprawnione do wyświetlania/listowania encji. Pusta tablica = brak ograniczeń |
| `editRoles` | `Array<String>` | Role uprawnione do tworzenia i aktualizacji rekordów |
| `deleteRoles` | `Array<String>` | Role uprawnione do usuwania rekordów |
| `actionRoles` | `Array<String>` | Role uprawnione do wykonywania `@PortalAction` na encji |
| `ownerField` | `String` | Nazwa pola encji przechowującego `sub` z JWT właściciela rekordu (np. `"createdBySub"`). Gdy ustawione, włącza **filtrowanie na poziomie wiersza** dla ról w `ownerRoles` |
| `ownerRoles` | `Array<String>` | Role ograniczone do własnych rekordów (przez `ownerField`). Użytkownicy **poza** tą listą widzą wszystkie rekordy |

> **Bezpieczeństwo na poziomie wiersza (`ownerField` + `ownerRoles`):**
> - **Listowanie/eksport**: użytkownicy z rolą w `ownerRoles` widzą tylko rekordy, gdzie `ownerField == JWT.sub`
> - **Tworzenie**: pole `ownerField` jest automatycznie ustawiane na `JWT.sub`
> - **Aktualizacja/usuwanie**: użytkownicy z rolą w `ownerRoles` mogą modyfikować tylko własne rekordy

> Nazwy ról muszą odpowiadać wartościom w tokenie JWT/OIDC skonfigurowanym przez `PortalUiConfig.SecurityConfig.rolesAttribute`.

### Przykłady

**Pełna konfiguracja bezpieczeństwa:**
```kotlin
@PortalSecurity(
    viewRoles = ["user", "editor", "admin"],
    editRoles = ["editor", "admin"],
    deleteRoles = ["admin"],
    actionRoles = ["admin"]
)
@PortalEntity(label = "Klient", module = "CRM")
@Entity
class Customer { ... }
```

**Tylko odczyt dla zwykłych użytkowników:**
```kotlin
@PortalSecurity(
    viewRoles = ["user", "admin"],
    editRoles = ["admin"],
    deleteRoles = ["admin"],
    actionRoles = ["admin"]
)
@PortalEntity(label = "Log audytu", module = "System", allowCreate = false, allowEdit = false)
@Entity
class AuditLog { ... }
```

**Encja dostępna tylko dla administratorów:**
```kotlin
@PortalSecurity(
    viewRoles = ["admin"],
    editRoles = ["admin"],
    deleteRoles = ["admin"],
    actionRoles = ["admin"]
)
@PortalEntity(label = "Konfiguracja systemu", module = "System")
@Entity
class SystemConfig { ... }
```

**Bezpieczeństwo na poziomie wiersza (sprzedawca widzi tylko swoje rekordy):**
```kotlin
@PortalSecurity(
    viewRoles = ["sales", "manager", "admin"],
    editRoles = ["sales", "manager", "admin"],
    deleteRoles = ["manager", "admin"],
    actionRoles = ["manager", "admin"],
    ownerField = "createdBySub",   // pole w encji, gdzie jest JWT.sub właściciela
    ownerRoles = ["sales"]         // rola "sales" widzi tylko własne rekordy
)
@PortalEntity(label = "Leady sprzedażowe", module = "CRM")
@Entity
class SalesLead {
    @Column(length = 100)
    @PortalField(label = "Właściciel (sub)", hidden = true)
    var createdBySub: String = ""  // automatycznie ustawiane z JWT przy tworzeniu
    // ...
}
```

---

## 10. Rejestracja encji w `PortalModuleConfig`

Każda encja oznaczona `@PortalEntity` **musi** być zarejestrowana w klasie dziedziczącej po `PortalModuleConfig`. Bean CDI musi być `@ApplicationScoped`.

### Struktura konfiguracji

```kotlin
@ApplicationScoped
class MyModuleConfig : PortalModuleConfig() {

    override fun modules() = listOf(
        ModuleDef(
            name = "MojModul",        // musi odpowiadać @PortalEntity.module
            label = "Mój Moduł",      // etykieta w UI
            icon = "layers",           // ikona Lucide
            order = 1,
            defaultEntity = MyEntity::class.java,
            entities = listOf(
                EntityRef(entityClass = MyEntity::class.java,       group = "Podstawowe", order = 1),
                EntityRef(entityClass = AnotherEntity::class.java,  group = "Podstawowe", order = 2),
                EntityRef(entityClass = UngroupedEntity::class.java, order = 10)  // bez grupy
            )
        )
    )
}
```

### Pola `ModuleDef`

| Pole | Typ | Domyślna | Opis |
|---|---|---|---|
| `name` | `String` | — | Identyfikator modułu (musi pasować do `@PortalEntity.module`) |
| `label` | `String` | — | Wyświetlana nazwa modułu w nawigacji |
| `labelKey` | `String` | `""` | Klucz i18n dla `label`, np. `"module.crm"` |
| `icon` | `String` | `"folder"` | Ikona Lucide modułu |
| `order` | `Int` | `0` | Pozycja sortowania modułu |
| `defaultEntity` | `Class<*>` | — | Encja otwierana po kliknięciu modułu |
| `entities` | `List<EntityRef>` | `[]` | Lista encji w module |

### Pola `EntityRef`

| Pole | Typ | Domyślna | Opis |
|---|---|---|---|
| `entityClass` | `Class<*>` | — | Klasa encji JPA |
| `group` | `String` | `""` | Nazwa grupy w menu bocznym (puste = bez grupy) |
| `order` | `Int` | `0` | Pozycja sortowania wewnątrz grupy/modułu |

### Wiele modułów

```kotlin
@ApplicationScoped
class AppModuleConfig : PortalModuleConfig() {

    override fun modules() = listOf(crmModule(), catalogModule(), systemModule())

    private fun crmModule() = ModuleDef(
        name = "CRM", label = "CRM", icon = "users", order = 1,
        defaultEntity = Customer::class.java,
        entities = listOf(
            EntityRef(Customer::class.java, group = "Klienci", order = 1),
            EntityRef(Lead::class.java,     group = "Klienci", order = 2),
            EntityRef(Country::class.java,  group = "Słowniki", order = 1),
        )
    )

    private fun catalogModule() = ModuleDef(
        name = "Katalog", label = "Katalog", icon = "package", order = 2,
        defaultEntity = Product::class.java,
        entities = listOf(
            EntityRef(Product::class.java,  group = "Produkty", order = 1),
            EntityRef(Category::class.java, group = "Słowniki", order = 1),
            EntityRef(Supplier::class.java, order = 10)
        )
    )

    private fun systemModule() = ModuleDef(
        name = "System", label = "System", icon = "settings", order = 99,
        defaultEntity = AuditLog::class.java,
        entities = listOf(
            EntityRef(AuditLog::class.java, order = 1),
            EntityRef(SystemConfig::class.java, order = 2)
        )
    )
}
```

---

## 11. Kompletny przykład — encja Klient

Poniżej kompletny przykład encji używającej wszystkich omówionych adnotacji:

```kotlin
// ─── Zakładki ───────────────────────────────────────────────────────────────
enum class CustomerTab(
    override val label: String,
    override val icon: String,
    override val order: Int
) : PortalTab {
    BASIC("Podstawowe",  "user",         0),
    CONTACT("Kontakt",   "phone",         1),
    FINANCIAL("Finanse", "dollar-sign",   2)
}

// ─── Enum statusu ───────────────────────────────────────────────────────────
enum class CustomerType(val label: String) {
    NEW("Nowy"), REGULAR("Regularny"), PREMIUM("Premium"), VIP("VIP");
    override fun toString() = label
}

enum class CustomerTag { VIP, NEW, REGULAR, PREMIUM }

// ─── Model formularza akcji ─────────────────────────────────────────────────
data class SendEmailForm(
    @field:PortalFormField(
        label = "Temat",
        renderer = RendererType.TEXT,
        required = true,
        order = 1
    )
    val subject: String = "",

    @field:PortalFormField(
        label = "Treść",
        renderer = RendererType.TEXTAREA,
        required = true,
        order = 2
    )
    val body: String = ""
)

// ─── Handlery akcji ─────────────────────────────────────────────────────────
@ApplicationScoped
@io.quarkus.arc.Unremovable
class ActivateCustomerHandler {
    val actionName = "activate"
    suspend fun validate(entity: EntityData, formData: EntityData?) =
        if (entity["isActive"] as? Boolean == true) "Klient już aktywny" else null
    suspend fun execute(entity: EntityData, formData: EntityData?) =
        ActionResult.Success("Klient aktywowany.", refreshTable = true)
}

@ApplicationScoped
@io.quarkus.arc.Unremovable
class SendEmailHandler {
    val actionName = "sendEmail"
    suspend fun validate(entity: EntityData, formData: EntityData?) = null
    suspend fun execute(entity: EntityData, formData: EntityData?): ActionResult {
        val subject = formData?.get("subject") as? String ?: ""
        val email = entity["email"] as? String ?: ""
        // logika wysyłki...
        return ActionResult.Success("E-mail '$subject' wysłany na $email.")
    }
    suspend fun executeBulk(entities: List<EntityData>, formData: EntityData?) =
        ActionResult.Success("E-mail wysłany do ${entities.size} klientów.")
}

// ─── Encja ──────────────────────────────────────────────────────────────────
@Entity
@Table(name = "customer")
@PortalEntity(
    label = "Klient",
    module = "CRM",
    group = "Klienci",
    icon = "users",
    order = 1,
    description = "Klienci firmy — główny słownik CRM",
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
    label = "Aktywuj",
    icon = "check-circle",
    handler = ActivateCustomerHandler::class,
    confirmMessage = "Czy aktywować klienta?",
    order = 1
)
@PortalAction(
    name = "sendEmail",
    label = "Wyślij e-mail",
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
        label = "Imię i nazwisko",
        tab = "BASIC", order = 1,
        required = true,
        renderer = RendererType.TEXT,
        filterType = FilterType.CONTAINS
    )
    var name: String = ""

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    @PortalField(
        label = "Typ klienta",
        tab = "BASIC", order = 2,
        renderer = RendererType.SELECT,
        filterType = FilterType.IN,
        selectEnum = CustomerType::class
    )
    var customerType: CustomerType? = null

    @Column
    @PortalField(
        label = "Aktywny",
        tab = "BASIC", order = 3,
        renderer = RendererType.BOOLEAN,
        filterType = FilterType.BOOLEAN
    )
    var isActive: Boolean = true

    @Column(unique = true)
    @PortalField(
        label = "E-mail",
        tab = "CONTACT", order = 1,
        renderer = RendererType.EMAIL,
        filterType = FilterType.EXACT
    )
    var email: String = ""

    @Column(length = 20)
    @Regex(
        pattern = """^\+?[\d\s\-]{7,20}$""",
        message = "Nieprawidłowy format numeru telefonu"
    )
    @PortalField(
        label = "Telefon",
        tab = "CONTACT", order = 2,
        renderer = RendererType.TEXT,
        filterType = FilterType.STARTS_WITH,
        placeholder = "+48 …"
    )
    var phone: String = ""

    @Column
    @PortalField(
        label = "Kraj",
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
        label = "Limit kredytowy",
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
        message = "Nowy klient może mieć limit maksymalnie 5000 zł"
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
        label = "Tagi",
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
        message = "Nowy klient może mieć tylko tag NEW"
    )
    @PortalDependency(
        field = "customerType",
        operator = DependencyOperator.EQ,
        value = "VIP",
        allowedValues = ["VIP", "PREMIUM"]
    )
    var tags: String = ""

    // Soft-delete — wymagane gdy softDelete = true w @PortalEntity
    @Column
    @PortalField(label = "Usunięty", hidden = true, showInTable = false, showInFilter = false)
    var deleted: Boolean = false
}
```

---

## 12. Najczęstsze wzorce i FAQ

### Jaki renderer wybrać?

| Typ Kotlin/JPA | Zalecany renderer |
|---|---|
| `String` (krótki) | `TEXT` lub `EMAIL`, `URL`, `PASSWORD`, `COLOR` |
| `String` (długi) | `TEXTAREA` |
| `String` (JSON) | `JSON` |
| `Int`, `Long` | `NUMBER` |
| `Double`, `BigDecimal` | `DECIMAL` |
| `Boolean` | `BOOLEAN` |
| `Enum` | `SELECT` z `selectEnum` |
| Lista enumów | `MULTI_SELECT` z `selectEnum` |
| Klucz obcy (Long?) | `RELATION` z `@PortalRelation` + `@PortalLookup` |
| Kolekcja encji | `RELATION_LIST` z `@PortalRelation` + `@PortalLookup` |
| `String` (ścieżka pliku) | `FILE` |

### Jak ukryć pole ID?

```kotlin
@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
@PortalField(label = "ID", order = 0, readonly = true, showInFilter = false)
var id: Long = 0
// showInTable = true (domyślnie) — ID jest widoczne w tabeli
// Jeśli chcesz całkowicie ukryć: hidden = true
```

### Jak zrobić pole tylko w formularzu, nie w tabeli?

```kotlin
@PortalField(label = "Opis", order = 5, renderer = RendererType.TEXTAREA, showInTable = false)
var description: String = ""
```

### Jak zrobić pole tylko w tabeli, nie filtrowalne?

```kotlin
@PortalField(label = "Nazwa", order = 1, filterType = FilterType.NONE)
var name: String = ""
```

### Jak dodać domyślną wartość?

```kotlin
@PortalField(label = "Aktywny", order = 3, renderer = RendererType.BOOLEAN, defaultValue = "true")
var isActive: Boolean = true

@PortalField(label = "Status", order = 4, renderer = RendererType.SELECT,
             selectEnum = Status::class, defaultValue = "ACTIVE")
var status: Status? = null
```

### Jak pogrupować pola wewnątrz zakładki?

```kotlin
// Pola z tą samą wartością `group` są renderowane razem w ramce/sekcji
@PortalField(label = "Imię", tab = "BASIC", order = 1, group = "Dane osobowe")
var firstName: String = ""

@PortalField(label = "Nazwisko", tab = "BASIC", order = 2, group = "Dane osobowe")
var lastName: String = ""

@PortalField(label = "Data urodzenia", tab = "BASIC", order = 3, group = "Dane osobowe")
var birthDate: String = ""
```

### Dlaczego `@PortalFormField` wymaga `@field:`?

Kotlin umieszcza adnotacje na właściwości (property), ale Java Reflection widzi je na polu (field). Bez prefiksu `@field:` `MetadataService` nie znajdzie adnotacji przez refleksję.

```kotlin
// ✅ Poprawnie
data class MyForm(
    @field:PortalFormField(label = "Nazwa", required = true)
    val name: String = ""
)

// ❌ Błędnie — adnotacja nie będzie odczytana
data class MyForm(
    @PortalFormField(label = "Nazwa", required = true)
    val name: String = ""
)
```

### Jak działa soft-delete?

1. Dodaj `softDelete = true` do `@PortalEntity`
2. Dodaj pole `deleted: Boolean = false` do encji
3. Opcjonalnie oznacz je jako `@PortalField(label = "Usunięty", hidden = true)` żeby było niewidoczne

Framework automatycznie filtruje rekordy z `deleted = true` we wszystkich zapytaniach listujących.

### Jak konfigurować kaskadowe dropdowny?

Użyj `@PortalLookup(dependsOn = "nazwaPolaRodzica")`. Frontend automatycznie przekaże wartość pola nadrzędnego jako filtr przy pobieraniu opcji z endpointu `/lookup`.

```kotlin
// Kraj (źródło)
@PortalLookup(labelField = "name", valueField = "id")
var countryId: Long? = null

// Miasto (zależy od kraju)
@PortalLookup(labelField = "name", valueField = "id", dependsOn = "countryId")
var cityId: Long? = null
```

### Kolejność adnotacji na polu

Zalecana kolejność (dla czytelności):

```kotlin
@Column(...)             // JPA
@Enumerated(...)         // JPA (opcjonalnie)
@Regex(...)              // Walidacja wzorcem
@PortalField(...)        // Deklaracja pola UI
@PortalRelation(...)     // Konfiguracja relacji (jeśli dotyczy)
@PortalLookup(...)       // Konfiguracja lookup (jeśli dotyczy)
@PortalDependency(...)   // Reguły warunkowe (jeśli dotyczy, powtarzalna)
var fieldName: Type = defaultValue
```

---

## 13. `RowColor` — kolorowanie wierszy tabeli

Encje mogą implementować interfejs `RowColorProvider` aby sterować kolorem tła wiersza w tabeli portalu. Framework automatycznie dołącza kolor do danych encji jako pole `_rowColor`.

```kotlin
enum class RowColor {
    NONE, SUCCESS, WARNING, DANGER, INFO, MUTED
}

interface RowColorProvider {
    fun currentStatus(): RowColor?
}
```

### Mapowanie kolorów na klasy CSS

| Wartość | Kolor | Klasa CSS |
|---|---|---|
| `NONE` | — (domyślny) | brak |
| `SUCCESS` | zielony | `qp-tr-success` |
| `WARNING` | żółty | `qp-tr-warning` |
| `DANGER` | czerwony | `qp-tr-danger` |
| `INFO` | niebieski | `qp-tr-info` |
| `MUTED` | szary | `qp-tr-muted` |

### Jak zaimplementować

```kotlin
@Entity
@Table(name = "task_run")
@PortalEntity(label = "Uruchomienia zadań", module = "System")
class TaskRun : RowColorProvider {

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    @PortalField(label = "Status", order = 2, renderer = RendererType.SELECT, selectEnum = TaskRunStatus::class)
    var status: TaskRunStatus = TaskRunStatus.URUCHOMIONO

    // Implementacja interfejsu — zwraca kolor na podstawie statusu
    override fun currentStatus(): RowColor? = when (status) {
        TaskRunStatus.URUCHOMIONO -> RowColor.INFO     // niebieski — w toku
        TaskRunStatus.ZAKONCZONE  -> RowColor.SUCCESS  // zielony — sukces
        TaskRunStatus.ERROR       -> RowColor.DANGER   // czerwony — błąd
        TaskRunStatus.ANULOWANE   -> RowColor.WARNING  // żółty — anulowane
    }
}
```

> `null` zwrócone przez `currentStatus()` jest równoważne `RowColor.NONE` — wiersz nie jest kolorowany.
> Metadane encji automatycznie zawierają pole `rowColorField = "_rowColor"`, co sygnalizuje frontendowi kolorowanie.

---

## 14. Konfiguracja `portal.ui` — `application.properties`

Framework czyta konfigurację UI przez SmallRye Config z prefiksem `portal.ui`. Wszystkie właściwości mają wartości domyślne — możesz nadpisać tylko to co potrzebujesz.

```properties
# Tytuł aplikacji w przeglądarce
portal.ui.title=Quatrion Portal

# Ścieżka do logo (opcjonalnie)
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

# ── Motyw (kolory) ────────────────────────────────────────────────────────────
portal.ui.theme.primary-color=#2563eb
portal.ui.theme.accent-color=#3b82f6
portal.ui.theme.sidebar-bg=#1e293b
portal.ui.theme.sidebar-text=#e2e8f0
portal.ui.theme.header-bg=#ffffff

# ── Tabela ────────────────────────────────────────────────────────────────────
portal.ui.table.default-page-size=25
portal.ui.table.show-row-numbers=false
portal.ui.table.enable-export=false
portal.ui.table.sticky-header=true

# ── Formularz ─────────────────────────────────────────────────────────────────
portal.ui.form.modal-width=lg           # sm | md | lg | xl | 2xl
portal.ui.form.nested-modal-width=md
portal.ui.form.show-tab-icons=true
portal.ui.form.auto-save-interval=0     # sekundy; 0 = wyłączone

# ── Filtry ────────────────────────────────────────────────────────────────────
portal.ui.filter.position=modal         # modal | sidebar | inline
portal.ui.filter.remember-filters=true
portal.ui.filter.max-filter-fields=20

# ── Bezpieczeństwo ────────────────────────────────────────────────────────────
portal.ui.security.provider=none        # none | keycloak | oidc
portal.ui.security.roles-attribute=realm_access.roles

# ── Eksport ───────────────────────────────────────────────────────────────────
portal.export.max-rows=50000            # limit wierszy w jednym eksporcie
```

### Opis kluczowych właściwości

| Właściwość | Domyślna | Opis |
|---|---|---|
| `portal.ui.security.provider` | `"none"` | Dostawca bezpieczeństwa: `"none"` (brak auth), `"keycloak"`, `"oidc"` |
| `portal.ui.security.roles-attribute` | `"realm_access.roles"` | Ścieżka JSON w tokenie JWT skąd pobierane są role użytkownika. Musi odpowiadać rolom w `@PortalSecurity` |
| `portal.ui.table.enable-export` | `false` | Gdy `true`, w tabeli pojawia się przycisk eksportu (CSV/XLSX/JSON/PDF) |
| `portal.ui.form.auto-save-interval` | `0` | Interwał auto-zapisu formularza w sekundach. `0` = wyłączone |
| `portal.export.max-rows` | `50000` | Maks. liczba wierszy eksportowanych w jednym żądaniu. Nadmiar zwraca HTTP 413 |

---

## 15. Pełna lista endpointów REST API

Wszystkie endpointy wymagają uwierzytelnienia (`@Authenticated`). Bazowa ścieżka: `/api/portal`.

### Metadane

| Metoda | Ścieżka | Opis |
|---|---|---|
| `GET` | `/api/portal/metadata` | Pełne metadane portalu (encje, pola, akcje, konfiguracja UI). Obsługuje ETag/304 |

### CRUD encji (`/api/portal/data/{entityName}`)

| Metoda | Ścieżka | Opis |
|---|---|---|
| `GET` | `/{entityName}` | Lista rekordów — paginacja, sortowanie, filtrowanie przez query params |
| `GET` | `/{entityName}/{id}` | Pobierz rekord po ID |
| `POST` | `/{entityName}` | Utwórz rekord. Zwraca `201 Created` |
| `PUT` | `/{entityName}/{id}` | Zaktualizuj rekord. Obsługuje optimistic locking (`409 Conflict`) |
| `DELETE` | `/{entityName}/{id}` | Usuń rekord. Zwraca `204 No Content` |
| `DELETE` | `/{entityName}/bulk` | Usuń wiele rekordów. Body: `{"ids": [1, 2, 3]}` |
| `PUT` | `/{entityName}/bulk-update` | Zaktualizuj jedno pole w wielu rekordach. Body: `{"ids": [1,2], "field": "status", "value": "ACTIVE"}` |

### Lookup i wyszukiwanie

| Metoda | Ścieżka | Opis |
|---|---|---|
| `GET` | `/{entityName}/lookup` | Opcje dla pickera relacji. Params: `q`, `labelField`, `valueField`, `max`, `filterQuery`, `dependsOnField`, `dependsOnValue`, `orderBy` |
| `GET` | `/{entityName}/search` | Pełnotekstowe wyszukiwanie po polach TEXT/TEXTAREA/EMAIL/URL. Params: `q` (min. 2 znaki), `page`, `size` |
| `GET` | `/{entityName}/count` | Liczba rekordów. Zwraca `{"count": 42}` |
| `GET` | `/{entityName}/stats` | Statystyki pól numerycznych (min/max/avg/sum) |

### Soft-delete

| Metoda | Ścieżka | Opis |
|---|---|---|
| `GET` | `/{entityName}/deleted` | Lista soft-usuniętych rekordów (encja musi mieć `softDelete = true`) |
| `POST` | `/{entityName}/{id}/restore` | Przywróć soft-usunięty rekord |

### Akcje i historia

| Metoda | Ścieżka | Opis |
|---|---|---|
| `POST` | `/{entityName}/{id}/action/{actionName}` | Wykonaj akcję. Body: `EntityData` (dane formularza), opcjonalny |
| `GET` | `/{entityName}/{id}/history` | Historia zmian (encja musi mieć `auditLog = true`). Params: `page`, `size` |

### Eksport i import

| Metoda | Ścieżka | Opis |
|---|---|---|
| `GET` | `/{entityName}/export/csv` | Eksport do CSV. Stosuje aktywne filtry z query params. Limit: `portal.export.max-rows` |
| `GET` | `/{entityName}/export/xlsx` | Eksport do XLSX |
| `GET` | `/{entityName}/export/json` | Eksport do JSON |
| `GET` | `/{entityName}/export/pdf` | Eksport do PDF |
| `POST` | `/{entityName}/import` | Import z CSV. Body: `{"csv": "nagłówek1,nagłówek2\nwartość1,wartość2\n..."}` |
| `POST` | `/{entityName}/batch` | Batch-create z JSON array. Body: `[{...}, {...}]` |

### Filtrowanie w listowaniu

Query parametry dla `GET /{entityName}`:

```
?filter[fieldName][operator]=value
&sort=fieldName&order=asc
&page=0&size=25
```

Przykłady:
```
?filter[status][eq]=ACTIVE
?filter[name][contains]=kowal
?filter[price][gte]=100&filter[price][lte]=1000
?filter[customerType][in]=VIP,PREMIUM
?sort=name&order=asc&page=0&size=50
```

---

## 16. Szybka referencja adnotacji

| Adnotacja | Target | Powtarzalna | Opis |
|---|---|---|---|
| `@PortalEntity` | Klasa | Nie | Rejestruje encję; ustawia label, moduł, ikonę, zakładki, uprawnienia, rozmiar strony, soft-delete |
| `@PortalAction` | Klasa | **Tak** | Deklaruje przycisk akcji z handlerem, opcjonalnym formularzem, potwierdzeniem |
| `@PortalSecurity` | Klasa | Nie | Kontrola dostępu oparta na rolach (view/edit/delete/action + row-level ownership) |
| `@PortalField` | Pole/Funkcja | Nie | Deklaruje pole UI; ustawia renderer, filtr, walidację, szerokość, grupę |
| `@PortalRelation` | Pole | Nie | Konfiguruje encję docelową, wyświetlane kolumny, akcje per-wiersz dla RELATION/RELATION_LIST |
| `@PortalLookup` | Pole/Funkcja | Nie | Konfiguruje label/value pól lookupa, filtr, kaskadową zależność, parentField |
| `@PortalDependency` | Pole/Funkcja | **Tak** | Warunkowa widoczność, dostępne wartości i zakres numeryczny |
| `@PortalFormField` | Pole | Nie | Opisuje pole w modelu formularza akcji (użyj `@field:`) |
| `@Regex` | Pole | Nie | Dołącza wzorzec regex dla walidacji po stronie frontendu |
| `RowColorProvider` | Interfejs (klasa) | — | Implementuj aby sterować kolorem wiersza w tabeli |
