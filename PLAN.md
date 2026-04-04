# План бекенду — Africa Merch Store

## Поточний стан

В репо є тільки 3 Java файли (Nova Poshta). Решта модулів описана в CLAUDE.md але видалена. Потрібно написати заново.

**Пріоритет №1** — Common + Product модулі. Без працюючого API товарів фронтенд фільтри, sitemap і checkout не працюють.

---

## Що є

- [x] Nova Poshta клієнт з circuit breaker + Caffeine cache
- [x] `NovaPoshtaController` — ендпоінти пошуку міст/відділень
- [x] `NovaWarehouseResponse` DTO
- [x] Gradle multi-module структура (порожні модулі)
- [x] `application.yml` конфіг
- [x] CLAUDE.md з повною документацією архітектури

---

## Що треба зробити

### Б1. Common модуль — моделі, DTO, інфраструктура

**Пакет:** `com.africe.backend.common`

**Domain-моделі** (`common/model/`):
- `Product` — id, slug, title, description, basePrice, attributes[], variants[], images[], artistId, status, createdAt, updatedAt
- `ProductVariant` — sku, attributes (Map), priceModifier, stock
- `ProductAttribute` — type, values[]
- `ProductStatus` enum — ACTIVE, DRAFT, ARCHIVED
- `Order` — id, firstName, lastName, email, phone, items[], totalAmount, status, paymentMethod, shippingDetails, comment, createdAt, updatedAt
- `OrderItem` — productId, productTitle, sku, variantName, quantity, unitPrice
- `OrderStatus` enum — **WAITING_PAYMENT**, PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
- `PaymentMethod` enum — COD, ONLINE
- `ShippingDetails` — city, cityRef, warehouseRef, warehouseDescription, trackingNumber, carrier
- `Artist` — id, slug, name, bio, image, socialLinks (Map), createdAt, updatedAt
- `AdminUser` — id, email, password, name, createdAt
- `RefreshToken` — id, token, adminId, expiresAt
- `OutboxEvent` — id, **channel** (TELEGRAM / EMAIL), type, payload, status, retryCount, createdAt, processedAt
- `OutboxStatus` enum — PENDING, PROCESSING, SENT, FAILED
- `AuditLog` — adminId, adminEmail, action, targetType, targetId, details, timestamp

