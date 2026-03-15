# Africe Merch Store Backend - Design Specification

## Overview

Backend for the Africe online merch store. Sells artist merchandise with admin management and Telegram notifications for new orders.

## Stack

- **Runtime:** Java 21
- **Framework:** Spring Boot 3.x
- **Build:** Gradle (Kotlin DSL)
- **Database:** MongoDB (cloud-hosted, write concern `w: "majority"`)
- **Auth:** Spring Security + JWT (access + refresh tokens)
- **Resilience:** Resilience4j (circuit breakers)
- **Monitoring:** Spring Boot Actuator + Micrometer
- **Storage:** AWS S3 (pre-signed URLs)
- **Validation:** Hibernate Validator (Bean Validation)

## Architecture: Multi-Module Gradle Project

```
africa-backend/
├── build.gradle.kts              # Root build config, shared dependencies
├── settings.gradle.kts           # Module declarations
│
├── common/                       # Shared models, DTOs, exceptions
│   └── build.gradle.kts
│
├── auth-service/                 # JWT generation/validation, Spring Security config
│   └── build.gradle.kts
│
├── product-service/              # Product catalog CRUD
│   └── build.gradle.kts
│
├── order-service/                # Order creation & management
│   └── build.gradle.kts
│
├── admin-service/                # Admin controllers, dashboard stats
│   └── build.gradle.kts
│
├── telegram-service/             # Telegram Bot API notifications
│   └── build.gradle.kts
│
└── app/                          # Spring Boot entry point, assembles all modules
    └── build.gradle.kts
```

### Module Dependencies

```
app → all modules
admin-service → auth-service, product-service, order-service
telegram-service → common
order-service → common, product-service
product-service → common
auth-service → common
```

### Module Responsibilities

- **common** — Shared domain entities (Product, Order, InventoryTransaction), DTOs, exception types, application events (`OrderCreatedEvent`), MongoDB base config
- **auth-service** — JWT token generation/validation, Spring Security filter chain, admin user entity and login endpoint
- **product-service** — Product repository (Spring Data MongoDB), product service, public REST controller for catalog browsing
- **order-service** — Order repository, order service with atomic stock updates, checkout controller, publishes `OrderCreatedEvent`
- **admin-service** — Admin REST controllers for product/order management, dashboard stats via MongoDB aggregation pipelines
- **telegram-service** — Telegram Bot API client, listens for `OrderCreatedEvent`, formats and sends notification messages
- **app** — `@SpringBootApplication` entry point, `application.yml` configuration, pulls all modules into a single deployable JAR

## Data Models

### Product (`products` collection)

```java
@Document("products")
public class Product {
    @Id private String id;
    @Indexed(unique = true) private String slug;
    private String title;
    private String description;
    private BigDecimal basePrice;
    private List<ProductAttribute> attributes;
    private List<ProductVariant> variants;
    private List<String> images;
    private ProductStatus status;  // DRAFT, ACTIVE, ARCHIVED
    private Instant createdAt;
    private Instant updatedAt;
}
```

**ProductAttribute:** `{ type: String, values: List<String> }` (e.g., Size: S/M/L)

**ProductVariant:** `{ sku: String, attributes: Map<String, String>, priceModifier: BigDecimal, stock: int }` (e.g., `{ sku: "TSHIRT-BLK-S", attributes: { "Size": "S", "Color": "Black" }, priceModifier: 0, stock: 50 }`)

### Order (`orders` collection)

```java
@Document("orders")
public class Order {
    @Id private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private List<OrderItem> items;
    private BigDecimal totalAmount;
    private OrderStatus status;  // PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
    private ShippingDetails shippingDetails;
    @Indexed private Instant createdAt;
    private Instant updatedAt;
}
```

**OrderItem:** Snapshot of product at purchase time — `{ productId, productTitle, sku, variantName, quantity, unitPrice }`

**ShippingDetails:** `{ address, city, postalCode, country, trackingNumber, carrier }`

### InventoryTransaction (`inventory_transactions` collection)

```java
@Document("inventory_transactions")
public class InventoryTransaction {
    @Id private String id;
    private String productId;
    private String sku;
    private int change;       // negative = sale, positive = restock
    private String orderId;
    private Instant createdAt;
}
```

## API Endpoints

### Public API

| Method | Endpoint | Module | Description |
|--------|----------|--------|-------------|
| GET | `/api/v1/products` | product-service | List active products with filters |
| GET | `/api/v1/products/{slug}` | product-service | Product detail by slug |
| POST | `/api/v1/orders/checkout` | order-service | Create order (guest checkout) |

### Admin API (JWT Protected)

