# Checkout and Reward Service

A Spring Boot service for cart checkout, inventory management, coupon redemption, and milestone
rewards, built to preserve business invariants (no overselling, exactly-once checkout under
retries, at-most-once coupon redemption, exactly-once milestone coupon generation) under
concurrency. See `PLAN.md` for the full functional spec and `DECISIONS.md` for how each invariant
is enforced and how ambiguities were resolved.

## Stack

Java 21, Spring Boot 3, PostgreSQL, Flyway, Maven, JUnit 5 + Mockito + Testcontainers.

## Setup

### Prerequisites

- Java 21 (`brew install openjdk@21` on macOS)
- Maven (`brew install maven`)
- Docker, for local Postgres via `docker-compose` and for the Testcontainers-backed integration
  tests. On macOS without Docker Desktop, [Colima](https://github.com/abiosoft/colima) works:
  `brew install colima docker && colima start`. If using Colima, point Testcontainers at its
  socket and disable its resource-reaper (which doesn't work through Colima's VM boundary):
  ```bash
  export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
  export TESTCONTAINERS_RYUK_DISABLED=true
  ```

### Start Postgres and run the app

```bash
docker-compose up -d
mvn spring-boot:run
```

The app starts on `http://localhost:8080`. Flyway applies all migrations automatically on
startup.

### Seed demo data

PLAN.md's API surface has no product-management endpoint (see `DECISIONS.md`, ambiguity 4), so
there's no way to create a product through the API. For manual exploration, seed a couple of demo
products directly:

```bash
psql postgresql://checkout:checkout@localhost:5432/checkout_reward \
  -f src/main/resources/db/seed/seed-demo-products.sql
```

This inserts three products with fixed IDs (`11111111-...`, `22222222-...`, `33333333-...`) used
in the example requests below.

## Running Tests

```bash
mvn test      # unit tests only (Mockito, no external dependencies)
mvn verify    # unit + integration tests (spins up Postgres via Testcontainers, requires Docker)
              # also runs Checkstyle
```

## API

All error responses share the shape `{ "code": "...", "message": "..." }`.

### Cart

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/carts` | - | Creates an empty `ACTIVE` cart. `201`. |
| GET | `/carts/{cartId}` | - | `200`, or `404 CART_NOT_FOUND`. |
| POST | `/carts/{cartId}/items` | `{ "productId": "...", "quantity": 2 }` | Adds to (or increments) a line item. `201`, `400 INVALID_QUANTITY`, `404 PRODUCT_NOT_FOUND`, `409 CART_ALREADY_CHECKED_OUT`. |
| PUT | `/carts/{cartId}/items/{productId}` | `{ "quantity": 3 }` | Sets a line item's quantity absolutely. |
| DELETE | `/carts/{cartId}/items/{productId}` | - | Removes a line item. `200`. |

### Checkout

| Method | Path | Headers | Body |
|---|---|---|---|
| POST | `/carts/{cartId}/checkout` | `Idempotency-Key: <client-generated>` (required) | `{ "couponCode": "OPTIONAL" }` |

Responses: `201 Created` (order, possibly a replay of a prior identical request - see
`DECISIONS.md` §3), `400 IDEMPOTENCY_KEY_REQUIRED`, `400 VALIDATION_ERROR`,
`404 CART_NOT_FOUND` / `COUPON_NOT_FOUND`, `409 CART_ALREADY_CHECKED_OUT` /
`INSUFFICIENT_INVENTORY` / `COUPON_ALREADY_REDEEMED` / `IDEMPOTENCY_KEY_CONFLICT`.

### Orders

| Method | Path |
|---|---|
| GET | `/orders/{orderId}` |

### Admin

| Method | Path | Notes |
|---|---|---|
| POST | `/admin/coupons/generate` | Idempotent: generates the next due milestone coupon, or reports `{ "generated": false }` if none is due or another request already generated it. |
| GET | `/admin/reports` | Read-only aggregate report. |

## Example Requests

Assumes demo products are seeded (see above) and the app is running on `localhost:8080`.

```bash
# Create a cart
CART_ID=$(curl -s -X POST localhost:8080/carts | jq -r .id)

# Add two mice
curl -s -X POST localhost:8080/carts/$CART_ID/items \
  -H 'Content-Type: application/json' \
  -d '{"productId":"11111111-1111-1111-1111-111111111111","quantity":2}'

# Checkout (Idempotency-Key required)
curl -s -X POST localhost:8080/carts/$CART_ID/checkout \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{}'

# Retrieve the order
curl -s localhost:8080/orders/<order-id-from-above>

# Generate the next milestone coupon, if due
curl -s -X POST localhost:8080/admin/coupons/generate

# View the aggregate report
curl -s localhost:8080/admin/reports
```

Checkout with a coupon:

```bash
curl -s -X POST localhost:8080/carts/$CART_ID/checkout \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"couponCode":"MILESTONE-1-ABCD1234"}'
```

## Configuration

`src/main/resources/application.yml`:

```yaml
reward:
  every-nth-order: 5     # milestone spacing
  discount-percent: 10   # discount applied by milestone-generated coupons
```
