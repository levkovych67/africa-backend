# Admin Panel — Implementation Guide

Functional admin dashboard for the Africe merch store. Plain white design, no custom styling — just clean, default UI that works.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Next.js (App Router) |
| Language | TypeScript |
| Styling | Tailwind CSS — default utility classes, white bg, gray borders, standard rounded corners |
| State | Zustand (auth token persistence) |
| Server State | TanStack React Query |

No custom fonts, no animations, no design system. Use Tailwind defaults (`text-gray-900`, `bg-white`, `border-gray-200`, `rounded-lg`, etc.).

---

## Project Structure

```
africa-admin/
├── src/
│   ├── app/
│   │   ├── layout.tsx
│   │   ├── login/page.tsx
│   │   ├── (dashboard)/
│   │   │   ├── layout.tsx          # Sidebar + topbar
│   │   │   ├── page.tsx            # Dashboard stats
│   │   │   ├── orders/
│   │   │   │   ├── page.tsx        # Orders list
│   │   │   │   └── [id]/page.tsx   # Order detail
│   │   │   ├── products/
│   │   │   │   ├── page.tsx        # Products list
│   │   │   │   ├── new/page.tsx    # Create product
│   │   │   │   └── [id]/page.tsx   # Edit product
│   │   │   └── artists/
│   │   │       ├── page.tsx        # Artists list
│   │   │       ├── new/page.tsx    # Create artist
│   │   │       └── [id]/page.tsx   # Edit artist
│   ├── components/
│   │   ├── layout/
│   │   │   ├── sidebar.tsx
│   │   │   └── auth-guard.tsx
│   │   ├── orders/
│   │   │   └── order-detail.tsx
│   │   ├── products/
│   │   │   └── product-form.tsx
│   │   └── artists/
│   │       └── artist-form.tsx
│   ├── lib/
│   │   ├── api/
│   │   │   ├── client.ts           # Auth-aware fetch wrapper
│   │   │   ├── auth.ts
│   │   │   ├── products.ts
│   │   │   ├── artists.ts
│   │   │   ├── orders.ts
│   │   │   ├── dashboard.ts
│   │   │   └── images.ts
│   │   └── utils.ts                # formatPrice, formatDate
│   ├── hooks/
│   │   ├── use-products.ts
│   │   ├── use-artists.ts
│   │   ├── use-orders.ts
│   │   └── use-dashboard.ts
│   ├── store/
│   │   └── auth.ts
│   └── types/
│       └── index.ts                # All types in one file
```

---

## Authentication

### Flow

1. `POST /api/v1/auth/login` with `{ email, password }` → `{ accessToken, refreshToken }`
2. Store in Zustand with `persist` middleware (localStorage)
3. All API calls: `Authorization: Bearer <accessToken>`
4. On 401 → try `POST /api/v1/auth/refresh` → retry. If refresh fails → redirect to `/login`

### Auth Store

```typescript
interface AuthStore {
  accessToken: string | null;
  refreshToken: string | null;
  setTokens: (access: string, refresh: string) => void;
  clearTokens: () => void;
}
```

### API Client

```typescript
async function adminClient<T>(endpoint: string, options?: RequestInit): Promise<T> {
  const { accessToken } = useAuthStore.getState();
  const res = await fetch(`${BASE_URL}${endpoint}`, {
    headers: {
      "Content-Type": "application/json",
      ...(accessToken && { Authorization: `Bearer ${accessToken}` }),
      ...options?.headers,
    },
    ...options,
  });

  if (res.status === 401) {
    const refreshed = await attemptRefresh();
    if (refreshed) return adminClient(endpoint, options);
    useAuthStore.getState().clearTokens();
    window.location.href = "/login";
    throw new Error("Unauthorized");
  }

  if (!res.ok) {
    const error = await res.json().catch(() => ({ message: "Error" }));
    throw new Error(error.message);
  }

  if (res.status === 204) return undefined as T;
  return res.json();
}
```

### AuthGuard

Component that wraps `(dashboard)/layout.tsx`. On mount: if no `accessToken` → redirect to `/login`.