| Method | Endpoint | Module | Description |
|--------|----------|--------|-------------|
| POST | `/api/v1/auth/login` | auth-service | Admin login, returns access + refresh tokens |
| POST | `/api/v1/auth/refresh` | auth-service | Refresh access token |
| GET | `/api/v1/admin/dashboard/stats` | admin-service | Revenue, units sold, top products |
| GET | `/api/v1/admin/products` | admin-service | List all products (including drafts) |
| POST | `/api/v1/admin/products` | admin-service | Create new product |
| PUT | `/api/v1/admin/products/{id}` | admin-service | Update product details/stock |
| DELETE | `/api/v1/admin/products/{id}` | admin-service | Archive product (soft delete) |
| GET | `/api/v1/admin/orders` | admin-service | List orders with search/filter |
| PUT | `/api/v1/admin/orders/{id}/status` | admin-service | Update order status |

## Checkout Flow (V1)

1. Customer browses products via public API
2. Customer submits checkout request with: firstName, lastName, email, phone, items (productId + sku + quantity), shipping address
3. Server validates items exist and have sufficient stock
4. **Multi-item atomicity:** Use a MongoDB multi-document transaction (supported on Atlas replica sets, including free-tier M0) to decrement stock for all items atomically. If any item fails the stock check, the entire transaction is aborted and no stock is modified.
5. Within the transaction: atomic stock decrement via `$inc` with `stock >= quantity` filter for each item
6. Within the transaction: create `InventoryTransaction` records for each item
7. Within the transaction: create `Order` with status `PENDING`
8. Commit transaction. If it fails, return error to customer indicating which items are unavailable.
9. Within the transaction: save an `OutboxEvent` document to `outbox_events` collection (type: `ORDER_CREATED`, payload: order details)
10. Commit transaction. Outbox worker picks up the event and sends Telegram notification.
11. Admin manually confirms/manages order status via admin API

## Atomic Stock Updates

To prevent overselling, use MongoDB's atomic `$inc` with a conditional filter:

```javascript
db.products.updateOne(
  { "variants.sku": "TSHIRT-S", "variants.stock": { $gte: requestedQuantity } },
  { $inc: { "variants.$.stock": -requestedQuantity } }
)
```

If the update matches 0 documents, the item is out of stock — reject the order item.

## Transactional Outbox Pattern

Ensures reliable event delivery even if Telegram API or the service crashes.

### OutboxEvent (`outbox_events` collection)

```java
@Document("outbox_events")
public class OutboxEvent {
    @Id private String id;
    private String type;           // e.g., "ORDER_CREATED"
    private String payload;        // JSON-serialized event data
    private OutboxStatus status;   // PENDING, SENT, FAILED
    private int retryCount;
    private Instant createdAt;
    private Instant processedAt;
}
```

### How it works

1. During checkout transaction, an `OutboxEvent` is saved to `outbox_events` with status `PENDING`
2. A `@Scheduled` worker (every 5 seconds) polls for `PENDING` events
3. Worker processes each event (e.g., sends Telegram notification)
4. On success: marks event as `SENT`
5. On failure: increments `retryCount`, keeps as `PENDING` (up to max 5 retries, then `FAILED`)
6. This guarantees at-least-once delivery

## Telegram Notification Service

- Triggered by the outbox worker when processing `ORDER_CREATED` events
- Sends message via Telegram Bot API (`sendMessage` endpoint)
- Wrapped in `@CircuitBreaker(name = "telegram")` via Resilience4j — if Telegram fails 3 times, circuit opens for 30 seconds to prevent resource exhaustion
- Message format (supports multi-item orders):
  ```
  New Merch Sale!
  Items:
  - {product_name} ({variant}) x{quantity}
  - {product_name} ({variant}) x{quantity}
  Total: {total_price}
  Customer: {firstName} {lastName}
  ```
- Configuration in `application.yml`:
  - `telegram.bot.token` — Bot API token
  - `telegram.bot.chat-ids` — Allowed chat IDs for notifications
- Uses `RestClient` (Spring 6.1+) to call Telegram API

## Security

- **Public routes** (`/api/v1/products/**`, `/api/v1/orders/**`): `permitAll()`
- **Auth route** (`POST /api/v1/auth/login`): `permitAll()` — issues JWT tokens
- **Admin routes** (`/api/v1/admin/**`): `authenticated()` — JWT required
- JWT tokens contain admin user ID and roles
- Spring Security filter chain: JWT validation filter applied before `UsernamePasswordAuthenticationFilter`
- Security config lives in `auth-service`, applied via `@Configuration` auto-detected by `app` module
- CORS configured for frontend origin
- **Refresh Tokens:** Login returns both an access token (short-lived, 15 min) and a refresh token (long-lived, 7 days). `POST /api/v1/auth/refresh` issues a new access token using a valid refresh token. Refresh tokens stored in MongoDB `refresh_tokens` collection.

## Pagination

All list endpoints use Spring Data's `Pageable` with a standard response envelope:

```json
{
  "content": [...],
  "totalElements": 100,
  "totalPages": 10,
  "page": 0,
  "size": 10
}
```

Query parameters: `page` (0-indexed), `size` (default 20, max 100), `sort` (e.g., `createdAt,desc`).

