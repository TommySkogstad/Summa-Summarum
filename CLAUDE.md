# CLAUDE.md

## Prosjektoversikt

**Summa Summarum** - Enkelt regnskapssystem for bedrift/forening.

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
    routes/    AuthRoutes.kt, HealthRoutes.kt, CategoryRoutes.kt, TransactionRoutes.kt, ReportRoutes.kt
    services/  AuthService.kt, EmailService.kt, CategoryService.kt, TransactionService.kt, ReportService.kt, RateLimiter.kt
    utils/     TimeUtils.kt, Validators.kt
  frontend/src/
    App.tsx, main.tsx, index.css
    api/       apiClient.ts, auth.ts, categories.ts, transactions.ts, reports.ts
    context/   AuthContext.tsx
    components/ ProtectedRoute.tsx, Layout.tsx, Sidebar.tsx
    pages/     Login.tsx, Dashboard.tsx, Transactions.tsx, TransactionForm.tsx, Categories.tsx, Reports.tsx
    lib/       queryClient.ts, formatters.ts
```

## Database (6 tabeller)

| Tabell | Formål |
|--------|--------|
| Users | Brukere med OTP-felt (kun ADMIN-rolle) |
| Categories | Kontoplan (code, name, type=INNTEKT/UTGIFT, active, isDefault) |
| Transactions | Transaksjoner (date, type, amount, description, categoryId) |
| Attachments | Bilagsvedlegg (transactionId, filename, originalName, mimeType) |
| AuditLogs | Sporingslogg |
| EmailLog | E-postlogg |

## API-endepunkter

| Metode | Endepunkt | Beskrivelse |
|--------|-----------|-------------|
| POST | `/api/auth/request-code` | Be om OTP-kode |
| POST | `/api/auth/verify-code` | Verifiser OTP |
| POST | `/api/auth/logout` | Logg ut |
| GET | `/api/auth/me` | Hent innlogget bruker |
| GET/POST | `/api/categories` | Liste/opprett kategorier |
| PUT/DELETE | `/api/categories/{id}` | Oppdater/slett kategori |
| GET/POST | `/api/transactions` | Liste/opprett transaksjoner |
| GET/PUT/DELETE | `/api/transactions/{id}` | Hent/oppdater/slett transaksjon |
| POST | `/api/transactions/{id}/attachments` | Last opp vedlegg |
| GET/DELETE | `/api/transactions/{id}/attachments/{aid}` | Hent/slett vedlegg |
| GET | `/api/reports/overview` | Dashboard-oversikt |
| GET | `/api/reports/monthly?year=` | Månedsrapport |
| GET | `/api/reports/categories?year=&month=` | Kategorirapport |
| GET | `/api/reports/yearly` | Årsrapport |

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