---

## Pages

### Login (`/login`)

Centered form, no sidebar.

- Email input
- Password input
- "Увійти" button
- Error message on 401

---

### Dashboard (`/`)

**API:** `GET /api/v1/admin/dashboard/stats?from=YYYY-MM-DD&to=YYYY-MM-DD`

**Layout:**

1. **Date range** — two `<input type="date">` fields. Default: last 30 days.

2. **3 stat cards** in a row:

| Дохід | Замовлення | Продано одиниць |
|-------|-----------|----------------|
| `totalRevenue` UAH | `totalOrders` | `totalUnitsSold` |

3. **Revenue by day** — simple HTML table or CSS bar chart from `revenueByDay[]`:

| Дата | Дохід | Замовлення |
|------|-------|-----------|
| 2026-03-14 | 4,500 UAH | 5 |
| 2026-03-15 | 3,200 UAH | 3 |

4. **Top products** — table from `topProducts[]`:

| Товар | Продано | Дохід |
|-------|---------|-------|
| Базова чорна футболка | 45 | 45,000 UAH |

---

### Orders (`/orders`)

**API:** `GET /api/v1/admin/orders?search=&status=&page=0&size=20&sort=createdAt,desc`

**Filters:**
- `<input>` for email search
- `<select>` for status: All / PENDING / CONFIRMED / SHIPPED / DELIVERED / CANCELLED

**Table:**

| Клієнт | Email | Телефон | Сума | Статус | Доставка | Дата |
|--------|-------|---------|------|--------|----------|------|
| Іван Петренко | ivan@ex.com | +380... | 2,000 UAH | PENDING | Київ, Відд. №5 | 16.03.2026 |

- Click row → `/orders/[id]`
- Pagination: Previous / Next buttons with page number

**Status badge** — just a `<span>` with conditional bg:
- PENDING: `bg-yellow-100 text-yellow-800`
- CONFIRMED: `bg-blue-100 text-blue-800`
- SHIPPED: `bg-purple-100 text-purple-800`
- DELIVERED: `bg-green-100 text-green-800`
- CANCELLED: `bg-red-100 text-red-800`

---

### Order Detail (`/orders/[id]`)

**Note:** No single-order GET endpoint exists. Pass order data via React Query cache from list, or filter the list by ID.

**Sections:**

1. **Header:** Order ID + created date
2. **Customer info:** firstName, lastName, email, phone
3. **Shipping:** city, warehouseDescription, carrier
4. **Comment:** displayed if present
5. **Items table:**

| Товар | SKU | Варіант | К-сть | Ціна | Сума |
|-------|-----|---------|-------|------|------|
| Базова чорна футболка | PROD-1-M | M | 2 | 1,000 | 2,000 |

Total row at bottom.

6. **Status update:**
- `<select>` with all statuses
- "Оновити статус" button
- `PUT /api/v1/admin/orders/{id}/status` with `{ status }`
- `window.confirm()` before updating

---

### Products (`/products`)

**API:** `GET /api/v1/admin/products?page=0&size=20&sort=createdAt,desc`

**Top bar:** "Додати товар" link → `/products/new`

**Table:**

| Назва | Артист | Ціна | Статус | Варіантів | Дата |
|-------|--------|------|--------|-----------|------|
| Базова чорна футболка | Африка Рекордс | 1,000 UAH | ACTIVE | 4 | 15.03.2026 |

- Click row → `/products/[id]`
- Pagination

---

### Product Form (`/products/new` and `/products/[id]`)

**APIs:**
- `POST /api/v1/admin/products` (create, returns 201)
- `PUT /api/v1/admin/products/{id}` (update)
- `POST /api/v1/admin/products/images/presign` (image upload)

**Fields:**

| Field | Input | Required | API field |
|-------|-------|----------|-----------|
| Назва | `<input type="text">` | Yes | `title` |
| Опис | `<textarea>` | No | `description` |
| Базова ціна (UAH) | `<input type="number">` | Yes | `basePrice` |
| Артист | `<select>` loaded from `GET /api/v1/admin/artists` | No | `artistId` |