Applies to: `GET /api/v1/products`, `GET /api/v1/admin/products`, `GET /api/v1/admin/orders`.

## Error Response Contract

All error responses use a consistent format:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient stock for SKU TSHIRT-BLK-S",
  "timestamp": "2026-03-15T12:00:00Z"
}
```

Standard HTTP status codes:
- `400` — Validation failure (bad input, insufficient stock)
- `401` — Missing or invalid JWT
- `403` — Valid JWT but insufficient permissions
- `404` — Resource not found
- `409` — Conflict (e.g., concurrent stock modification)
- `500` — Internal server error

Implemented via `@RestControllerAdvice` global exception handler in `common` module.

## Image Storage (AWS S3 — Pre-signed URLs)

Uses **pre-signed URLs** so the frontend uploads directly to S3 — the backend never proxies file data, keeping memory usage low.

### Upload Flow

1. Admin calls `POST /api/v1/admin/products/images/presign` with `{ fileName, contentType }`
2. Backend validates content type (JPEG, PNG, WebP) and generates a pre-signed PUT URL (expires in 15 min)
3. Backend returns `{ uploadUrl, publicUrl }` — the `publicUrl` is the final CDN/S3 URL
4. Frontend uploads the file directly to S3 using the `uploadUrl`
5. Admin includes the `publicUrl` in the product's `images` list when creating/updating products

### Configuration

- `aws.s3.bucket` — S3 bucket name
- `aws.s3.region` — AWS region
- `aws.access-key` / `aws.secret-key` — AWS credentials (or use IAM role)
- S3 bucket must have CORS configured for the frontend origin

### Admin API Addition

| Method | Endpoint | Module | Description |
|--------|----------|--------|-------------|
| POST | `/api/v1/admin/products/images/presign` | admin-service | Generate pre-signed S3 upload URL |

## Audit Log

Every admin action is logged for traceability.

### AuditLog (`audit_logs` collection)

```java
@Document("audit_logs")
public class AuditLog {
    @Id private String id;
    private String adminId;
    private String adminEmail;
    private String action;        // e.g., "UPDATE_PRODUCT", "CHANGE_ORDER_STATUS"
    private String targetType;    // e.g., "Product", "Order"
    private String targetId;
    private String details;       // JSON diff or description of the change
    @Indexed private Instant createdAt;
}
```

### Implementation

- Implemented via a Spring AOP `@Aspect` that intercepts all `@AdminAudited` annotated controller methods
- Automatically captures admin identity from `SecurityContext`, action from annotation metadata, target from method params
- Persisted asynchronously (`@Async`) to avoid adding latency to admin requests

## Dashboard Stats (Advanced Aggregation)

The `GET /api/v1/admin/dashboard/stats` endpoint uses MongoDB `$facet` to return multiple result sets in a single query:

```json
{
  "totalRevenue": 12500.00,
  "totalOrders": 150,
  "totalUnitsSold": 320,
  "topProducts": [
    { "productId": "...", "title": "...", "unitsSold": 45, "revenue": 2250.00 }
  ],
  "revenueByDay": [
    { "date": "2026-03-14", "revenue": 450.00, "orders": 5 }
  ]
}
```

- Supports query params: `from` (date), `to` (date) for time range filtering (default: last 30 days)
- Uses `$facet` aggregation stage for top products + revenue trends in one DB round-trip

## DTO Validation (Hibernate Validator)

All request DTOs use Bean Validation annotations:

```java
public class CheckoutRequest {
    @NotBlank private String firstName;
    @NotBlank private String lastName;
    @NotBlank @Email private String email;
    @NotBlank private String phone;
    @NotEmpty private List<@Valid CheckoutItemRequest> items;
    @NotNull @Valid private ShippingDetailsRequest shippingDetails;
}
```

- `@Valid` on controller method parameters triggers validation
- Validation errors are caught by the global exception handler and returned in the standard error format

## MongoDB Write Concern

For order-critical operations, use write concern `w: "majority"`:

- Configured globally in `application.yml`: `spring.data.mongodb.write-concern=majority`
- Ensures writes are acknowledged by a majority of replica set members before returning success
- Prevents data loss if a primary node fails immediately after a write

## Monitoring (Spring Boot Actuator)

- Spring Boot Actuator enabled with health, info, metrics endpoints
- `GET /actuator/health` — health check (MongoDB connectivity, Redis connectivity)
- `GET /actuator/info` — application info
- Micrometer metrics exported for monitoring integration
- Actuator endpoints secured: only `/actuator/health` is public, rest require admin auth

## Architecture Testing (ArchUnit)

ArchUnit tests enforce module boundaries at compile time:

- Controllers only inject services (never repositories directly)
- Services within one module do not directly access repositories of another module
- No circular dependencies between modules
- Tests live in `common/src/test/` and run as part of `./gradlew test`

## V1 Scope Exclusions (Deferred)

- Payment integration (Monobank — future)
- User registration / accounts
- Webhook endpoints for payment callbacks
- Sharding / advanced scalability
