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
    services/  AuthService.kt, EmailService.kt, CategoryService.kt, TransactionService.kt, ReportService.kt, OrganizationService.kt, DocumentParserService.kt, ExchangeRateService.kt, RateLimiter.kt
    utils/     TimeUtils.kt, Validators.kt
  frontend/src/
    App.tsx, main.tsx, index.css
    api/       apiClient.ts, auth.ts, categories.ts, transactions.ts, reports.ts, organizations.ts
    context/   AuthContext.tsx
    components/ ProtectedRoute.tsx, Layout.tsx, Sidebar.tsx
    pages/     Login.tsx, Dashboard.tsx, Transactions.tsx, TransactionForm.tsx, Categories.tsx, Reports.tsx, Organizations.tsx, OrgMembers.tsx
    lib/       queryClient.ts, formatters.ts
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

| Metode | Endepunkt | Tilgang |
|--------|-----------|---------|
| POST | `/api/auth/request-code` | Offentlig |
| POST | `/api/auth/verify-code` | Offentlig |
| POST | `/api/auth/logout` | Offentlig |
| GET | `/api/auth/me` | Autentisert |
| POST | `/api/auth/switch-org/{orgId}` | Autentisert medlem/superadmin |
| GET/POST | `/api/categories` | GET: Admin+, POST: Superadmin |
| PUT/DELETE | `/api/categories/{id}` | Superadmin |
| GET/POST | `/api/transactions` | Org-medlem (scoped) |
| GET/PUT/DELETE | `/api/transactions/{id}` | Org-medlem (scoped) |
| POST | `/api/transactions/{id}/attachments` | Org-medlem (scoped) |
| GET/DELETE | `/api/transactions/{id}/attachments/{aid}` | Org-medlem (scoped) |
| GET | `/api/reports/overview` | Org-medlem (scoped) |
| GET | `/api/reports/monthly?year=` | Org-medlem (scoped) |
| GET | `/api/reports/categories?year=&month=` | Org-medlem (scoped) |
| GET | `/api/reports/yearly` | Org-medlem (scoped) |
| GET | `/api/reports/mva?year=&month=` | Org-medlem (scoped) |
| GET | `/api/exchange-rate?currency=&date=` | Org-medlem |
| GET | `/api/organizations` | Autentisert |
| POST | `/api/organizations` | Superadmin |
| PUT | `/api/organizations/{id}` | Superadmin |
| GET | `/api/organizations/{id}/members` | Superadmin / org-oppretter |
| POST | `/api/organizations/{id}/members` | Superadmin / org-oppretter |
| DELETE | `/api/organizations/{id}/members/{userId}` | Superadmin / org-oppretter |

## Konvensjoner

- Exposed DSL for database (ikke DAO)
- `TimeUtils.nowOslo()` for konsistent norsk tid
- API-stier i frontend uten `/api`-prefix (apiClient legger det til)
- CSRF-token på POST/PUT/DELETE via X-CSRF-Token header
- Dev-modus: OTP "123456" fungerer alltid

## Porter

| Tjeneste | Port |
|----------|------|
| Nginx (dev) | 9200 |
| Backend | 8083 |
| Frontend | 5175 |

## Miljøvariabler

Se `.env.example` for komplett mal.

## Cloudflare Tunnel

Service URL for public hostname: `http://nginx:80`