**Images section:**
- List of current image URLs with "×" remove button next to each
- "Додати зображення" button → `<input type="file" accept="image/*">`
- On file select:
  1. `POST /api/v1/admin/products/images/presign` → `{ uploadUrl, publicUrl }`
  2. `PUT` file bytes to `uploadUrl` with `Content-Type` header
  3. Append `publicUrl` to images array
- Show upload progress/spinner per image

**Attributes section:**
- List of attribute groups, each with:
  - Type: `<input>` (e.g. "Розмір")
  - Values: comma-separated `<input>` or individual tag inputs (e.g. "M, L, XL")
  - "Видалити" button
- "Додати атрибут" button

**Variants section:**
- Editable table:

| SKU | Атрибути (JSON) | Ціна +/- | Залишок | |
|-----|----------------|----------|---------|---|
| `<input>` | `<input>` key:value | `<input type="number">` | `<input type="number">` | × |

- "Додати варіант" button adds empty row
- Attributes column: simple key-value input (e.g. `Розмір: M`)

**Action buttons:**
- "Зберегти" — POST or PUT
- "Опублікувати" — update with `status: ACTIVE` (only shown for DRAFT products)
- "Архівувати" — `DELETE /api/v1/admin/products/{id}` with `confirm()`
- "Назад" — link back to `/products`

---

### Artists (`/artists`)

**API:** `GET /api/v1/admin/artists?page=0&size=20`

**Top bar:** "Додати артиста" link → `/artists/new`

**Table:**

| Ім'я | Slug | Дата |
|------|------|------|
| Африка Рекордс | afryka-rekords | 15.03.2026 |

- Click row → `/artists/[id]`

---

### Artist Form (`/artists/new` and `/artists/[id]`)

**APIs:**
- `POST /api/v1/admin/artists` (create)
- `PUT /api/v1/admin/artists/{id}` (update)
- `DELETE /api/v1/admin/artists/{id}` (delete)

**Fields:**

| Field | Input | Required | API field |
|-------|-------|----------|-----------|
| Ім'я | `<input type="text">` | Yes | `name` |
| Біографія | `<textarea>` | No | `bio` |
| Фото URL | `<input type="text">` or image upload via presign | No | `image` |

**Social links section:**
- Dynamic list of rows:
  - Platform: `<select>` (instagram, spotify, youtube, tiktok, soundcloud, website)
  - URL: `<input type="url">`
  - "×" remove button
- "Додати" button adds new row
- Serialized as `Record<string, string>` → `socialLinks`

**Action buttons:**
- "Зберегти"
- "Видалити" — `DELETE` with `confirm()`
- "Назад" → `/artists`

---

## Sidebar

Fixed left sidebar, always visible on authenticated pages.

```
┌──────────────┐
│ AFRICA ADMIN │
│              │
│ Дашборд      │
│ Замовлення   │
│ Товари       │
│ Артисти      │
│              │
│              │
│ Вийти        │
└──────────────┘
```

- `w-60`, `bg-white`, `border-r border-gray-200`, full height
- Active link: `bg-gray-100 font-semibold`
- "Вийти": clears auth store, redirects to `/login`

---

## API Endpoints

### Auth

| Method | Endpoint | Request | Response |
|--------|----------|---------|----------|
| POST | `/api/v1/auth/login` | `{ email, password }` | `{ accessToken, refreshToken }` |
| POST | `/api/v1/auth/refresh` | `{ refreshToken }` | `{ accessToken, refreshToken }` |

### Products

| Method | Endpoint | Request | Response |
|--------|----------|---------|----------|
| GET | `/api/v1/admin/products?page&size&sort` | — | `Page<Product>` |
| POST | `/api/v1/admin/products` | `{ title, description?, basePrice, artistId?, attributes?, variants?, images? }` | `Product` (201) |
| PUT | `/api/v1/admin/products/{id}` | partial: any field from above | `Product` |
| DELETE | `/api/v1/admin/products/{id}` | — | 204 (sets status to ARCHIVED) |

