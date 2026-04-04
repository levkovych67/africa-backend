# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Africe is an e-commerce merch store for Ukrainian music artists. The backend is a **modular monolith** built with Java 21, Spring Boot 3.4.3, and MongoDB, organized as a multi-module Gradle (Kotlin DSL) project that compiles into a single deployable JAR.

## Build & Run Commands

```bash
# Build the fat JAR
./gradlew :app:bootJar

# Run locally (needs MongoDB on localhost:27017)
./gradlew :app:bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew :app:test --tests "com.africe.backend.ArchitectureTest"

# Run tests for a specific module
./gradlew :order-service:test
```

## Module Structure

```
app/              → Bootstrap, config, OpenAPI, seeders (depends on all modules)
common/           → Shared models, DTOs, enums, audit aspect, exception handlers
auth-service/     → JWT auth (jjwt), admin users, Spring Security config
product-service/  → Product & artist catalog CRUD
order-service/    → Checkout flow, Nova Poshta shipping integration, Caffeine cache
admin-service/    → Admin CRUD endpoints, dashboard stats, S3 presigned URL generation
telegram-service/ → Telegram bot notifications, outbox worker pattern
```

**Dependency direction:** `common` ← all services; `product-service` ← `order-service` ← `admin-service`; `auth-service` ← `admin-service`, `telegram-service`. The `app` module aggregates everything.

## Architecture Rules

ArchUnit tests enforce (`ArchitectureTest.java`):
- Models must not depend on services
- Services must not depend on controllers

Package convention: `com.africe.backend.<module>.<layer>` (e.g., `com.africe.backend.order.service`).

## Key Patterns

- **Outbox pattern**: Order events are written to an `OutboxEvent` MongoDB collection, then polled and sent to Telegram by `OutboxWorker` (telegram-service). This decouples notification delivery from the checkout flow.
- **Audit aspect**: `@AdminAudited` annotation + `AuditAspect` logs admin actions to an `AuditLog` collection.
- **Circuit breakers** (Resilience4j): Applied to Telegram bot and Nova Poshta API calls.
- **DTOs as records**: Request/response types in `common/dto/` are Java records.
- **Lombok**: Used project-wide for models (`@Data`, `@Builder`, etc.).

## External Integrations

- **MongoDB** (Atlas in prod): Single database, configured via `MONGODB_URI`
- **AWS S3**: Product image storage; admin-service generates presigned upload URLs
- **Nova Poshta API**: Ukrainian shipping provider for city/warehouse lookup
- **Telegram Bot API**: Order notifications to admin chat(s)

## Deployment

Deploys to a VPS via GitHub Actions on push to `main`:
1. SSH into VPS, `git pull`, build JAR, restart systemd service
2. Runs behind Nginx reverse proxy (API on `:8080`, frontend on `:3000`)
3. Environment variables loaded from `/opt/africa/africa-backend/.env.prod`
4. Spring profile: `prod` (uses `application-prod.yml`)

## Configuration

Environment variables (see `.env.prod.example`): `MONGODB_URI`, `JWT_SECRET`, `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `AWS_S3_BUCKET`, `AWS_S3_REGION`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_IDS`, `NOVA_POSHTA_API_KEY`, `CORS_ALLOWED_ORIGINS`, `SEED_ENABLED`.

Seeder runs on startup when `SEED_ENABLED=true` — loads products/artists from JSON files in `app/src/main/resources/seed/`.
