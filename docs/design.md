# Africe Merch Store — System Design

Complete technical design document for the Africe artist merchandise e-commerce platform.

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Architecture](#2-architecture)
3. [Backend Design](#3-backend-design)
4. [Frontend Design](#4-frontend-design)
5. [Data Models](#5-data-models)
6. [API Design](#6-api-design)
7. [Authentication & Security](#7-authentication--security)
8. [Business Logic](#8-business-logic)
9. [External Integrations](#9-external-integrations)
10. [Infrastructure & Deployment](#10-infrastructure--deployment)
11. [Observability](#11-observability)
12. [Known Gaps & Future Work](#12-known-gaps--future-work)

---

## 1. System Overview

### Purpose

Africe is an online merch store for Ukrainian music artists. It provides a public storefront for customers to browse products and place orders, and an admin panel for managing inventory, artists, and order fulfillment.

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                          NGINX (port 80)                        │
│                     Reverse proxy + routing                     │
├──────────────────────────────┬──────────────────────────────────┤
│   /api/*, /actuator/*,       │   / (everything else)            │
│   /swagger-ui/*, /api-docs   │                                  │
│           ↓                  │           ↓                      │
│   Spring Boot (port 8080)    │   Next.js (port 3000)            │
│   Backend API                │   Frontend SSR + SPA             │
└──────────────────────────────┴──────────────────────────────────┘
         │              │              │              │
    MongoDB         AWS S3       Nova Poshta     Telegram
    Atlas           (images)     API (shipping)  Bot API
```

### Tech Stack Summary

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.4.3, Gradle (Kotlin DSL) |
| Frontend | Next.js 16, React 19, TypeScript |
| Database | MongoDB (Atlas) |
| Object Storage | AWS S3 (eu-north-1) |
| Shipping | Nova Poshta REST API |
| Notifications | Telegram Bot API |
| Reverse Proxy | Nginx |
| Process Manager | systemd |
| Styling | Tailwind CSS |
| State Management | Zustand + TanStack React Query |
| Animations | Framer Motion |

---

## 2. Architecture

### Backend: Modular Monolith

The backend is a single Spring Boot application organized as a multi-module Gradle project. All modules compile into one deployable JAR but maintain clear boundaries.

```
africa-backend/
├── app/                 # Bootstrap, config, seeders
├── common/              # Shared models, DTOs, enums, audit aspect
├── auth-service/        # JWT authentication, admin users
├── product-service/     # Product & artist catalog
├── order-service/       # Checkout, Nova Poshta integration
├── admin-service/       # Admin CRUD, dashboard, S3 uploads
└── telegram-service/    # Bot commands, async notifications
```

**Module dependency graph:**

```
app
 ├── admin-service
 │    ├── product-service
 │    ├── order-service
 │    └── auth-service
 ├── telegram-service
 │    ├── auth-service
 │    └── common
 └── common (transitive to all)
```

### Frontend: Next.js App Router

Single Next.js application serving both the public storefront and admin panel.

```
africa-frontend/
└── src/
    ├── app/             # Pages (App Router)
    │   ├── (public)     # Storefront pages
    │   └── admin/       # Admin panel pages
    ├── components/      # React components by feature
    ├── lib/api/         # API client (public + admin)
    ├── hooks/           # React Query hooks
    ├── store/           # Zustand stores (auth, cart)
    └── types/           # TypeScript interfaces
```

### Communication Patterns

| Pattern | Where Used |
|---------|-----------|
| Synchronous REST | Frontend ↔ Backend (all API calls) |
| Direct method calls | Inter-module communication within backend |
| Outbox pattern (async) | Order events → Telegram notifications |
| Presigned URLs | Frontend → S3 direct upload (bypasses backend) |
| Polling | Telegram bot command polling (3s), Outbox worker (5s) |

---

## 3. Backend Design

### Module Responsibilities

#### `common`
Shared across all modules. Contains:
- **Domain models**: Product, Order, Artist, OrderItem, ShippingDetails, ProductVariant, ProductAttribute
- **Enums**: ProductStatus, OrderStatus, OutboxStatus
- **DTOs**: All request/response objects
- **Audit**: AuditLog model, @AdminAudited annotation, AuditAspect (AOP)
- **Config**: MongoConfig (write concern MAJORITY), JacksonConfig, CorsConfig

#### `auth-service`
- AdminUser + RefreshToken models and repositories
- JwtService — token generation/validation (JJWT, HS256)
- JwtAuthenticationFilter — extracts and validates JWT from requests
- SecurityConfig — defines public vs protected endpoint patterns
- AuthController — login and refresh endpoints

#### `product-service`
- ProductRepository + ArtistRepository (Spring Data MongoDB)
- ProductService — CRUD, slug generation (Ukrainian → Latin transliteration), stock queries
- ArtistService — CRUD, slug generation
- Public controllers for storefront (active products only)

#### `order-service`
- OrderRepository + InventoryTransactionRepository
- OrderService — checkout flow with atomic stock decrement
- NovaPoshtaClient — REST client for city/warehouse search
- Public controllers for checkout and shipping lookup

#### `admin-service`
- Admin controllers wrapping product/order/artist services
- DashboardController — aggregated revenue/order statistics
- ImageController + S3PresignService — presigned URL generation
- All mutating endpoints annotated with @AdminAudited

#### `telegram-service`
- TelegramClient — HTTP client for Telegram Bot API
- BotCommandPoller — polls for bot commands every 3 seconds
- CreateAdminCommand — creates admin accounts via `/createadmin` chat command
- OutboxWorker — polls outbox_events every 5 seconds, dispatches to handlers
- TelegramNotificationHandler — formats and sends order notifications
- Circuit breaker (Resilience4j) wraps all Telegram API calls

### Spring Security Configuration

```
Public (no auth):
  /api/v1/products/**
  /api/v1/artists/**
  /api/v1/orders/**
  /api/v1/auth/**
  /actuator/**
  /swagger-ui/** , /api-docs

Protected (JWT required):
  /api/v1/admin/**
```

CORS configured via `cors.allowed-origins` property. CSRF disabled (stateless JWT). Sessions disabled.

---

## 4. Frontend Design

### Pages & Routes

#### Public Storefront

| Route | Component | Description |
|-------|-----------|-------------|
| `/` | Homepage | Hero section + product feed grid |
| `/product/[slug]` | ProductDetail | Image gallery, variant selector, add to cart |
| `/artist/[slug]` | ArtistProfile | Bio, social links, artist's products |
| `/checkout` | CheckoutForm | Multi-step: contacts → shipping → review |
| `/order/[id]` | OrderTracking | Order confirmation and status |

#### Admin Panel

| Route | Component | Description |
|-------|-----------|-------------|
| `/admin/login` | LoginForm | Email/password authentication |
| `/admin` | Dashboard | Revenue stats, top products, daily breakdown |
| `/admin/products` | ProductList | Paginated table with search/filter |
| `/admin/products/new` | ProductForm | Create with variants, attributes, images |
| `/admin/products/[id]` | ProductForm | Edit existing product |
| `/admin/artists` | ArtistList | Paginated table |
| `/admin/artists/new` | ArtistForm | Create with social links |
| `/admin/artists/[id]` | ArtistForm | Edit existing artist |
| `/admin/orders` | OrderList | Filterable by status/email |
| `/admin/orders/[id]` | OrderDetail | View details, update status |

### State Management

**Zustand stores** (persisted to localStorage):
- `useAuthStore` — accessToken, refreshToken (key: `admin-auth`)
- `useCartStore` — cart items, isOpen, add/remove/update/clear (key: `africa-cart`)

**TanStack React Query** — all server data fetching:
- 5-minute stale time
- Refetch on window focus disabled
- Custom hooks per resource: `useProducts`, `useArtists`, `useOrders`, `useDashboard`

### API Client Architecture

Two fetch wrappers:

1. **`apiClient`** (public) — generic fetch with error handling, throws `ApiRequestError`
2. **`adminClient`** (authenticated) — injects Bearer token, auto-refreshes on 401, retries original request, redirects to `/admin/login` on auth failure

Both target `NEXT_PUBLIC_API_URL` (default: `http://localhost:8080`).

### Design System

- Tailwind CSS with custom palette: pearl, coral, emerald, stone
- Custom typography utilities: `.text-h1-hero`, `.text-h2-section`
- Fonts: Inter (body), Plus Jakarta Sans (headings), Space Grotesk (accents) — all with Cyrillic
- Framer Motion page transitions and micro-interactions
- Cart drawer with backdrop blur

---

## 5. Data Models

### MongoDB Collections

```
┌─────────────────────────────────────────────────────────────┐
│                        products                              │
├─────────────────────────────────────────────────────────────┤
│ _id          : ObjectId (PK)                                │
│ slug         : String (unique index)                        │
│ title        : String                                       │
│ description  : String                                       │
│ basePrice    : BigDecimal                                   │
│ attributes   : [{type: String, values: [String]}]           │
│ variants     : [{sku: String, attributes: Map<String,       │
│                  String>, priceModifier: BigDecimal,         │
│                  stock: int}]                                │
│ images       : [String]  (S3 URLs)                          │
│ artistId     : String (indexed)                             │
│ status       : String (DRAFT | ACTIVE | ARCHIVED) (indexed) │
│ createdAt    : Instant                                      │
│ updatedAt    : Instant                                      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                         orders                               │
├─────────────────────────────────────────────────────────────┤
│ _id          : ObjectId (PK)                                │
│ firstName    : String                                       │
│ lastName     : String                                       │
│ email        : String (indexed)                             │
│ phone        : String                                       │
│ items        : [{productId, productTitle, sku, variantName, │
│                  quantity: int, unitPrice: BigDecimal}]      │
│ totalAmount  : BigDecimal                                   │
│ status       : String (indexed)                             │
│ shippingDetails : {city, cityRef, warehouseRef,             │
│                    warehouseDescription, country, carrier,   │
│                    trackingNumber}                           │
│ comment      : String                                       │
│ createdAt    : Instant (indexed)                            │
│ updatedAt    : Instant                                      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                        artists                               │
├─────────────────────────────────────────────────────────────┤
│ _id          : ObjectId (PK)                                │
│ slug         : String (unique index)                        │
│ name         : String                                       │
│ bio          : String                                       │
│ image        : String (S3 URL)                              │
│ socialLinks  : Map<String, String>                          │
│ createdAt    : Instant                                      │
│ updatedAt    : Instant                                      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                      admin_users                             │
├─────────────────────────────────────────────────────────────┤
│ _id          : ObjectId (PK)                                │
│ email        : String (unique)                              │
│ password     : String (BCrypt)                              │
│ name         : String                                       │
│ createdAt    : Instant                                      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    refresh_tokens                             │
├─────────────────────────────────────────────────────────────┤
│ _id          : ObjectId (PK)                                │
│ adminUserId  : String                                       │
│ token        : String (unique)                              │
│ expiresAt    : Instant                                      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                     outbox_events                            │
├─────────────────────────────────────────────────────────────┤
│ _id          : ObjectId (PK)                                │
│ type         : String (e.g. "ORDER_CREATED")                │
│ payload      : String (JSON)                                │
│ status       : String (PENDING | PROCESSING | SENT | FAILED)│
│ retryCount   : int (max 5)                                  │
│ createdAt    : Instant                                      │
│ processedAt  : Instant                                      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                inventory_transactions                        │
├─────────────────────────────────────────────────────────────┤
│ _id          : ObjectId (PK)                                │
│ productId    : String                                       │
│ sku          : String                                       │
│ change       : int (negative for sales)                     │
│ orderId      : String                                       │
│ createdAt    : Instant                                      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                      audit_logs                              │
├─────────────────────────────────────────────────────────────┤
│ _id          : ObjectId (PK)                                │
│ adminId      : String                                       │
│ adminEmail   : String                                       │
│ action       : String (e.g. "CREATE_PRODUCT")               │
│ targetType   : String                                       │
│ targetId     : String                                       │
│ details      : String                                       │
│ createdAt    : Instant (indexed)                            │
└─────────────────────────────────────────────────────────────┘
```

### Entity Relationships

```
Artist  1 ──────── * Product
                      │
                      │ (snapshot at checkout)
                      ↓
Order  1 ──────── * OrderItem ──→ productId, productTitle, unitPrice
  │
  └── 1 ──── 1 ShippingDetails (embedded)

Product variant stock change ──→ InventoryTransaction (audit trail)
Order creation ──→ OutboxEvent ──→ Telegram notification
Admin mutation ──→ AuditLog (via @AdminAudited AOP)
AdminUser 1 ──── * RefreshToken
```

### Enums

**ProductStatus**: `DRAFT` → `ACTIVE` → `ARCHIVED`

**OrderStatus** (state machine):
```
PENDING ──→ CONFIRMED ──→ SHIPPED ──→ DELIVERED
   │            │            │
   └────────────┴────────────┘
                │
            CANCELLED
```

Valid transitions:
- PENDING → CONFIRMED, CANCELLED
- CONFIRMED → SHIPPED, CANCELLED
- SHIPPED → DELIVERED, CANCELLED
- DELIVERED → (terminal)
- CANCELLED → (terminal)

---

## 6. API Design

### Conventions

- Base path: `/api/v1`
- Content-Type: `application/json`
- Pagination: `?page=0&size=20&sort=field,direction`
- Error format: `{ status, error, message, timestamp }`
- Null fields omitted from responses (Jackson NON_NULL)
- Dates: ISO-8601 (`2026-03-16T12:00:00Z`)

### Endpoint Summary

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| **Public** | | | |
| GET | `/api/v1/products` | — | List active products (paginated, searchable) |
| GET | `/api/v1/products/{slug}` | — | Get product by slug |
| GET | `/api/v1/artists` | — | List all artists |
| GET | `/api/v1/artists/{slug}` | — | Get artist by slug |
| GET | `/api/v1/artists/{slug}/products` | — | Artist's products (paginated) |
| POST | `/api/v1/orders/checkout` | — | Create guest order |
| GET | `/api/v1/orders/{id}` | — | Get order by ID |
| GET | `/api/v1/orders/nova-poshta/cities` | — | Search Nova Poshta cities |
| GET | `/api/v1/orders/nova-poshta/warehouses` | — | Get warehouses for city |
| **Auth** | | | |
| POST | `/api/v1/auth/login` | — | Admin login → tokens |
| POST | `/api/v1/auth/refresh` | — | Refresh access token |
| **Admin** | | | |
| GET | `/api/v1/admin/products` | JWT | List all products (inc. DRAFT/ARCHIVED) |
| POST | `/api/v1/admin/products` | JWT | Create product (status=DRAFT) |
| PUT | `/api/v1/admin/products/{id}` | JWT | Update product (partial) |
| DELETE | `/api/v1/admin/products/{id}` | JWT | Archive product (soft delete) |
| GET | `/api/v1/admin/artists` | JWT | List artists (paginated) |
| POST | `/api/v1/admin/artists` | JWT | Create artist |
| PUT | `/api/v1/admin/artists/{id}` | JWT | Update artist (partial) |
| DELETE | `/api/v1/admin/artists/{id}` | JWT | Delete artist (hard delete) |
| GET | `/api/v1/admin/orders` | JWT | List orders (filterable) |
| PUT | `/api/v1/admin/orders/{id}/status` | JWT | Update order status |
| GET | `/api/v1/admin/dashboard/stats` | JWT | Dashboard analytics |
| POST | `/api/v1/admin/products/images/presign` | JWT | S3 presigned upload URL |
| **Monitoring** | | | |
| GET | `/actuator/health` | — | Health check |
| GET | `/actuator/prometheus` | — | Prometheus metrics |

---

## 7. Authentication & Security

### JWT Authentication

```
┌──────────┐    POST /auth/login     ┌──────────┐
│  Admin    │ ──────────────────────→ │  Backend  │
│  Browser  │ ←────────────────────── │          │
│          │   {accessToken,          │          │
│          │    refreshToken}         │          │
│          │                          │          │
│          │  GET /admin/* + Bearer   │          │
│          │ ──────────────────────→  │          │
│          │ ←────────────────────── │          │
│          │   200 OK (data)         │          │
│          │                          │          │
│          │  GET /admin/* (expired)  │          │
│          │ ──────────────────────→  │          │
│          │ ←────────────────────── │          │
│          │   401 Unauthorized       │          │
│          │                          │          │
│          │  POST /auth/refresh      │          │
│          │ ──────────────────────→  │          │
│          │ ←────────────────────── │          │
│          │   {new accessToken}      │          │
└──────────┘                          └──────────┘
```

- **Algorithm**: HMAC-SHA256 (HS256)
- **Access token TTL**: 15 minutes
- **Refresh token TTL**: 7 days (stored in DB)
- **Password hashing**: BCrypt (strength 10)
- Frontend auto-refreshes on 401 via `adminClient` interceptor

### Security Measures

| Area | Implementation |
|------|---------------|
| Auth | JWT with short-lived access tokens |
| Password | BCrypt hashing |
| CORS | Configurable allowed origins |
| CSRF | Disabled (stateless API) |
| Sessions | Stateless (no server-side sessions) |
| S3 uploads | Presigned URLs (15min expiry), content-type whitelist |
| File names | Sanitized (special chars removed), UUID in path |
| Telegram commands | Chat ID whitelist |
| Admin audit | AOP-based logging of all mutations |
| Write concern | MongoDB MAJORITY (data durability) |

---

## 8. Business Logic

### Checkout Flow

```
Customer submits checkout form
          │
          ↓
┌─────────────────────────────┐
│  Validate request payload   │
│  (required fields, email)   │
└────────────┬────────────────┘
             │
             ↓
┌─────────────────────────────┐
│  For each item:             │
│  Atomic stock decrement     │
│  WHERE id=productId         │
│    AND variants.sku=sku     │←── If stock insufficient → 400 error
│    AND variants.stock >= qty│    (entire order rejected)
│  SET variants.$.stock -= qty│
└────────────┬────────────────┘
             │
             ↓
┌─────────────────────────────┐
│  Snapshot product data:     │
│  - productTitle             │
│  - unitPrice (base + mod)   │
│  - variantName              │
│  Calculate totalAmount      │
└────────────┬────────────────┘
             │
             ↓
┌─────────────────────────────┐
│  Create Order (PENDING)     │
│  Create InventoryTransaction│
│  Create OutboxEvent         │
│  (ORDER_CREATED)            │
└────────────┬────────────────┘
             │
             ↓
      Return OrderResponse (201)
```

Key design decisions:
- **Atomic stock decrement** using MongoDB's positional operator (`$`) prevents overselling
- **Price snapshot** in OrderItem makes orders immutable to future price changes
- **Outbox pattern** decouples notification from checkout (checkout never fails due to Telegram)

### Async Notification Pipeline

```
OutboxWorker (@Scheduled every 5s)
          │
          ↓
   Find PENDING event
   (atomic findAndModify → PROCESSING)
          │
          ↓
   Parse payload, route by event type
          │
          ↓
   TelegramNotificationHandler
   sends message to configured chat IDs
          │
     ┌────┴────┐
     ↓         ↓
   Success   Failure
     │         │
     ↓         ↓
   SENT    retryCount++
           (retry up to 5×, then FAILED)
```

### Image Upload Flow

```
Admin                     Backend                    AWS S3
  │                          │                          │
  │  POST /presign           │                          │
  │  {fileName, contentType} │                          │
  │ ────────────────────────→│                          │
  │                          │  Generate presigned URL  │
  │                          │  (15 min expiry)         │
  │  {uploadUrl, publicUrl}  │                          │
  │ ←────────────────────────│                          │
  │                          │                          │
  │  PUT uploadUrl + file    │                          │
  │ ──────────────────────────────────────────────────→ │
  │                          │                          │
  │  Use publicUrl in product.images                    │
  │                          │                          │
```

S3 key pattern: `products/{uuid}/{sanitized-filename}`

### Admin Audit Trail

All admin mutations are tracked via AOP:
1. Controller method annotated with `@AdminAudited(action = "CREATE_PRODUCT")`
2. `AuditAspect` intercepts `@AfterReturning`
3. Extracts admin ID/email from security context
4. Writes `AuditLog` entry asynchronously

---

## 9. External Integrations

### MongoDB Atlas

- **Connection**: Cloud-hosted MongoDB Atlas cluster
- **Write concern**: MAJORITY (ensures data written to majority of replicas)
- **Auto-index**: Enabled — indexes defined via `@Indexed` annotations
- **Driver**: Spring Data MongoDB (reactive not used)

### AWS S3

- **Bucket**: `africa-shop-dev` (eu-north-1)
- **SDK**: AWS SDK v2 (S3Presigner)
- **Public access**: `s3:GetObject` on `products/*` prefix
- **CORS**: Configured for frontend origin (PUT method)
- **IAM**: Dedicated `africa-backend-s3` user with minimal permissions (Put/Get/Delete on products/*)

### Nova Poshta

- **Type**: Ukrainian postal/shipping service
- **API**: REST (`https://api.novaposhta.ua/v2.0/json/`)
- **Methods used**:
  - `searchSettlements` — city autocomplete
  - `getWarehouses` — warehouse selection for a city
- **Integration**: Backend acts as proxy (frontend calls backend, backend calls Nova Poshta)
- **Client**: Spring RestClient

### Telegram Bot

- **Bot commands** (polled every 3s):
  - `/createadmin email@example.com [name]` — creates admin with random password
  - `/help` — lists available commands
- **Notifications** (outbox-driven):
  - Order created → formatted message with customer + items + total
- **Security**: Chat ID whitelist for command authorization
- **Resilience**: Circuit breaker (50% failure threshold, 30s open state, 5-call window)

---

## 10. Infrastructure & Deployment

### Deployment Architecture

```
┌──────────────────────────────────────────────┐
│              VPS / Server                     │
│                                              │
│  ┌────────────┐                              │
│  │   Nginx    │ :80                          │
│  │            │──→ /api/*    → :8080 (Java)  │
│  │            │──→ /*        → :3000 (Node)  │
│  └────────────┘                              │
│                                              │
│  ┌────────────┐    ┌────────────┐            │
│  │ Spring Boot│    │  Next.js   │            │
│  │  (systemd) │    │ (standalone│            │
│  │  :8080     │    │  output)   │            │
│  └────────────┘    │  :3000     │            │
│                    └────────────┘            │
└──────────────────────────────────────────────┘
         │                │
    MongoDB Atlas     AWS S3 (CDN)
    (external)        (external)
```

### Nginx Configuration

- Proxies `/api/`, `/actuator/`, `/swagger-ui/`, `/api-docs` → Spring Boot (:8080)
- Proxies everything else → Next.js (:3000)
- Client max body size: 20MB
- Forwards `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`

### systemd Service

- **Unit**: `africa-backend.service`
- **User**: `deploy`
- **Working dir**: `/opt/africa/africa-backend`
- **Command**: `java -jar app/build/libs/app-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod`
- **Env file**: `/opt/africa/africa-backend/.env.prod`
- **Restart**: always, 5s delay

### Environment Configuration

| Profile | Seed Data | Log Level | Source |
|---------|-----------|-----------|--------|
| local | Enabled | DEBUG | application.yml + application-local.yml |
| prod | Disabled | INFO | application-prod.yml + .env.prod |

**Required environment variables** (prod):
```
MONGODB_URI
JWT_SECRET
AWS_S3_BUCKET
AWS_S3_REGION
AWS_ACCESS_KEY
AWS_SECRET_KEY
TELEGRAM_BOT_TOKEN
TELEGRAM_CHAT_IDS
CORS_ALLOWED_ORIGINS
```

### Build & Run

```bash
# Backend
./gradlew :app:bootJar
java -jar app/build/libs/app-0.0.1-SNAPSHOT.jar

# Frontend
npm run build    # produces standalone output
npm run start    # serves on port 3000
```

---

## 11. Observability

### Health & Metrics

| Endpoint | Purpose |
|----------|---------|
| `GET /actuator/health` | Service health (UP/DOWN) |
| `GET /actuator/metrics` | JVM and application metrics |
| `GET /actuator/prometheus` | Prometheus scrape endpoint |

### Logging

- Backend: SLF4J with Spring Boot defaults
- `com.africe.backend`: DEBUG (local), INFO (prod)
- `org.springframework.data.mongodb`: INFO

### API Documentation

- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/api-docs`
- Auto-generated from Spring controller annotations (SpringDoc 2.8.4)

### Circuit Breaker (Telegram)

| Parameter | Value |
|-----------|-------|
| Failure rate threshold | 50% |
| Minimum calls | 3 |
| Wait duration (open) | 30 seconds |
| Sliding window size | 5 calls |

---

## 12. Known Gaps & Future Work

### Current Limitations

| Area | Gap | Impact |
|------|-----|--------|
| **Payments** | No payment gateway integration | Orders are created without payment verification. Manual confirmation via admin panel. |
| **Tests** | No unit or integration tests | No automated quality assurance. |
| **CI/CD** | No pipeline configured | Manual build and deploy. |
| **Dashboard auth** | `/api/v1/admin/dashboard/stats` lacks JWT check | Dashboard stats accessible without authentication. |
| **Nova Poshta API key** | Hardcoded in application.yml | Should be an environment variable. |
| **Email notifications** | None | Customers don't receive order confirmation or status update emails. |
| **Search** | Basic title substring match | No full-text search, no filters by price/attribute/artist on public API. |
| **Rate limiting** | None | Public endpoints unprotected from abuse. |
| **Stock restoration** | No stock rollback on cancellation | Cancelled order items don't return stock to inventory. |
| **Redis** | Not implemented (was in original spec) | No caching layer for product catalog. |

### Recommended Next Steps

1. **Payment integration** — Stripe, LiqPay, or Monobank for Ukrainian market
2. **Test suite** — Unit tests for services, integration tests for checkout flow
3. **CI/CD pipeline** — GitHub Actions for build, test, deploy
4. **Fix dashboard auth** — Add JWT requirement to DashboardController
5. **Move Nova Poshta API key to env var**
6. **Customer email notifications** — Order confirmation + status updates
7. **Stock restoration on cancellation** — Atomic increment on cancel
8. **Redis cache** — Product catalog cache-aside pattern
9. **Rate limiting** — Spring Cloud Gateway or bucket4j for public endpoints
10. **Full-text search** — MongoDB Atlas Search or Elasticsearch