### Artists

| Method | Endpoint | Request | Response |
|--------|----------|---------|----------|
| GET | `/api/v1/admin/artists?page&size` | — | `Page<Artist>` |
| POST | `/api/v1/admin/artists` | `{ name, bio?, image?, socialLinks? }` | `Artist` (201) |
| PUT | `/api/v1/admin/artists/{id}` | partial: any field | `Artist` |
| DELETE | `/api/v1/admin/artists/{id}` | — | 204 (permanent delete) |

### Orders

| Method | Endpoint | Request | Response |
|--------|----------|---------|----------|
| GET | `/api/v1/admin/orders?search&status&page&size&sort` | — | `Page<Order>` |
| PUT | `/api/v1/admin/orders/{id}/status` | `{ status }` | `Order` |

Status values: `PENDING`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`

### Dashboard

| Method | Endpoint | Request | Response |
|--------|----------|---------|----------|
| GET | `/api/v1/admin/dashboard/stats?from&to` | — | `DashboardStats` |

Dates: `YYYY-MM-DD` format. Defaults: `from` = 30 days ago, `to` = today.

### Images

| Method | Endpoint | Request | Response |
|--------|----------|---------|----------|
| POST | `/api/v1/admin/products/images/presign` | `{ fileName, contentType }` | `{ uploadUrl, publicUrl }` |

Allowed: `image/jpeg`, `image/png`, `image/webp`. Upload the file with `PUT` to `uploadUrl`.

---

## Types

```typescript
// Auth
interface LoginPayload { email: string; password: string; }
interface AuthResponse { accessToken: string; refreshToken: string; }

// Product
interface Product {
  id: string;
  slug: string;
  title: string;
  description: string;
  basePrice: number;
  attributes: { type: string; values: string[] }[];
  variants: { sku: string; attributes: Record<string, string>; priceModifier: number; stock: number }[];
  images: string[];
  artistId: string | null;
  artistName: string | null;
  artistSlug: string | null;
  status: "DRAFT" | "ACTIVE" | "ARCHIVED";
  createdAt: string;
  updatedAt: string;
}

// Artist
interface Artist {
  id: string;
  slug: string;
  name: string;
  bio: string;
  image: string | null;
  socialLinks: Record<string, string>;
  createdAt: string;
  updatedAt: string;
}

// Order
interface Order {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  items: { productId: string; productTitle: string; sku: string; variantName: string; quantity: number; unitPrice: number }[];
  totalAmount: number;
  status: "PENDING" | "CONFIRMED" | "SHIPPED" | "DELIVERED" | "CANCELLED";
  shippingDetails: {
    city: string;
    cityRef: string;
    warehouseRef: string;
    warehouseDescription: string;
    country: string;
    carrier: string;
    trackingNumber: string | null;
  };
  comment: string | null;
  createdAt: string;
  updatedAt: string | null;
}

// Dashboard
interface DashboardStats {
  totalRevenue: number;
  totalOrders: number;
  totalUnitsSold: number;
  topProducts: { productId: string; title: string; unitsSold: number; revenue: number }[];
  revenueByDay: { date: string; revenue: number; orders: number }[];
}

// Pagination
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

// Image upload
interface PresignResponse { uploadUrl: string; publicUrl: string; }
```

---

## Error Handling

API errors return: `{ status, error, message, timestamp }`

- **401:** Refresh token → retry. If fails → redirect to login.
- **400:** Show `message` as alert or inline error.
- **404:** Show "Не знайдено".
- **500:** Show "Сталася помилка".

Use `window.alert()` or simple inline error messages. No toast library needed.

---

## CORS

Add admin dev URL to backend config:

```yaml
cors:
  allowed-origins: http://localhost:3000,http://localhost:3001
```

---

## Implementation Order

1. Auth store + API client + login page
2. Sidebar layout + auth guard
3. Orders list + order detail + status update
4. Products list + product form (with image upload)
5. Artists list + artist form
6. Dashboard stats
