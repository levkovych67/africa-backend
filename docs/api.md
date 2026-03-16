# Africe Merch Store — API Documentation

**Base URL:** `http://localhost:8080`
**Swagger UI:** `http://localhost:8080/swagger-ui.html`
**OpenAPI JSON:** `http://localhost:8080/api-docs`

---

## Authentication

Admin endpoints require a JWT Bearer token in the `Authorization` header:

```
Authorization: Bearer <access_token>
```

Access tokens expire after **15 minutes**. Use the refresh endpoint to get a new one.

---

## Error Responses

All errors follow a consistent format:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient stock for SKU: TSHIRT-BLK-S",
  "timestamp": "2026-03-15T12:00:00Z"
}
```

| Status | Meaning |
|--------|---------|
| `400` | Validation failure or bad input |
| `401` | Missing or invalid JWT |
| `403` | Insufficient permissions |
| `404` | Resource not found |
| `409` | Conflict (concurrent modification) |
| `500` | Internal server error |

---

## Pagination

List endpoints support pagination via query parameters:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | int | `0` | Page number (0-indexed) |
| `size` | int | `20` | Items per page (max 100) |
| `sort` | string | — | Sort field and direction, e.g. `createdAt,desc` |

Paginated response envelope:

```json
{
  "content": [...],
  "totalElements": 100,
  "totalPages": 5,
  "number": 0,
  "size": 20,
  "first": true,
  "last": false
}
```

---

# Public Endpoints

No authentication required.

---

## Products

### List Products

```
GET /api/v1/products
```

Returns paginated list of **active** products only.

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `search` | string | No | Filter by title (case-insensitive partial match) |
| `page` | int | No | Page number |
| `size` | int | No | Page size |
| `sort` | string | No | Sort field |

**Response:** `200 OK`

```json
{
  "content": [
    {
      "id": "6650a1b2c3d4e5f6a7b8c9d0",
      "slug": "bazova-chorna-futbolka-z-hranzh-aktsentom-1",
      "title": "Базова чорна футболка з гранж-акцентом",
      "description": "Додай характеру у свій повсякденний образ!",
      "basePrice": 1000,
      "attributes": [
        {
          "type": "Розмір",
          "values": ["M", "L", "XL", "XXL"]
        }
      ],
      "variants": [
        {
          "sku": "PROD-1-M",
          "attributes": { "Розмір": "M" },
          "priceModifier": 0,
          "stock": 50
        }
      ],
      "images": [
        "https://africa-shop-dev.s3.eu-north-1.amazonaws.com/products/1/image.jpg"
      ],
      "artistId": "a1b2c3d4-artist-id",
      "artistName": "Африка Рекордс",
      "artistSlug": "afryka-rekords",
      "status": "ACTIVE",
      "createdAt": "2026-03-15T20:00:00Z",
      "updatedAt": "2026-03-15T20:00:00Z"
    }
  ],
  "totalElements": 20,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

---

### Get Product by Slug

```
GET /api/v1/products/{slug}
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `slug` | string | Product URL slug |

**Response:** `200 OK` — Single `ProductResponse` (same shape as list item above)

**Error:** `404` if product not found

---

## Artists

### List All Artists

```
GET /api/v1/artists
```

Returns all artists (not paginated — small dataset).

**Response:** `200 OK`

```json
[
  {
    "id": "a1b2c3d4-artist-id",
    "slug": "afryka-rekords",
    "name": "Африка Рекордс",
    "bio": "Український музичний лейбл та мерч-бренд.",
    "image": null,
    "socialLinks": {
      "instagram": "https://instagram.com/africarecords"
    },
    "createdAt": "2026-03-15T20:00:00Z",
    "updatedAt": "2026-03-15T20:00:00Z"
  }
]
```

---

### Get Artist by Slug

```
GET /api/v1/artists/{slug}
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `slug` | string | Artist URL slug |

**Response:** `200 OK` — Single `ArtistResponse` (same shape as list item above)

**Error:** `404` if artist not found

---

### Get Artist Products

```
GET /api/v1/artists/{slug}/products
```

Returns paginated list of **active** products belonging to the artist.

| Parameter | Type | Description |
|-----------|------|-------------|
| `slug` | string | Artist URL slug |

**Query Parameters:** `page`, `size`, `sort` (standard pagination)

**Response:** `200 OK` — Paginated `ProductResponse`

---

## Orders

### Checkout (Create Order)

```
POST /api/v1/orders/checkout
```

Creates a guest order. Atomically decrements stock for all items. If any item is out of stock, the entire order is rejected.

**Request Body:**

```json
{
  "firstName": "Іван",
  "lastName": "Петренко",
  "email": "ivan@example.com",
  "phone": "+380991234567",
  "items": [
    {
      "productId": "6650a1b2c3d4e5f6a7b8c9d0",
      "sku": "PROD-1-M",
      "quantity": 2
    }
  ],
  "shippingDetails": {
    "city": "Київ",
    "cityRef": "8d5a980d-391c-11dd-90d9-001a92567626",
    "warehouseRef": "1ec09d88-e1c2-11e3-8c4a-0050568002cf",
    "warehouseDescription": "Відділення №5: вул. Хрещатик, 22"
  },
  "comment": "Зателефонуйте перед відправкою"
}
```

**Validation Rules:**

| Field | Rules |
|-------|-------|
| `firstName` | Required, non-blank |
| `lastName` | Required, non-blank |
| `email` | Required, valid email format |
| `phone` | Required, non-blank |
| `items` | Required, at least 1 item |
| `items[].productId` | Required, non-blank |
| `items[].sku` | Required, non-blank |
| `items[].quantity` | Required, minimum 1 |
| `shippingDetails` | Required |
| `shippingDetails.city` | Required, non-blank |
| `shippingDetails.cityRef` | Required, non-blank (Nova Poshta city Ref) |
| `shippingDetails.warehouseRef` | Required, non-blank (Nova Poshta warehouse Ref) |
| `shippingDetails.warehouseDescription` | Required, non-blank |
| `comment` | Optional |

**Response:** `201 Created`

```json
{
  "id": "6650b2c3d4e5f6a7b8c9d0e1",
  "firstName": "Іван",
  "lastName": "Петренко",
  "email": "ivan@example.com",
  "phone": "+380991234567",
  "items": [
    {
      "productId": "6650a1b2c3d4e5f6a7b8c9d0",
      "productTitle": "Базова чорна футболка з гранж-акцентом",
      "sku": "PROD-1-M",
      "variantName": "M",
      "quantity": 2,
      "unitPrice": 1000
    }
  ],
  "totalAmount": 2000,
  "status": "PENDING",
  "shippingDetails": {
    "city": "Київ",
    "cityRef": "8d5a980d-391c-11dd-90d9-001a92567626",
    "warehouseRef": "1ec09d88-e1c2-11e3-8c4a-0050568002cf",
    "warehouseDescription": "Відділення №5: вул. Хрещатик, 22",
    "country": "Ukraine",
    "carrier": "Nova Poshta",
    "trackingNumber": null
  },
  "comment": "Зателефонуйте перед відправкою",
  "createdAt": "2026-03-16T12:00:00Z",
  "updatedAt": null
}
```

**Errors:**
- `400` — Validation failure or insufficient stock
- `404` — Product not found

---

## Nova Poshta (Shipping)

Proxy endpoints for Nova Poshta API. Used by the frontend for city search and warehouse selection during checkout.

### Search Cities

```
GET /api/v1/orders/nova-poshta/cities
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `q` | string | Yes | — | City name search query (Ukrainian) |
| `limit` | int | No | `10` | Max results |

**Response:** `200 OK`

```json
[
  {
    "ref": "8d5a980d-391c-11dd-90d9-001a92567626",
    "name": "Київ",
    "region": "Київська"
  },
  {
    "ref": "db5c88f5-391c-11dd-90d9-001a92567626",
    "name": "Київець",
    "region": "Вінницька"
  }
]
```

---

### Get Warehouses

```
GET /api/v1/orders/nova-poshta/warehouses
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `cityRef` | string | Yes | — | Nova Poshta city Ref (from city search) |
| `limit` | int | No | `50` | Max results |

**Response:** `200 OK`

```json
[
  {
    "ref": "1ec09d88-e1c2-11e3-8c4a-0050568002cf",
    "description": "Відділення №1: вул. Пирогівський шлях, 135",
    "number": "1",
    "shortAddress": "Київ, вул. Пирогівський шлях, 135"
  },
  {
    "ref": "7f468024-e1c2-11e3-8c4a-0050568002cf",
    "description": "Відділення №2: просп. Берестейський, 51",
    "number": "2",
    "shortAddress": "Київ, просп. Берестейський, 51"
  }
]
```

---

# Auth Endpoints

No JWT required. These endpoints issue tokens.

---

### Login

```
POST /api/v1/auth/login
```

**Request Body:**

```json
{
  "email": "admin@africe.com",
  "password": "your-password"
}
```

| Field | Rules |
|-------|-------|
| `email` | Required, non-blank |
| `password` | Required, non-blank |

**Response:** `200 OK`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Error:** `401` — Invalid credentials

---

### Refresh Token

```
POST /api/v1/auth/refresh
```

Issues a new access token using a valid refresh token. The refresh token itself remains the same until it expires (7 days).

**Request Body:**

```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

| Field | Rules |
|-------|-------|
| `refreshToken` | Required, non-blank |

**Response:** `200 OK`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...(new token)",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Error:** `401` — Refresh token expired or invalid

---

# Admin Endpoints

All admin endpoints require `Authorization: Bearer <access_token>` header.

---

## Admin — Products

### List All Products

```
GET /api/v1/admin/products
```

Returns **all** products including DRAFT and ARCHIVED.

**Query Parameters:** `page`, `size`, `sort` (standard pagination)

**Response:** `200 OK` — Paginated `ProductResponse` (same shape as public endpoint)

---

### Create Product

```
POST /api/v1/admin/products
```

Creates a new product with status `DRAFT`. Slug is auto-generated from title (Ukrainian → Latin transliteration).

**Request Body:**

```json
{
  "title": "Нова футболка",
  "description": "Опис товару",
  "basePrice": 1200,
  "artistId": "a1b2c3d4-artist-id",
  "attributes": [
    {
      "type": "Розмір",
      "values": ["M", "L", "XL"]
    }
  ],
  "variants": [
    {
      "sku": "NEW-TSHIRT-M",
      "attributes": { "Розмір": "M" },
      "priceModifier": 0,
      "stock": 50
    }
  ],
  "images": [
    "https://africa-shop-dev.s3.eu-north-1.amazonaws.com/products/uuid/front.jpg"
  ]
}
```

| Field | Rules |
|-------|-------|
| `title` | Required, non-blank |
| `basePrice` | Required |
| `description` | Optional |
| `artistId` | Optional |
| `attributes` | Optional |
| `variants` | Optional |
| `images` | Optional |

**Response:** `201 Created` — `ProductResponse`

---

### Update Product

```
PUT /api/v1/admin/products/{id}
```

Partial update — only non-null fields in the request body are updated.

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | string | Product MongoDB ID |

**Request Body:** (all fields optional)

```json
{
  "title": "Оновлена назва",
  "basePrice": 1500,
  "artistId": "new-artist-id",
  "variants": [...]
}
```

**Response:** `200 OK` — `ProductResponse`

**Error:** `404` if product not found

---

### Archive Product

```
DELETE /api/v1/admin/products/{id}
```

Soft-deletes a product by setting its status to `ARCHIVED`.

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | string | Product MongoDB ID |

**Response:** `204 No Content`

**Error:** `404` if product not found

---

## Admin — Artists

### List All Artists

```
GET /api/v1/admin/artists
```

**Query Parameters:** `page`, `size`, `sort` (standard pagination)

**Response:** `200 OK` — Paginated `ArtistResponse`

---

### Create Artist

```
POST /api/v1/admin/artists
```

Slug is auto-generated from name (Ukrainian → Latin transliteration).

**Request Body:**

```json
{
  "name": "Новий Артист",
  "bio": "Біографія артиста",
  "image": "https://example.com/photo.jpg",
  "socialLinks": {
    "instagram": "https://instagram.com/artist",
    "spotify": "https://open.spotify.com/artist/..."
  }
}
```

| Field | Rules |
|-------|-------|
| `name` | Required, non-blank |
| `bio` | Optional |
| `image` | Optional |
| `socialLinks` | Optional |

**Response:** `201 Created` — `ArtistResponse`

---

### Update Artist

```
PUT /api/v1/admin/artists/{id}
```

Partial update — only non-null fields are updated. If `name` is updated, slug is regenerated.

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | string | Artist MongoDB ID |

**Request Body:** (all fields optional)

```json
{
  "name": "Оновлена Назва",
  "bio": "Новий опис",
  "socialLinks": { "instagram": "https://instagram.com/new" }
}
```

**Response:** `200 OK` — `ArtistResponse`

**Error:** `404` if artist not found

---

### Delete Artist

```
DELETE /api/v1/admin/artists/{id}
```

Permanently deletes the artist.

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | string | Artist MongoDB ID |

**Response:** `204 No Content`

**Error:** `404` if artist not found

---

## Admin — Orders

### List Orders

```
GET /api/v1/admin/orders
```

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `search` | string | No | Filter by customer email (case-insensitive) |
| `status` | string | No | Filter by status: `PENDING`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED` |
| `page` | int | No | Page number |
| `size` | int | No | Page size |
| `sort` | string | No | Sort field |

**Response:** `200 OK` — Paginated `OrderResponse`

---

### Update Order Status

```
PUT /api/v1/admin/orders/{id}/status
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | string | Order MongoDB ID |

**Request Body:**

```json
{
  "status": "CONFIRMED"
}
```

| Field | Rules | Valid Values |
|-------|-------|-------------|
| `status` | Required | `PENDING`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED` |

**Response:** `200 OK` — `OrderResponse`

**Error:** `404` if order not found

---

## Admin — Dashboard

### Get Dashboard Stats

```
GET /api/v1/admin/dashboard/stats
```

Returns aggregated statistics. Only counts orders with status `CONFIRMED`, `SHIPPED`, or `DELIVERED`.

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `from` | date (`YYYY-MM-DD`) | No | 30 days ago | Start date |
| `to` | date (`YYYY-MM-DD`) | No | today | End date |

**Response:** `200 OK`

```json
{
  "totalRevenue": 125000.00,
  "totalOrders": 150,
  "totalUnitsSold": 320,
  "topProducts": [
    {
      "productId": "6650a1b2c3d4e5f6a7b8c9d0",
      "title": "Базова чорна футболка з гранж-акцентом",
      "unitsSold": 45,
      "revenue": 45000.00
    }
  ],
  "revenueByDay": [
    {
      "date": "2026-03-14",
      "revenue": 4500.00,
      "orders": 5
    }
  ]
}
```

---

## Admin — Images

### Generate Pre-signed Upload URL

```
POST /api/v1/admin/products/images/presign
```

Generates a pre-signed S3 URL for direct image upload from the frontend.

**Request Body:**

```json
{
  "fileName": "tshirt-front.jpg",
  "contentType": "image/jpeg"
}
```

| Field | Rules |
|-------|-------|
| `fileName` | Required, non-blank |
| `contentType` | Required, non-blank (`image/jpeg`, `image/png`, `image/webp`) |

**Response:** `200 OK`

```json
{
  "uploadUrl": "https://africa-shop-dev.s3.eu-north-1.amazonaws.com/...?X-Amz-Algorithm=...",
  "publicUrl": "https://africa-shop-dev.s3.eu-north-1.amazonaws.com/products/uuid/tshirt-front.jpg"
}
```

**Frontend Upload Flow:**

```javascript
// 1. Get pre-signed URL
const { uploadUrl, publicUrl } = await api.post('/api/v1/admin/products/images/presign', {
  fileName: 'photo.jpg',
  contentType: 'image/jpeg'
});

// 2. Upload directly to S3
await fetch(uploadUrl, {
  method: 'PUT',
  headers: { 'Content-Type': 'image/jpeg' },
  body: imageFile
});

// 3. Use publicUrl in product images array
```

---

# Monitoring

### Health Check

```
GET /actuator/health
```

No authentication required.

**Response:** `200 OK`

```json
{
  "status": "UP"
}
```

---

# Enums Reference

### ProductStatus

| Value | Description |
|-------|-------------|
| `DRAFT` | Newly created, not visible to customers |
| `ACTIVE` | Published and visible in the store |
| `ARCHIVED` | Soft-deleted, not visible to customers |

### OrderStatus

| Value | Description |
|-------|-------------|
| `PENDING` | Order created, awaiting admin confirmation |
| `CONFIRMED` | Admin confirmed the order |
| `SHIPPED` | Order shipped, tracking info may be available |
| `DELIVERED` | Order delivered to customer |
| `CANCELLED` | Order cancelled |

---

# Quick Reference

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/api/v1/products` | No | List active products |
| `GET` | `/api/v1/products/{slug}` | No | Get product by slug |
| `GET` | `/api/v1/artists` | No | List all artists |
| `GET` | `/api/v1/artists/{slug}` | No | Get artist by slug |
| `GET` | `/api/v1/artists/{slug}/products` | No | Get artist's products |
| `POST` | `/api/v1/orders/checkout` | No | Create guest order |
| `GET` | `/api/v1/orders/nova-poshta/cities` | No | Search cities (Nova Poshta) |
| `GET` | `/api/v1/orders/nova-poshta/warehouses` | No | Get warehouses for city |
| `POST` | `/api/v1/auth/login` | No | Admin login |
| `POST` | `/api/v1/auth/refresh` | No | Refresh access token |
| `GET` | `/api/v1/admin/products` | JWT | List all products |
| `POST` | `/api/v1/admin/products` | JWT | Create product |
| `PUT` | `/api/v1/admin/products/{id}` | JWT | Update product |
| `DELETE` | `/api/v1/admin/products/{id}` | JWT | Archive product |
| `GET` | `/api/v1/admin/artists` | JWT | List all artists (paginated) |
| `POST` | `/api/v1/admin/artists` | JWT | Create artist |
| `PUT` | `/api/v1/admin/artists/{id}` | JWT | Update artist |
| `DELETE` | `/api/v1/admin/artists/{id}` | JWT | Delete artist |
| `GET` | `/api/v1/admin/orders` | JWT | List orders |
| `PUT` | `/api/v1/admin/orders/{id}/status` | JWT | Update order status |
| `GET` | `/api/v1/admin/dashboard/stats` | JWT | Dashboard statistics |
| `POST` | `/api/v1/admin/products/images/presign` | JWT | Get S3 upload URL |
| `GET` | `/actuator/health` | No | Health check |
