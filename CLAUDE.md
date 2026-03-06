# CLAUDE.md

## Prosjektoversikt

**Summa Summarum** - Enkelt regnskapssystem for bedrift/forening med multi-tenancy (flere organisasjoner).

## Teknologier

- **Backend**: Kotlin 2.1 + Ktor 3.0.3 (JVM 21), port 8083
- **Frontend**: React 18 + Vite + TypeScript + Tailwind + TanStack Query, port 5175
- **Database**: PostgreSQL 16 + Exposed ORM 0.57
- **Reverse Proxy**: Nginx (dev: 9200)
- **Auth**: JWT (HttpOnly cookies) + OTP (passwordless login)
- **E-post**: Postfix (DKIM-signert, selector: summa) via jakarta.mail
- **Deployment**: Docker Compose + Cloudflare Tunnel

## Kommandoer

```bash
# Start utvikling (http://localhost:9200)
./dev

# Se logger
docker compose logs -f backend
docker compose logs -f frontend

# Stopp og slett database
docker compose down -v

# Produksjon med Cloudflare Tunnel
docker compose -f docker-compose.tunnel.yml up -d --build
```

## Prosjektstruktur

```
summa-summarum/
  backend/src/main/kotlin/no/summa/
    Application.kt
    database/  Tables.kt, Seed.kt
    models/    DTOs.kt
    plugins/   Auth.kt, CSRF.kt, Database.kt, Routing.kt, Security.kt, Serialization.kt
    routes/    AuthRoutes.kt, HealthRoutes.kt, CategoryRoutes.kt, TransactionRoutes.kt, ReportRoutes.kt, OrganizationRoutes.kt
    services/  AuthService.kt, EmailService.kt, CategoryService.kt, TransactionService.kt, ReportService.kt, OrganizationService.kt, DocumentParserService.kt, ExchangeRateService.kt, AuditLogService.kt, RateLimiter.kt
    utils/     TimeUtils.kt, Validators.kt
  frontend/src/
    App.tsx, main.tsx, index.css
    api/       apiClient.ts, auth.ts, categories.ts, transactions.ts, reports.ts, organizations.ts
    context/   AuthContext.tsx
    components/ ProtectedRoute.tsx, Layout.tsx, Sidebar.tsx
    pages/     Login.tsx, Dashboard.tsx, Transactions.tsx, TransactionForm.tsx, Categories.tsx, Reports.tsx, Organizations.tsx, OrgMembers.tsx
    lib/       queryClient.ts, formatters.ts
  backend/.dockerignore
  frontend/.dockerignore
```

## Database (10 tabeller)

| Tabell | Formål |
|--------|--------|
| Users | Brukere med OTP-felt (roller: ADMIN, SUPERADMIN) |
| Organizations | Organisasjoner (name, orgNumber, mvaRegistered, createdBy, active) |
| UserOrganizations | Kobling bruker-organisasjon (userId, organizationId) |
| Categories | Delt kontoplan (code, name, type=INNTEKT/UTGIFT, active, isDefault) |
| Transactions | Transaksjoner (date, type, amount, currency, vatRate, vatAmount, exchangeRate, amountNok, description, categoryId, organizationId) |
| Attachments | Bilagsvedlegg (transactionId, filename, originalName, mimeType) |
| ParsedDocuments | AI-parset data fra bilag (attachmentId, totalAmount, vatAmount, vatRate, vendorName, vendorOrgNumber, paymentDueDate, paymentReference, status) |
| ParsedLineItems | Linjeposter fra parset bilag (parsedDocumentId, description, amount, vatRate, vatAmount) |
| AuditLogs | Sporingslogg |
| EmailLog | E-postlogg |

## Multi-tenancy

