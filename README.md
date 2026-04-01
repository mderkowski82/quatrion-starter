# Quatrion Starter

> ⚠️ **ALPHA VERSION** — This project is under active development. The annotation API and interfaces may change without backward compatibility. Not recommended for production use.

🇵🇱 [Dokumentacja po polsku → README_PL.md](README_PL.md)

---

Starter project for the **Quatrion Portal** framework — a rapid application development platform that turns annotated JPA entities into a fully functional admin UI **automatically**, with zero frontend code.

## What is Quatrion Portal?

**Quatrion Portal** is a full-stack framework built on top of Quarkus and Next.js. The idea is simple:

1. Annotate your JPA entities with `@PortalEntity`, `@PortalField`, `@PortalRelation` and other annotations.
2. Register entities in `AppModuleConfig`.
3. Start the application — the backend exposes a metadata API (`/api/portal/metadata`), and the frontend dynamically generates a complete CRUD interface (tables, forms, filters, search, export, actions) — **no React components required**.

### Key features (Alpha)

| Feature | Status |
|---|---|
| Auto-generated CRUD tables | ✅ Available |
| Auto-generated forms with validation | ✅ Available |
| Column filters & global search | ✅ Available |
| Module / sidebar navigation | ✅ Available |
| Keycloak OIDC authentication | ✅ Available |
| Role-based field/action security (`@PortalSecurity`) | ✅ Available |
| Custom action buttons (`@PortalAction`) | ✅ Available |
| Relation lookups (`@PortalRelation`, `@PortalLookup`) | ✅ Available |
| Conditional field rules (`@PortalDependency`) | ✅ Available |
| Soft delete & audit log | ✅ Available |
| CSV / Excel export | 🔄 In progress |
| Multi-tenant support | 🔄 Planned |

> Full annotation reference: see [`ANNOTATIONS_EN.md`](ANNOTATIONS_EN.md) (English) or [`ANNOTATIONS_PL.md`](ANNOTATIONS_PL.md) (Polish).

---

## Stack

| Layer | Technology |
|---|---|
| Backend | Quarkus 3.23 + Kotlin 2.2 + Hibernate Reactive |
| Frontend | Next.js (Docker image from `ghcr.io`) |
| Auth | Keycloak 25 (OIDC / JWT) |
| Database | PostgreSQL 16 |

## Quick Start

### Option 1 — Dev mode (backend hot-reload, no Node.js required)

```bash
# 1. Start infrastructure (PostgreSQL + Keycloak)
docker compose -f docker-compose.infra.yml up -d

# 2. Run backend with live reload
./gradlew quarkusDev
```

| URL | Service |
|---|---|
| http://localhost:8080 | Backend API |
| http://localhost:8080/q/swagger-ui | Swagger UI |
| http://localhost:8180 | Keycloak Admin |

### Option 2 — Full Docker stack

```bash
# 1. Copy and edit environment variables
cp .env.example .env

# 2. Start everything
docker compose up
```

| URL | Service |
|---|---|
| http://localhost:3000 | Frontend |
| http://localhost:8080 | Backend API |
| http://localhost:8080/q/swagger-ui | Swagger UI |
| http://localhost:8180 | Keycloak Admin |

## Adding an Entity

**Step 1 — Create a JPA entity:**

```kotlin
// src/main/kotlin/com/example/portal/entity/Customer.kt
@Entity @Table(name = "customer")
@PortalEntity(label = "Customer", module = "CRM", icon = "users", order = 1)
class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @Column(length = 100, nullable = false)
    @PortalField(label = "Name", order = 1, required = true,
        renderer = RendererType.TEXT, filterType = FilterType.CONTAINS)
    var name: String = ""

    @Column(length = 200)
    @PortalField(label = "Email", order = 2,
        renderer = RendererType.EMAIL, filterType = FilterType.CONTAINS)
    var email: String = ""
}
```

**Step 2 — Register in `AppModuleConfig`:**

```kotlin
ModuleDef(
    name = "CRM", label = "CRM", icon = "users", order = 2,
    defaultEntity = Customer::class.java,
    entities = listOf(EntityRef(entityClass = Customer::class.java, group = "Contacts", order = 1))
)
```

That's it — the full CRUD UI (table, form, filters, search, export) is generated automatically.

## Environment Variables

Copy `.env.example` → `.env` and fill in:

| Variable | Description |
|---|---|
| `PORTAL_DB_USER` / `PORTAL_DB_PASSWORD` | PostgreSQL credentials |
| `KEYCLOAK_CLIENT_SECRET` | Keycloak frontend client secret |
| `KEYCLOAK_BACKEND_CLIENT_SECRET` | Keycloak backend service account secret |
| `AUTH_SECRET` | Auth.js signing secret (`openssl rand -hex 32`) |
| `FRONTEND_VERSION` | Frontend image tag (e.g. `2026.03.30-1422`) or `latest` |
| `LICENSE_KEY` | Quatrion Portal license key |

## Frontend Image Tags

The frontend is served from a pre-built Docker image:

```
ghcr.io/mderkowski82/quatrion-portal-frontend:<tag>
```

Available tags follow **CalVer** (`YYYY.MM.DD-HHmm`) plus `latest`:

```bash
# Pin a specific version
FRONTEND_VERSION=2026.03.30-1422

# Or always use latest
FRONTEND_VERSION=latest
```

See all available tags at:
https://github.com/mderkowski82/quatrion-saas/pkgs/container/quatrion-portal-frontend

## Keycloak Dev Credentials

Default dev accounts (from `keycloak/realm-export.json`):

| User | Password | Role |
|---|---|---|
| `admin@example.com` | `admin123` | `portal-admin` |
| `user@example.com` | `user123` | `portal-user` |

## Project Structure

```
quatrion-starter/
├── src/main/kotlin/com/example/portal/
│   ├── config/AppModuleConfig.kt   ← register entities here
│   └── entity/Product.kt           ← example entity
├── src/main/resources/
│   └── application.properties
├── docker-compose.yml              ← full stack (infra + backend + frontend image)
├── docker-compose.infra.yml        ← dev: only postgres + keycloak
├── Dockerfile                      ← backend build
├── keycloak/realm-export.json      ← Keycloak realm config
└── .env.example                    ← environment variables template
```

## Documentation

- [`ANNOTATIONS_EN.md`](ANNOTATIONS_EN.md) — Full annotation reference (English)

## License

Quatrion Portal is a commercial product. Usage requires a valid `LICENSE_KEY`.
Contact: https://quatrion.dev

---

> **Alpha Notice:** This starter and the underlying Quatrion Portal framework are currently in **alpha**. Features, annotations, and APIs are subject to change. Feedback and bug reports are welcome via GitHub Issues.
