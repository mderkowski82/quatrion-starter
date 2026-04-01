# Quatrion Starter

> ⚠️ **WERSJA ALFA** — Projekt jest w fazie aktywnego rozwoju. API oraz interfejs adnotacji mogą ulec zmianie bez zachowania wstecznej kompatybilności. Nie zaleca się używania w środowiskach produkcyjnych.

🇬🇧 [English documentation → README.md](README.md)

---

Starter projektu dla frameworka **Quatrion Portal** — platforma do szybkiego tworzenia aplikacji, która automatycznie przekształca adnotowane encje JPA w pełnofunkcjonalny interfejs administracyjny. Bez pisania kodu frontendowego.

## Czym jest Quatrion Portal?

**Quatrion Portal** to fullstackowy framework oparty na Quarkus i Next.js. Zasada działania jest prosta:

1. Adnotuj swoje encje JPA za pomocą `@PortalEntity`, `@PortalField`, `@PortalRelation` i innych adnotacji.
2. Zarejestruj encje w `AppModuleConfig`.
3. Uruchom aplikację — backend udostępnia API metadanych (`/api/portal/metadata`), a frontend dynamicznie generuje kompletny interfejs CRUD (tabele, formularze, filtry, wyszukiwanie, eksport, akcje) — **żadnych komponentów React nie jest wymaganych**.

### Kluczowe funkcje (Alfa)

| Funkcja | Status |
|---|---|
| Automatycznie generowane tabele CRUD | ✅ Dostępne |
| Automatycznie generowane formularze z walidacją | ✅ Dostępne |
| Filtry kolumn i globalne wyszukiwanie | ✅ Dostępne |
| Nawigacja modułowa / boczny panel | ✅ Dostępne |
| Uwierzytelnianie OIDC przez Keycloak | ✅ Dostępne |
| Bezpieczeństwo pól i akcji oparte na rolach (`@PortalSecurity`) | ✅ Dostępne |
| Niestandardowe przyciski akcji (`@PortalAction`) | ✅ Dostępne |
| Lookupowanie relacji (`@PortalRelation`, `@PortalLookup`) | ✅ Dostępne |
| Warunkowe reguły pól (`@PortalDependency`) | ✅ Dostępne |
| Miękkie usuwanie i dziennik audytu | ✅ Dostępne |
| Eksport CSV / Excel | 🔄 W trakcie |
| Obsługa wielu tenantów | 🔄 Planowane |

> Pełna dokumentacja adnotacji: [`ANNOTATIONS_PL.md`](ANNOTATIONS_PL.md) (Polski) lub [`ANNOTATIONS_EN.md`](ANNOTATIONS_EN.md) (English).

---

## Stack technologiczny

| Warstwa | Technologia |
|---|---|
| Backend | Quarkus 3.23 + Kotlin 2.2 + Hibernate Reactive |
| Frontend | Next.js (obraz Docker z `ghcr.io`) |
| Autentykacja | Keycloak 25 (OIDC / JWT) |
| Baza danych | PostgreSQL 16 |

## Szybki start

### Opcja 1 — Tryb deweloperski (hot-reload backendu, bez Node.js)

```bash
# 1. Uruchom infrastrukturę (PostgreSQL + Keycloak)
docker compose -f docker-compose.infra.yml up -d

# 2. Uruchom backend z live reload
./gradlew quarkusDev
```

| URL | Usługa |
|---|---|
| http://localhost:8080 | Backend API |
| http://localhost:8080/q/swagger-ui | Swagger UI |
| http://localhost:8180 | Keycloak Admin |

### Opcja 2 — Pełny stack Docker

```bash
# 1. Skopiuj i uzupełnij zmienne środowiskowe
cp .env.example .env

# 2. Uruchom wszystko
docker compose up
```

| URL | Usługa |
|---|---|
| http://localhost:3000 | Frontend |
| http://localhost:8080 | Backend API |
| http://localhost:8080/q/swagger-ui | Swagger UI |
| http://localhost:8180 | Keycloak Admin |

## Dodawanie encji

**Krok 1 — Utwórz encję JPA:**