- **Roller**: ADMIN (per org, full tilgang), SUPERADMIN (global, kan opprette orgs og se alt)
- **Dataisolasjon**: Transaksjoner og rapporter er scoped til aktiv organisasjon via JWT-claim
- **Delt kontoplan**: Kategorier er globale, kun redigerbare av SUPERADMIN
- **Org-bytting**: POST `/api/auth/switch-org/{orgId}` re-utsteder JWT med ny orgId
- **Medlemshåndtering**: SUPERADMIN eller org-oppretter kan legge til/fjerne medlemmer

## API-endepunkter

| Metode | Endepunkt | Tilgang | CSRF |
|--------|-----------|---------|------|
| POST | `/api/auth/request-code` | Offentlig | Nei |
| POST | `/api/auth/verify-code` | Offentlig | Nei |
| POST | `/api/auth/logout` | Offentlig | Nei |
| GET | `/api/auth/me` | Autentisert | Nei |
| POST | `/api/auth/switch-org/{orgId}` | Autentisert medlem/superadmin | Nei |
| GET | `/api/categories` | Admin+ | Nei |
| POST | `/api/categories` | Superadmin | Ja |
| PUT/DELETE | `/api/categories/{id}` | Superadmin | Ja |
| GET | `/api/transactions` | Org-medlem (scoped) | Nei |
| POST | `/api/transactions` | Org-medlem (scoped) | Ja |
| GET | `/api/transactions/{id}` | Org-medlem (scoped) | Nei |
| PUT/DELETE | `/api/transactions/{id}` | Org-medlem (scoped) | Ja |
| POST | `/api/transactions/{id}/attachments` | Org-medlem (scoped) | Ja |
| GET | `/api/transactions/{id}/attachments/{aid}` | Org-medlem (scoped) | Nei |
| DELETE | `/api/transactions/{id}/attachments/{aid}` | Org-medlem (scoped) | Ja |
| GET | `/api/reports/overview` | Org-medlem (scoped) | Nei |
| GET | `/api/reports/monthly?year=` | Org-medlem (scoped) | Nei |
| GET | `/api/reports/categories?year=&month=` | Org-medlem (scoped) | Nei |
| GET | `/api/reports/yearly` | Org-medlem (scoped) | Nei |
| GET | `/api/reports/mva?year=&month=` | Org-medlem (scoped) | Nei |
| GET | `/api/exchange-rate?currency=&date=` | Org-medlem | Nei |
| GET | `/api/organizations` | Autentisert | Nei |
| POST | `/api/organizations` | Superadmin | Ja |
| PUT | `/api/organizations/{id}` | Superadmin | Ja |
| GET | `/api/organizations/{id}/members` | Superadmin / org-oppretter | Nei |
| POST | `/api/organizations/{id}/members` | Superadmin / org-oppretter | Ja |
| DELETE | `/api/organizations/{id}/members/{userId}` | Superadmin / org-oppretter | Ja |

## Konvensjoner

- Exposed DSL for database (ikke DAO)
- `TimeUtils.nowOslo()` for konsistent norsk tid
- API-stier i frontend uten `/api`-prefix (apiClient legger det til)
- CSRF-verifisering: Alle POST/PUT/DELETE-routes kaller `verifyCsrf(authService)`. Frontend sender X-CSRF-Token header.
- AuditLog: Alle CUD-operasjoner på transaksjoner, kategorier, organisasjoner og vedlegg logges via `AuditLogService`
- Dev-modus: OTP "123456" fungerer kun når `DEV_MODE=true`
- SQL-søk escaper `%`, `_` og `\` for å forhindre wildcard-injection

## Porter

| Tjeneste | Port |
|----------|------|
| Nginx (dev) | 9200 |
| Backend | 8083 |
| Frontend | 5175 |

## Miljøvariabler

Se `.env.example` for komplett mal.

- `JWT_SECRET` — **Påkrevd**. Applikasjonen kaster `IllegalStateException` ved oppstart hvis den mangler.
- `DEV_MODE` — Sett til `true` for dev-modus (OTP "123456" fungerer). Erstatter tidligere `KTOR_ENV`.

## Cloudflare Tunnel

Service URL for public hostname: `http://nginx:80`
