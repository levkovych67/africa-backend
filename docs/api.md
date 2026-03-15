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
      "slug": "africa-tour-tshirt",
      "title": "Africa Tour T-Shirt",
      "description": "Limited edition tour merch",
      "basePrice": 29.99,
      "attributes": [
        {
          "type": "Size",
          "values": ["S", "M", "L", "XL"]
        },
        {
          "type": "Color",
          "values": ["Black", "White"]
        }
      ],
      "variants": [
        {
          "sku": "TSHIRT-BLK-S",
          "attributes": {
            "Size": "S",
            "Color": "Black"
          },
          "priceModifier": 0,
          "stock": 50
        },
        {
          "sku": "TSHIRT-BLK-M",
          "attributes": {
            "Size": "M",
            "Color": "Black"
          },
          "priceModifier": 0,
          "stock": 30
        }
      ],
      "images": [
        "https://africe-images.s3.eu-central-1.amazonaws.com/products/uuid/front.jpg"
      ],
      "status": "ACTIVE",
      "createdAt": "2026-03-10T10:00:00Z",
      "updatedAt": "2026-03-12T14:30:00Z"
    }
  ],
  "totalElements": 12,
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

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `slug` | string | Product URL slug |

**Response:** `200 OK` — Single `ProductResponse` object (same shape as list item above)

**Error:** `404` if product not found

---

## Orders

### Checkout (Create Order)

```
POST /api/v1/orders/checkout
```

Creates a guest order. Atomically decrements stock for all items within a MongoDB transaction. If any item is out of stock, the entire order is rejected.

**Request Body:**