```kotlin
// src/main/kotlin/com/example/portal/entity/Customer.kt
@Entity @Table(name = "customer")
@PortalEntity(label = "Klient", module = "CRM", icon = "users", order = 1)
class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(length = 100, nullable = false)
    @PortalField(label = "Nazwa", order = 1, required = true,
        renderer = RendererType.TEXT, filterType = FilterType.CONTAINS)
    var name: String = ""

    @Column(length = 200)
    @PortalField(label = "Email", order = 2,
        renderer = RendererType.EMAIL, filterType = FilterType.CONTAINS)
    var email: String = ""
}
```

**Krok 2 — Zarejestruj encję w `AppModuleConfig`:**

```kotlin
ModuleDef(
    name = "CRM", label = "CRM", icon = "users", order = 2,
    defaultEntity = Customer::class.java,
    entities = listOf(EntityRef(entityClass = Customer::class.java, group = "Kontakty", order = 1))
)
```

To wszystko — pełny interfejs CRUD (tabela, formularz, filtry, wyszukiwanie, eksport) zostaje wygenerowany automatycznie.

## Zmienne środowiskowe

Skopiuj `.env.example` → `.env` i uzupełnij:

| Zmienna | Opis |
|---|---|
| `PORTAL_DB_USER` / `PORTAL_DB_PASSWORD` | Dane dostępowe do PostgreSQL |
| `KEYCLOAK_CLIENT_SECRET` | Sekret frontendowego klienta Keycloak |
| `KEYCLOAK_BACKEND_CLIENT_SECRET` | Sekret service account backendu Keycloak |
| `AUTH_SECRET` | Klucz podpisywania Auth.js (`openssl rand -hex 32`) |
| `FRONTEND_VERSION` | Tag obrazu frontendowego (np. `2026.03.30-1422`) lub `latest` |
| `LICENSE_KEY` | Klucz licencyjny Quatrion Portal |

## Tagi obrazu frontendowego

Frontend jest dostarczany jako gotowy obraz Docker:

```
ghcr.io/mderkowski82/quatrion-portal-frontend:<tag>
```

Tagi stosują konwencję **CalVer** (`YYYY.MM.DD-HHmm`) oraz `latest`:

```bash
# Przypnij konkretną wersję
FRONTEND_VERSION=2026.03.30-1422

# Lub zawsze używaj najnowszej
FRONTEND_VERSION=latest
```

Wszystkie dostępne tagi:
https://github.com/mderkowski82/quatrion-saas/pkgs/container/quatrion-portal-frontend

## Domyślne konta deweloperskie Keycloak

Konta deweloperskie (z `keycloak/realm-export.json`):

| Użytkownik | Hasło | Rola |
|---|---|---|
| `admin@example.com` | `admin123` | `portal-admin` |
| `user@example.com` | `user123` | `portal-user` |

## Struktura projektu

```
quatrion-starter/
├── src/main/kotlin/com/example/portal/
│   ├── config/AppModuleConfig.kt   ← rejestracja encji
│   └── entity/Product.kt           ← przykładowa encja
├── src/main/resources/
│   └── application.properties
├── docker-compose.yml              ← pełny stack (infra + backend + obraz frontendowy)
├── docker-compose.infra.yml        ← dev: tylko postgres + keycloak
├── Dockerfile                      ← budowanie backendu
├── keycloak/realm-export.json      ← konfiguracja realm Keycloak
└── .env.example                    ← szablon zmiennych środowiskowych
```

## Dokumentacja

- [`ANNOTATIONS_PL.md`](ANNOTATIONS_PL.md) — Pełna dokumentacja adnotacji (Polski)

## Licencja

Quatrion Portal jest produktem komercyjnym. Korzystanie wymaga ważnego klucza `LICENSE_KEY`.
Kontakt: https://quatrion.dev

---

> **Informacja o wersji alfa:** Ten starter oraz framework Quatrion Portal są aktualnie w fazie **alfa**. Funkcje, adnotacje i API mogą ulec zmianie. Zgłaszanie błędów i opinii jest mile widziane poprzez GitHub Issues.