**DTO** (`common/dto/`):
- `CheckoutRequest` — firstName, lastName, email, phone, items[], shippingDetails, comment, **paymentMethod**
- `CheckoutItemRequest` — productId, sku, quantity
- `ShippingDetailsRequest` — city, cityRef, warehouseRef, warehouseDescription
- `OrderResponse`, `OrderItemResponse`
- `ProductResponse`, `ProductVariantDto`, `ProductAttributeDto`
- `ArtistResponse`
- `LoginRequest`, `AuthResponse`, `RefreshTokenRequest`
- `ErrorResponse`
- `PresignRequest`, `PresignResponse`
- `DashboardStatsResponse`, `TopProductDto`, `RevenueDayDto`
- `ProductFiltersResponse` — artists[], attributes[] (для динамічних фільтрів)
- `UpdateOrderStatusRequest` — status, **trackingNumber** (обов'язковий для SHIPPED)

**Інфраструктура** (`common/`):
- `GlobalExceptionHandler` (@RestControllerAdvice) — ResourceNotFoundException→404, InsufficientStockException→400, PaymentRequiredException→402, Validation→400, generic→500
- `@AdminAudited` анотація + `AuditAspect` — логування адмін-дій
- `MongoConfig`

---

### Б2. Auth модуль

**Пакет:** `com.africe.backend.auth`

**Файли:**
- `JwtService` — generateAccessToken, generateRefreshToken, extractUserId, isTokenValid (jjwt, HMAC-SHA)
- `JwtAuthenticationFilter` — OncePerRequestFilter, читає Bearer token, ставить Authentication в SecurityContext
- `SecurityConfig` — Spring Security: захист /api/v1/admin/** (JWT), /api/v1/auth/** відкритий, /api/v1/** відкритий, /api/v1/payments/callback відкритий (webhook)
- `AuthController`:
  - `POST /api/v1/auth/login` — email+password → {accessToken, refreshToken}
  - `POST /api/v1/auth/refresh` — refreshToken → new accessToken
- `AdminUserRepository`
- `RefreshTokenRepository`

---

### Б3. Product модуль + динамічні фільтри

**Пакет:** `com.africe.backend.product`

**Файли:**
- `ProductRepository` (MongoRepository):
  - findByStatus(status, pageable)
  - findByStatusAndTitleContainingIgnoreCase(status, search, pageable)
  - findByStatusAndArtistId(status, artistId, pageable)
  - findBySlug(slug)
- `ProductService`:
  - listActiveProducts(search, artistId, sort, pageable) → Page<ProductResponse>
  - getBySlug(slug) → ProductResponse
  - **getAvailableFilters()** → ProductFiltersResponse
- `ProductController`:
  - `GET /api/v1/products` — ?search=, ?artistId=, ?sort=price_asc|price_desc|newest, ?page=, ?size=
  - `GET /api/v1/products/{slug}`
  - `GET /api/v1/products/filters` — динамічні фільтри
- `ArtistRepository`, `ArtistService`, `ArtistController`:
  - `GET /api/v1/artists`
  - `GET /api/v1/artists/{slug}`

**Динамічні фільтри — MongoDB Aggregation:**
```
GET /api/v1/products/filters →
{
  artists: [{ id, name, slug }],
  attributes: [
    { type: "Розмір", values: ["S", "M", "L", "XL"] },
    { type: "Колір", values: ["Чорний", "Білий"] }
  ]
}
```

Реалізація через MongoDB aggregation pipeline:
```java
// 1. Фільтруємо тільки ACTIVE товари
// 2. $unwind attributes
// 3. $unwind attributes.values
// 4. $group by attributes.type → collect unique values
// Результат: динамічний список фільтрів з реальних даних
```

Адмін додає новий атрибут "Матеріал" в товарі → він автоматично з'являється у фільтрах без правки коду.

---

### Б4. Order модуль

**Пакет:** `com.africe.backend.order`

**Файли:**
- `OrderRepository` (MongoRepository)
- `OrderService`:
  - **checkout(CheckoutRequest) → OrderResponse:**
    1. Валідація полів
    2. Для кожного item: знайти product, знайти variant по SKU
    3. Атомарне зменшення stock через MongoTemplate.updateFirst() з positional operator:
       ```java
       Query query = new Query(Criteria.where("id").is(productId)
           .and("variants.sku").is(sku)
           .and("variants.stock").gte(quantity));  // ← гарантує що stock >= quantity
       Update update = new Update().inc("variants.$.stock", -quantity);
       UpdateResult result = mongoTemplate.updateFirst(query, update, Product.class);
       // result.getModifiedCount() == 0 означає недостатньо стоку
       ```
       **Важливо:** фільтр `"variants.stock": { $gte: quantity }` запобігає від'ємному залишку на рівні БД
    4. Якщо modifiedCount == 0 → throw InsufficientStockException
    5. **Якщо paymentMethod = ONLINE** → статус `WAITING_PAYMENT`
    6. **Якщо paymentMethod = COD** → статус `PENDING`
    7. Створити OutboxEvent(channel=TELEGRAM, type="ORDER_CREATED", payload=order JSON)
    8. Повернути OrderResponse
  - getOrder(id) → OrderResponse
  - **updateStatus(id, status, trackingNumber?)** → OrderResponse:
    - Якщо новий статус SHIPPED → trackingNumber обов'язковий
    - Зберегти trackingNumber в ShippingDetails
    - Створити OutboxEvent(channel=EMAIL, type=відповідний)
    - Створити OutboxEvent(channel=TELEGRAM, type=відповідний) — для відправки ТТН клієнту
  - **restoreStock(orderId)** — повертає stock при скасуванні
- `CheckoutController`:
  - `POST /api/v1/orders/checkout`
  - `GET /api/v1/orders/{id}`
- `InventoryTransactionRepository` — аудит змін стоку
- Nova Poshta — вже є ✅

**Автоматичне скасування неоплачених замовлень:**
- `ExpiredPaymentCleanupJob` (@Scheduled, кожні 5 хв):
  - Знаходить замовлення зі статусом `WAITING_PAYMENT` старші 30 хвилин
  - Для кожного: status → CANCELLED, restoreStock(), видалити outbox events
  - Лог: "Замовлення #{id} скасовано — оплата не отримана протягом 30 хв"

---

### Б5. Admin модуль

**Пакет:** `com.africe.backend.admin`

**Файли:**
- `AdminProductController`:
  - `GET /api/v1/admin/products` — ?search=, ?status=, пагінація
  - `POST /api/v1/admin/products` @AdminAudited
  - `PUT /api/v1/admin/products/{id}` @AdminAudited
  - `DELETE /api/v1/admin/products/{id}` @AdminAudited (архівація)
- `AdminOrderController`:
  - `GET /api/v1/admin/orders` — ?search=, ?status=, пагінація
  - `GET /api/v1/admin/orders/{id}`
  - **`PUT /api/v1/admin/orders/{id}/status`** @AdminAudited:
    - Body: `{ status: "CONFIRMED|SHIPPED|CANCELLED", trackingNumber?: "20450..." }`
    - Якщо status=SHIPPED і trackingNumber відсутній → 400 "ТТН обов'язковий"
    - Тригерить email + telegram клієнту (через outbox)
- `AdminArtistController`:
  - CRUD для артистів (GET list, POST, PUT, DELETE)
- `DashboardController`:
  - `GET /api/v1/admin/dashboard/stats` — ?from=, ?to= → totalRevenue, totalOrders, topProducts, revenueByDay
- `ImageController`:
  - `POST /api/v1/admin/images/presigned-url` → {uploadUrl, publicUrl}
- `S3PresignService` — AWS SDK v2, генерує presigned PUT URL (15 хв)

---

### Б6. Універсальний Outbox + Telegram модуль

**Пакет:** `com.africe.backend.notification`

**Ключова зміна:** OutboxWorker обробляє і Telegram, і Email. Один outbox, різні канали.

**OutboxEvent types:**
| type | channel | Коли |
|------|---------|------|
| ORDER_CREATED | TELEGRAM | Нове замовлення → повідомлення адміну з кнопками |
| ORDER_CONFIRMED | EMAIL | Адмін підтвердив → "Дякуємо, замовлення обробляється" |
| ORDER_CONFIRMED | TELEGRAM | Адмін підтвердив → повідомлення клієнту |
| ORDER_CANCELLED | EMAIL | Замовлення скасовано → "Замовлення відмінено" |
| ORDER_SHIPPED | EMAIL | Відправлено → "Ваше замовлення відправлено, ТТН: XXX" |
| ORDER_SHIPPED | TELEGRAM | Відправлено → повідомлення клієнту з ТТН |
| ORDER_DELIVERED | EMAIL | Доставлено → "Замовлення доставлено, дякуємо!" |

**Файли:**
- `OutboxWorker`:
  - @Scheduled(fixedDelay = 5000) — поллінг
  - claimNextEvent() — атомарний findAndModify (PENDING → PROCESSING)
  - **Маршрутизація по channel:**
    - TELEGRAM → TelegramNotificationHandler
    - EMAIL → EmailNotificationHandler
  - Retry: max 5 спроб, при помилці status залишається PENDING
  - @EventListener(OutboxEventCreated) — негайна обробка
- `TelegramClient`:
  - sendMessage(chatId, text, inlineKeyboard)
  - answerCallbackQuery(callbackQueryId)
  - editMessageReplyMarkup(chatId, messageId, null)
  - Circuit breaker (Resilience4j)
- `TelegramNotificationHandler`:
  - handleOrderCreated(orderJson): повідомлення адміну + 2 inline-кнопки [✅ Підтвердити] [❌ Відмінити]
  - handleOrderShipped(orderJson): повідомлення клієнту "Замовлення відправлено, ТТН: XXX"
- `BotCallbackHandler`:
  - Polling getUpdates() для callback_query
  - Парсить: `confirm_{orderId}` / `cancel_{orderId}`
  - Викликає OrderService.updateStatus()
  - Видаляє кнопки з повідомлення
  - Відповідає "Замовлення підтверджено ✅" / "Замовлення відмінено ❌"
- `BotCommandPoller` — /create_admin команда

**Flow (повний):**
```
1. Клієнт оформляє замовлення (COD)
   → Order(PENDING) + OutboxEvent(TELEGRAM, ORDER_CREATED)
   → OutboxWorker → Telegram адміну з кнопками

2. Клієнт оформляє замовлення (ONLINE)
   → Order(WAITING_PAYMENT) + OutboxEvent(TELEGRAM, ORDER_CREATED)
   → Redirect на Monobank → Оплата → Webhook → Order(PENDING)
   → Якщо 30 хв без оплати → auto-cancel + restore stock

3. Адмін натискає "Підтвердити" (Telegram або адмінка)
   → Order(CONFIRMED) + OutboxEvent(EMAIL, ORDER_CONFIRMED)
   → Клієнт отримує email "Замовлення обробляється"

4. Адмін натискає "Відправлено" + вводить ТТН
   → Order(SHIPPED, trackingNumber) + OutboxEvent(EMAIL, ORDER_SHIPPED) + OutboxEvent(TELEGRAM, ORDER_SHIPPED)
   → Клієнт отримує email + telegram "Відправлено, ТТН: 204500..."

5. Адмін натискає "Доставлено"
   → Order(DELIVERED) + OutboxEvent(EMAIL, ORDER_DELIVERED)
   → Клієнт отримує email "Доставлено, дякуємо!"
```

---

### Б7. Email-сервіс

**Залежність:** `spring-boot-starter-mail`

**Пакет:** `com.africe.backend.notification.email`

**Файли:**
- `EmailNotificationHandler`:
  - handleOrderConfirmed(order) → "Дякуємо! Замовлення #{id} підтверджено і обробляється"
  - handleOrderCancelled(order) → "Замовлення #{id} відмінено"
  - handleOrderShipped(order) → "Замовлення #{id} відправлено! ТТН: {trackingNumber}"
  - handleOrderDelivered(order) → "Замовлення #{id} доставлено! Дякуємо за покупку!"
  - Простий HTML шаблон: лого, номер замовлення, список товарів, сума, статус
- `EmailProperties` (@ConfigurationProperties):
  - from, host, port, username, password

**Відправка через OutboxWorker** — якщо SMTP впав, повідомлення залишиться в outbox і буде відправлено при наступній спробі (retry).

**Конфіг (application.yml):**
```yaml
spring:
  mail:
    host: ${MAIL_HOST}
    port: ${MAIL_PORT}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
mail:
  from: ${MAIL_FROM}
```

---

### Б8. Monobank Acquiring

**Пакет:** `com.africe.backend.payment`

**Файли:**
- `MonobankClient`:
  - createInvoice(amount, orderId, redirectUrl, webhookUrl) → {invoiceId, pageUrl}
  - POST `https://api.monobank.ua/api/merchant/invoice/create`
  - Header: `X-Token: {MONOBANK_TOKEN}`
  - Circuit breaker (Resilience4j)
- `PaymentController`:
  - `POST /api/v1/payments/create` — {orderId} → {paymentUrl}
    - Перевірити що order існує і status = WAITING_PAYMENT
    - Створити invoice через MonobankClient
    - Повернути pageUrl для redirect
  - `POST /api/v1/payments/callback` — webhook від Monobank (відкритий, без JWT)
    - Валідація підпису (X-Sign header)
    - Якщо status = "success" → Order.status = PENDING → OutboxEvent(TELEGRAM, ORDER_CREATED з кнопками)
    - Якщо status = "failure" → Order.status = CANCELLED, restoreStock()
- `MonobankWebhookValidator`:
  - validateSignature(body, xSignHeader) → boolean
  - Monobank підписує callback тілом запиту + public key
  - **ОБОВ'ЯЗКОВО** валідувати X-Sign перед обробкою — без цього будь-хто може підробити webhook
  - Публічний ключ Monobank: отримати через `GET /api/merchant/pubkey`
  - Алгоритм: ECDSA P-256 SHA-256
- `MonobankProperties` (@ConfigurationProperties):
  - token

**Локальна розробка:** Monobank не може достукатись до localhost. Використовувати:
- `ngrok http 8080` → отримати публічний URL → передати як webhookUrl при створенні invoice
- Або env-змінна `WEBHOOK_BASE_URL` (prod: домен, dev: ngrok URL)

**Нові env-змінні:** `MONOBANK_TOKEN`, `WEBHOOK_BASE_URL`

**Flow:**
```
Checkout(paymentMethod=ONLINE)
  → Order(WAITING_PAYMENT), stock зменшено
  → POST /api/v1/payments/create → Monobank invoice → pageUrl
  → Frontend redirect → клієнт платить на сторінці Monobank
  → Monobank POST /api/v1/payments/callback (success)
  → Order(PENDING) → Telegram адміну з кнопками
  
  АБО: 30 хв без callback → ExpiredPaymentCleanupJob → CANCELLED + restore stock
```

---

## Порядок реалізації

| # | Задача | Модуль | Залежить від |
|---|--------|--------|-------------|
| 1 | Common: моделі, DTO, exceptions, audit | common | — |
| 2 | Auth: JWT, Security, login/refresh | auth-service | Б1 |
| 3 | Product: CRUD, пошук, динамічні фільтри (aggregation) | product-service | Б1 |
| 4 | Order: checkout, stock, WAITING_PAYMENT, auto-cancel | order-service | Б1, Б3 |
| 5 | Admin: CRUD, dashboard, S3, статуси + ТТН | admin-service | Б1-Б4 |
| 6 | Outbox + Telegram: універсальний worker, кнопки, callback | notification | Б1, Б4 |
| 7 | Email: через outbox, retry при помилках | notification | Б1, Б4, Б6 |
| 8 | Monobank: invoice, webhook, auto-cancel expired | payment | Б4 |
| 9 | **HTTP-кеш + Caffeine + gzip** | app, product-service, admin-service | Б3, Б5 |

---

### Б9. HTTP-кешування відповідей (1 година) + gzip

**Мета:** Зменшити навантаження на MongoDB + прискорити відповіді. Публічні GET-ендпоінти кешуються, мутації і адмін — ні.

#### HTTP Cache-Control заголовки

**Файл:** `app/src/main/java/com/africe/backend/config/CacheControlConfig.java`

| Ендпоінт | Cache-Control | TTL |
|----------|--------------|-----|
| `GET /api/v1/products` | `public, max-age=3600` | 1 година |
| `GET /api/v1/products/{slug}` | `public, max-age=3600` | 1 година |
| `GET /api/v1/products/filters` | `public, max-age=3600` | 1 година |
| `GET /api/v1/artists` | `public, max-age=3600` | 1 година |
| `GET /api/v1/artists/{slug}` | `public, max-age=3600` | 1 година |
| `GET /api/v1/orders/{id}` | `no-store` | — |
| `POST /*` | `no-store` | — |
| `/api/v1/admin/**` | `no-store` | — |

#### Caffeine in-memory cache (розширити існуючий)

Додати до існуючого `CacheConfig.java`:
```java
manager.setCacheSpecification("maximumSize=500,expireAfterWrite=3600s");
manager.setCacheNames(List.of(
    "novaPoshtaCities",      // вже є
    "novaPoshtaWarehouses",  // вже є
    "products",              // НОВИЙ
    "productBySlug",         // НОВИЙ
    "productFilters",        // НОВИЙ
    "artists",               // НОВИЙ
    "artistBySlug",          // НОВИЙ
    "dashboardStats"         // НОВИЙ
));
```

**В сервісах:**
```java
@Cacheable("products") listActiveProducts(...)
@Cacheable(value = "productBySlug", key = "#slug") getBySlug(...)
@Cacheable("productFilters") getAvailableFilters()
```

**Інвалідація при мутаціях:**
```java
// AdminProductController create/update/delete:
@CacheEvict(value = {"products", "productBySlug", "productFilters"}, allEntries = true)

// AdminArtistController create/update/delete:
@CacheEvict(value = {"artists", "artistBySlug", "productFilters"}, allEntries = true)

// AdminOrderController status update:
@CacheEvict(value = {"dashboardStats"}, allEntries = true)
```

#### Gzip компресія

`application.yml`:
```yaml
server:
  compression:
    enabled: true
    mime-types: application/json,text/html,text/plain
    min-response-size: 1024
```

---

---

## Виправлення та доповнення (аудит квітень 2026)

### В1. КРИТИЧНІ — без цього не запускати

#### В1.1 Конфлікт `paymentMethod` між фронтом і бекендом

**Проблема:** Backend `CheckoutRequest` очікує поле `paymentMethod` (COD/ONLINE). Frontend `CheckoutPayload` його не має. Checkout зламається при підключенні бекенду.

**Фронтенд:**
- Додати `paymentMethod: "COD" | "ONLINE"` в `CheckoutPayload` (`src/types/order.ts`)
- В `StepPayment` додати вибір методу оплати (radio: "Накладений платіж" / "Оплата онлайн")
- При `ONLINE` — після submit показати redirect на Monobank (або mock-сторінку поки немає інтеграції)
- Поки Monobank не підключений — hardcode `paymentMethod: "COD"` в `submitCheckout`

**Бекенд:**
- `CheckoutRequest` вже має `paymentMethod` — ОК
- Валідувати що `paymentMethod` не null

#### В1.2 Бекенд ПОВИНЕН перераховувати ціну при checkout

**Проблема:** Ціна зберігається в кошику на клієнті. Якщо бекенд бере ціну з фронтенду або не перераховує — можна підробити запит і купити за 1 грн.

**Бекенд** (`OrderService.checkout()`):
- Для кожного `CheckoutItemRequest`: завантажити `Product` → знайти `Variant` по `sku`
- Обчислити `unitPrice = product.basePrice + variant.priceModifier`
- Обчислити `totalAmount = Σ(unitPrice × quantity)`
- **Ніколи не приймати ціну від клієнта** — `CheckoutItemRequest` містить тільки `productId, sku, quantity`

#### В1.3 Безпечний order tracking (accessToken)

**Проблема:** `GET /api/v1/orders/{id}` — публічний. MongoDB ObjectId передбачуваний (timestamp + machine). Хто вгадає id — бачить ПІБ, email, телефон, адресу.

**Бекенд:**
- Додати поле `accessToken` (UUID4) в модель `Order`
- Генерувати при створенні: `UUID.randomUUID().toString()`
- `GET /api/v1/orders/{id}?token={accessToken}` — повертати 404 якщо token не збігається
- Повертати `accessToken` в `OrderResponse` тільки при створенні (checkout response)

**Фронтенд:**
- `CheckoutSuccess` — зберігати `accessToken` і передавати в URL
- `/order/[id]?token=xxx` — `OrderTracker` передає token в API запит
- `getOrder(id, token)` — додати параметр

#### В1.4 `sitemap.ts` + `robots.ts`

**Фронтенд** — створити:

`src/app/sitemap.ts`:
- Статичні сторінки: `/`, `/checkout`
- Динамічні: fetch `GET /api/v1/products` → `/product/{slug}` для кожного
- Динамічні: fetch `GET /api/v1/artists` → `/artist/{slug}` для кожного
- `changeFrequency: "daily"` для товарів, `"weekly"` для артистів

`src/app/robots.ts`:
- Allow: `/`
- Disallow: `/admin`, `/checkout`, `/order`
- Sitemap: `https://domain.com/sitemap.xml`

#### В1.5 Головна сторінка — Server Component для SEO

**Проблема:** `page.tsx` (головна) має `"use client"` — Google бачить порожню сторінку. Каталог товарів не індексується.

**Фронтенд:**
- Прибрати `"use client"` з `src/app/page.tsx`
- `HeroSection` може залишитись client component (анімації)
- `ProductFeed` — розділити на Server Component обгортку (fetch даних) + Client Component (інтерактивність)
- `useRef` для `scrollSnap` — винести в окремий client component
- Переконатись що initial product data рендериться на сервері

#### В1.6 Юридичні сторінки

**Фронтенд** — створити статичні сторінки:

| Шлях | Вміст |
|------|-------|
| `/terms` | Договір публічної оферти |
| `/return-policy` | Умови обміну та повернення |
| `/privacy` | Політика конфіденційності |
| `/contacts` | Контактна інформація, email, телефон |

- Додати посилання в `Footer`
- Server Components (статичний текст, SEO-friendly)
- Контент — заповнити реальними даними ФОП / юридичної особи

#### В1.7 Статус `WAITING_PAYMENT` на фронтенді

**Проблема:** Frontend `OrderTracker` не знає про статус `WAITING_PAYMENT`. Клієнт з онлайн-оплатою побачить невідомий статус.

**Фронтенд:**
- Додати `WAITING_PAYMENT` в маппінг статусів в `OrderTracker`
- Текст: "Очікуємо оплату" з таймером або підказкою
- Якщо замовлення `WAITING_PAYMENT` — показати кнопку "Оплатити" (redirect на Monobank)

---

### В2. MVP ПОКРАЩЕННЯ — перед публічним запуском

#### В2.1 Фільтри + пошук на фронтенді

**Проблема:** Backend має `/api/v1/products/filters` з динамічними фільтрами. Frontend — жодного UI.

**Фронтенд:**
- Додати компонент `ProductFilters` (sidebar або dropdown)
- Fetch `GET /api/v1/products/filters` → показати чекбокси по артисту, розміру, кольору тощо
- Передавати обрані фільтри як query params до `GET /api/v1/products?artistId=&...`
- Додати сортування (ціна зростання/спадання, новинки)
- Додати пошукове поле в `Header` — підключити до `?search=` параметра API
- Mobile: фільтри у drawer/modal

#### В2.2 Конфлікт endpoint шляхів — звірити фронт і бекенд

| Що | Фронтенд використовує | PLAN.md бекенд |
|----|----------------------|----------------|
| Image presign | `POST /api/v1/admin/products/images/presign` | `POST /api/v1/admin/images/presigned-url` |
| Artist products | `GET /api/v1/artists/{slug}/products` | Немає — є `GET /api/v1/products?artistId=` |

**Рішення:** Обрати один варіант і вирівняти. Рекомендація:
- Image presign: `POST /api/v1/admin/images/presigned-url` (як в PLAN) → оновити фронт
- Artist products: додати в бекенд `GET /api/v1/artists/{slug}/products` (зручніше для фронту, slug замість id)

#### В2.3 Валідація stock при додаванні в кошик

**Проблема:** `addItem` інкрементує quantity без перевірки stock. Клієнт може додати 999 одиниць товару зі stock = 2.

**Фронтенд** (`CommandCenter`):
- При `handleAddToCart()`: перевірити `resolvedVariant.stock` >= поточна кількість в кошику + 1
- Якщо stock вичерпано — показати toast "Максимальна кількість: {stock}"
- В `CartItem` при інкременті — робити ту саму перевірку (потрібно знати stock варіанту)

**Опціонально:** зберігати `maxStock` в `CartItem` для швидкої перевірки.

#### В2.4 Видалення з кошика товарів що закінчились

**Проблема:** Товар доданий вчора може бути розпроданий сьогодні, але кошик показує його.

**Фронтенд:**
- При відкритті `CartDrawer` — для кожного item робити запит на актуальний stock (або batch-запит)
- Якщо `stock = 0` → автоматично видалити з кошика + показати toast "Товар {title} ({variant}) більше не в наявності і видалений з кошика"
- Якщо `stock < quantity` → зменшити quantity до stock + показати toast

**Бекенд:**
- Додати ендпоінт `POST /api/v1/products/stock-check` — приймає масив `[{productId, sku}]`, повертає `[{sku, stock}]`
- Або використовувати існуючий `GET /api/v1/products/{slug}` (менш ефективно)

#### В2.5 Сторінка "Доставка і оплата"

**Фронтенд** — створити `/delivery`:
- Доставка: Нова Пошта, по тарифах перевізника (за рахунок Нової Пошти)
- Оплата: Накладений платіж / Онлайн оплата (Monobank)
- Терміни доставки: 1-3 дні по Україні
- Додати посилання в `Footer` і в `StepShipping`

#### В2.6 Outbox + auto-cancel конфлікт з Telegram кнопками

**Проблема:** При `ONLINE` → Telegram повідомлення адміну з кнопками `[Підтвердити] [Відмінити]`. Якщо через 30 хв auto-cancel → адмін може натиснути "Підтвердити" на скасоване замовлення.

**Бекенд** (`BotCallbackHandler`):
- Перед зміною статусу — перевірити поточний статус замовлення
- Якщо `CANCELLED` → відповісти "Замовлення вже скасовано" + видалити кнопки
- `ExpiredPaymentCleanupJob`: при скасуванні — видалити/оновити Telegram повідомлення (editMessageReplyMarkup → null)

---

### В3. ТЕХНІЧНІ ПОКРАЩЕННЯ — після запуску

#### В3.1 Hydration mismatch кошика (SSR + localStorage)

**Проблема:** Zustand persist з localStorage + Next.js SSR = hydration mismatch. Сервер рендерить `items = []`, клієнт може мати інше.

**Фронтенд:**
- В `Header` (лічильник кошика) і `CartDrawer` — рендерити initial state `0` і оновлювати після hydration
- Використати `useEffect` або Zustand `onRehydrateStorage` callback
- Або: обгорнути cart-залежні елементи в `<ClientOnly>` wrapper

#### В3.2 AdminGuard — перевіряти валідність токена

**Проблема:** Перевіряє наявність токена, не валідність. Якщо expired — адмін бачить панель на секунду, потім викидає.

**Фронтенд:**
- Декодувати JWT на клієнті (без верифікації підпису, тільки exp claim)
- Якщо `exp < now` — спробувати refresh перед показом панелі
- Якщо refresh теж failed — redirect одразу, без мерехтіння UI

#### В3.3 Подвійне кешування: Caffeine + HTTP Cache-Control

**Проблема:** Caffeine 1 год + HTTP `max-age=3600` + React Query staleTime. Адмін оновив товар — клієнт бачить старе до 1 години.

**Рішення:**
- HTTP cache: зменшити до `max-age=300` (5 хв) або `max-age=60, stale-while-revalidate=3600`
- Або: використовувати `ETag` / `Last-Modified` замість жорсткого max-age
- React Query: staleTime = 60_000 (1 хв) для продуктів

#### В3.4 Google Analytics / Facebook Pixel

**Фронтенд:**
- Додати GA4 через `next/script` або `gtag.js`
- E-commerce events: `view_item`, `add_to_cart`, `begin_checkout`, `purchase`
- Facebook Pixel для ретаргетингу (якщо потрібно)

#### В3.5 CDN для S3 зображень

**Проблема:** Зображення йдуть напряму з S3 bucket. Для мобільних 5MB фото = повільно.

**Рішення:**
- CloudFront CDN перед S3 bucket
- Або: використовувати Next.js Image optimization (вже налаштований `remotePatterns`) — переконатись що всі `<img>` замінені на `<Image>`

#### В3.6 Race condition stock + React Query cache

**Проблема:** 2 клієнти бачать `stock: 1`, обидва думають "в наявності". Перший оформить — другий отримає помилку при checkout.

**Рішення (UX):**
- Не показувати точну кількість stock клієнту
- При stock <= 3 показувати "Залишилось мало" без точного числа
- staleTime для product detail = 30 сек (не 5 хв)
- При помилці checkout "Недостатньо товарів" — автоматично рефетчити product і оновити кошик

---

### Порядок реалізації виправлень

| # | Задача | Де | Залежить від |
|---|--------|----|-------------|
| 1 | paymentMethod в CheckoutPayload | frontend | — |
| 2 | Перерахунок ціни при checkout | backend (Б4) | — |
| 3 | Order accessToken | backend (Б4) + frontend | — |
| 4 | sitemap.ts + robots.ts | frontend | — |
| 5 | Головна → Server Component | frontend | — |
| 6 | Юридичні сторінки | frontend | — |
| 7 | WAITING_PAYMENT в OrderTracker | frontend | В1.1 |
| 8 | Фільтри + пошук UI | frontend | Б3 (backend) |
| 9 | Звірити endpoint шляхи | frontend + backend | Б3, Б5 |
| 10 | Валідація stock в кошику | frontend | Б3 |
| 11 | Видалення розпроданих з кошика | frontend + backend | Б3 |
| 12 | Сторінка "Доставка і оплата" | frontend | — |
| 13 | Outbox конфлікт Telegram | backend (Б6) | Б4, Б6 |
| 14 | Hydration mismatch fix | frontend | — |
| 15 | AdminGuard JWT exp check | frontend | Б2 |
| 16 | Кешування стратегія | backend (Б9) | Б9 |
| 17 | Analytics (GA4) | frontend | — |
| 18 | CDN для зображень | infra | — |
| 19 | Stock race condition UX | frontend | Б3 |

---

## Env-змінні (повний список)

```
MONGODB_URI
JWT_SECRET
AWS_ACCESS_KEY
AWS_SECRET_KEY
AWS_S3_BUCKET
AWS_S3_REGION
TELEGRAM_BOT_TOKEN
TELEGRAM_CHAT_IDS
NOVA_POSHTA_API_KEY
CORS_ALLOWED_ORIGINS
SEED_ENABLED
MAIL_HOST
MAIL_PORT
MAIL_USERNAME
MAIL_PASSWORD
MAIL_FROM
MONOBANK_TOKEN
WEBHOOK_BASE_URL          # prod: https://api.yourdomain.com, dev: https://xxx.ngrok.io
```
