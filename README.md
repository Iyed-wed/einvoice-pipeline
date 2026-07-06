# einvoice-pipeline

Generation and validation engine for European electronic invoices (Factur-X / EN 16931), with full observability, packaged as a ready-to-run Docker stack.

[![CI](https://github.com/Iyed-wed/einvoice-pipeline/actions/workflows/ci.yml/badge.svg)](https://github.com/Iyed-wed/einvoice-pipeline/actions/workflows/ci.yml)

---

## What it does

`einvoice-pipeline` accepts an invoice (JSON, CSV, or from SAP), **validates** it against the **EN 16931** business rules, **generates** a **Factur-X** file (PDF/A-3 with embedded CII XML) using the [Mustang Project](https://www.mustangproject.org/) library, **persists** an audit trail in PostgreSQL, and **exposes metrics** scraped by Prometheus and visualized in Grafana.

The differentiator is not the XML generation (already a commodity) but the **engineering quality**: standards-compliant validation, structured errors, end-to-end observability, and integration tests against a real database.

## Architecture at a glance

<p align="center">
  <img src="architecture.svg" alt="einvoice-pipeline — architecture" width="920"/>
</p>

The **three entry points** converge on the **same engine** (`InvoiceProcessingService`): web form (no-ERP case), CSV import (non-SAP ERP case), and an SAP gateway (documented OData / BAPI-RFC approach).

## Quick start

Requirements: Docker + Docker Compose.

```bash
docker compose up -d --build
```

This starts four services with healthchecks: the application, PostgreSQL, Prometheus and Grafana. The app waits for a healthy database before starting.

| Service | URL | Credentials |
|---|---|---|
| API + web form | http://localhost:8080 | — |
| Swagger UI (API docs) | http://localhost:8080/swagger-ui.html | — |
| Prometheus endpoint | http://localhost:8080/actuator/prometheus | — |
| Grafana (dashboard) | http://localhost:3000 | `admin` / `admin` |
| Prometheus | http://localhost:9090 | — |

The **einvoice-pipeline** Grafana dashboard is auto-provisioned (generated/rejected invoices, throughput, p50/p95 latency, rejections by EN 16931 rule).

## curl example

```bash
# Generate a Factur-X invoice from JSON and save it as a PDF
curl -X POST http://localhost:8080/api/invoices \
  -H "Content-Type: application/json" \
  -d @sample-invoice.json -o invoice.pdf

# CSV import (generic ERP export)
curl -X POST http://localhost:8080/api/invoices/import-csv \
  -H "Content-Type: text/csv" \
  --data-binary @sample-invoice.csv -o invoice.pdf
```

An inconsistent invoice returns an **RFC 7807** error (`application/problem+json`) citing the violated rule:

```json
{
  "type": "urn:einvoice-pipeline:problem:en16931-violation",
  "title": "Invoice violates EN 16931 business rules",
  "status": 422,
  "errors": [
    { "rule": "BR-CO-15", "field": "totalWithVat",
      "message": "Declared total with VAT (999.00) does not equal total without VAT + VAT (300.00)" }
  ]
}
```

## Tests

```bash
./mvnw verify
```

Integration tests use **Testcontainers** (a real PostgreSQL container), so a Docker daemon must be available. GitHub Actions CI re-runs build + tests on every push.

---

## Tech stack

| Domain | Choice |
|---|---|
| Language / Framework | Java 21 (LTS), Spring Boot 3.5 |
| Factur-X / EN 16931 | Mustang Project |
| Database | PostgreSQL 16, Flyway migrations |
| Observability | Micrometer → Prometheus → Grafana |
| API docs | springdoc-openapi (Swagger UI) |
| Tests | JUnit 5 + Testcontainers |
| Containers | Docker multi-stage, Docker Compose |
| CI | GitHub Actions |

## API endpoints

| Method | Path | Consumes | Produces | Description |
|---|---|---|---|---|
| POST | `/api/invoices` | `application/json` | `application/pdf` | Generate Factur-X from JSON |
| POST | `/api/invoices/import-csv` | `text/csv` | `application/pdf` | Generate Factur-X from a CSV export |
| GET | `/actuator/health` | — | `application/json` | Liveness/readiness |
| GET | `/actuator/prometheus` | — | `text/plain` | Metrics scrape endpoint |

**HTTP status semantics:** `200` Factur-X returned · `400` malformed request (missing/invalid field, bad CSV) · `422` well-formed invoice violating EN 16931 business rules · all errors as `application/problem+json`.

## Project structure

```
src/main/java/com/einvoice/pipeline/
├── controller/     REST endpoints + RFC 7807 error handling
├── service/        processing orchestration, Factur-X generation, CSV parsing
├── validation/     EN 16931 business rules (BR-CO-13/14/15)
├── model/          immutable domain model (records) + Bean Validation
├── repository/     JPA audit-trail entity + Spring Data repository
├── integration/    SAP gateway (documented OData/RFC approach)
├── observability/  Micrometer metrics
└── config/         OpenAPI configuration
monitoring/         Prometheus config + Grafana provisioning & dashboard
```

## Context

A senior-level demonstration project targeting European e-invoicing compliance work (France PDP / Factur-X, Tunisia TTN / El Fatoora).