```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "phone": "+380991234567",
  "items": [
    {
      "productId": "6650a1b2c3d4e5f6a7b8c9d0",
      "sku": "TSHIRT-BLK-S",
      "quantity": 2
    },
    {
      "productId": "6650a1b2c3d4e5f6a7b8c9d1",
      "sku": "HOODIE-WHT-M",
      "quantity": 1
    }
  ],
  "shippingDetails": {
    "address": "123 Main Street",
    "city": "Kyiv",
    "postalCode": "01001",
    "country": "Ukraine"
  }
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
| `shippingDetails.address` | Required, non-blank |
| `shippingDetails.city` | Required, non-blank |
| `shippingDetails.postalCode` | Required, non-blank |
| `shippingDetails.country` | Required, non-blank |

**Response:** `200 OK`

```json
{
  "id": "6650b2c3d4e5f6a7b8c9d0e1",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "phone": "+380991234567",
  "items": [
    {
      "productId": "6650a1b2c3d4e5f6a7b8c9d0",
      "productTitle": "Africa Tour T-Shirt",
      "sku": "TSHIRT-BLK-S",
      "variantName": "Size: S, Color: Black",
      "quantity": 2,
      "unitPrice": 29.99
    }
  ],
  "totalAmount": 59.98,
  "status": "PENDING",
  "shippingDetails": {
    "address": "123 Main Street",
    "city": "Kyiv",
    "postalCode": "01001",
    "country": "Ukraine",
    "trackingNumber": null,
    "carrier": null
  },
  "createdAt": "2026-03-15T12:00:00Z",
  "updatedAt": null
}
```

**Errors:**
- `400` — Validation failure or insufficient stock
- `404` — Product not found

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

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `page` | int | No | Page number |
| `size` | int | No | Page size |
| `sort` | string | No | Sort field, e.g. `createdAt,desc` |

**Response:** `200 OK` — Paginated `ProductResponse` (same shape as public endpoint)

---

### Create Product

```
POST /api/v1/admin/products
```

Creates a new product with status `DRAFT`. Slug is auto-generated from title.

**Request Body:**

```json
{
  "title": "Africa Tour T-Shirt",
  "description": "Limited edition tour merch with exclusive artwork",
  "basePrice": 29.99,
  "attributes": [
    {
      "type": "Size",
      "values": ["S", "M", "L", "XL"]
    },
    {
      "type": "Color",
      "values": ["Black", "White"]
    }
  ],
  "variants": [
    {
      "sku": "TSHIRT-BLK-S",
      "attributes": {
        "Size": "S",
        "Color": "Black"
      },
      "priceModifier": 0,
      "stock": 50
    },
    {
      "sku": "TSHIRT-WHT-XL",
      "attributes": {
        "Size": "XL",
        "Color": "White"
      },
      "priceModifier": 2.00,
      "stock": 25
    }
  ],
  "images": [
    "https://africe-images.s3.eu-central-1.amazonaws.com/products/uuid/front.jpg"
  ]
}
```

| Field | Rules |
|-------|-------|
| `title` | Required, non-blank |
| `basePrice` | Required |
| `description` | Optional |
| `attributes` | Optional |
| `variants` | Optional |
| `images` | Optional |

**Response:** `200 OK` — `ProductResponse`

---

### Update Product

```
PUT /api/v1/admin/products/{id}
```

Partial update — only non-null fields in the request body are updated.

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | string | Product MongoDB ID |

**Request Body:** (all fields optional)

```json
{
  "title": "Updated Title",
  "basePrice": 34.99,
  "variants": [
    {
      "sku": "TSHIRT-BLK-S",
      "attributes": { "Size": "S", "Color": "Black" },
      "priceModifier": 0,
      "stock": 100
    }
  ]
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

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | string | Product MongoDB ID |

**Response:** `204 No Content`

**Error:** `404` if product not found

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
| `status` | string | No | Filter by order status: `PENDING`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED` |
| `page` | int | No | Page number |
| `size` | int | No | Page size |
| `sort` | string | No | Sort field |

**Response:** `200 OK` — Paginated `OrderResponse`

---

### Update Order Status

```
PUT /api/v1/admin/orders/{id}/status
```

**Path Parameters:**

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

Returns aggregated statistics for the store. Only counts orders with status `CONFIRMED`, `SHIPPED`, or `DELIVERED`.

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `from` | date (`YYYY-MM-DD`) | No | 30 days ago | Start date |
| `to` | date (`YYYY-MM-DD`) | No | today | End date |

**Response:** `200 OK`

```json
{
  "totalRevenue": 12500.00,
  "totalOrders": 150,
  "totalUnitsSold": 320,
  "topProducts": [
    {
      "productId": "6650a1b2c3d4e5f6a7b8c9d0",
      "title": "Africa Tour T-Shirt",
      "unitsSold": 45,
      "revenue": 2250.00
    },
    {
      "productId": "6650a1b2c3d4e5f6a7b8c9d1",
      "title": "Logo Hoodie",
      "unitsSold": 30,
      "revenue": 1800.00
    }
  ],
  "revenueByDay": [
    {
      "date": "2026-03-14",
      "revenue": 450.00,
      "orders": 5
    },
    {
      "date": "2026-03-15",
      "revenue": 320.00,
      "orders": 3
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

Generates a pre-signed S3 URL for direct image upload from the frontend. The frontend uploads the file directly to S3 using the returned `uploadUrl`, then uses the `publicUrl` when creating/updating products.

**Request Body:**

```json
{
  "fileName": "tshirt-front.jpg",
  "contentType": "image/jpeg"
}
```

| Field | Rules | Valid Values |
|-------|-------|-------------|
| `fileName` | Required, non-blank | Any filename |
| `contentType` | Required, non-blank | `image/jpeg`, `image/png`, `image/webp` |

**Response:** `200 OK`

```json
{
  "uploadUrl": "https://africe-images.s3.eu-central-1.amazonaws.com/products/550e8400.../tshirt-front.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&...",
  "publicUrl": "https://africe-images.s3.eu-central-1.amazonaws.com/products/550e8400.../tshirt-front.jpg"
}
```

**Frontend Upload Flow:**

```javascript
// 1. Get pre-signed URL
const { uploadUrl, publicUrl } = await fetch('/api/v1/admin/products/images/presign', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({ fileName: 'photo.jpg', contentType: 'image/jpeg' })
}).then(r => r.json());

// 2. Upload directly to S3
await fetch(uploadUrl, {
  method: 'PUT',
  headers: { 'Content-Type': 'image/jpeg' },
  body: imageFile
});

// 3. Use publicUrl when creating/updating product
await fetch('/api/v1/admin/products', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    title: 'New Product',
    basePrice: 29.99,
    images: [publicUrl]
  })
}).then(r => r.json());
```

**Error:** `400` if content type is not supported

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
| `POST` | `/api/v1/orders/checkout` | No | Create guest order |
| `POST` | `/api/v1/auth/login` | No | Admin login |
| `POST` | `/api/v1/auth/refresh` | No | Refresh access token |
| `GET` | `/api/v1/admin/products` | JWT | List all products |
| `POST` | `/api/v1/admin/products` | JWT | Create product |
| `PUT` | `/api/v1/admin/products/{id}` | JWT | Update product |
| `DELETE` | `/api/v1/admin/products/{id}` | JWT | Archive product |
| `GET` | `/api/v1/admin/orders` | JWT | List orders |
| `PUT` | `/api/v1/admin/orders/{id}/status` | JWT | Update order status |
| `GET` | `/api/v1/admin/dashboard/stats` | JWT | Dashboard statistics |
| `POST` | `/api/v1/admin/products/images/presign` | JWT | Get S3 upload URL |
| `GET` | `/actuator/health` | No | Health check |
