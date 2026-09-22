# PLAN.md

## Goal

Build a reliable Checkout and Rewards Service that maintains correctness under:

- Concurrent checkouts
- Retry requests
- Inventory contention
- Coupon contention
- Price changes
- Report generation

The primary focus is preserving business invariants rather than CRUD completeness.

---

# 1. Core Invariants

The following invariants must always hold:

### Inventory
- Inventory can never become negative.
- Total purchased quantity cannot exceed available inventory.

### Cart
- A cart can be checked out only once.
- Invalid products or quantities cannot exist in a cart.

### Orders
- One successful checkout creates exactly one order.
- Retried checkout requests must not create duplicate orders.
- Order totals remain immutable after creation.

### Coupons
- Each coupon can be redeemed at most once.
- Failed checkout must not consume a coupon.
- Coupon generation for a milestone occurs only once.
- Two concurrent checkouts cannot redeem the same coupon.

### Reporting
- Reports are read-only.
- Reports reconcile with orders and coupons.

---

# 2. Technical Stack

## Backend
- Java 21
- Spring Boot 3

## Database
- PostgreSQL

Reason:
- Strong transaction support
- Row-level locking
- Easy implementation of concurrency guarantees

## Build Tool
- Maven

## Testing
- JUnit 5
- Mockito
- Testcontainers (optional)

---

# 3. Domain Model

## Product

```java
Product
{
    id
    name
    price
    inventory
}
```

---

## Cart

```java
Cart
{
    id
    status
}
```

Status:

- ACTIVE
- CHECKED_OUT

---

## CartItem

```java
CartItem
{
    cartId
    productId
    quantity
}
```

---

## Order

```java
Order
{
    id
    cartId
    subtotal
    discount
    total
    createdAt
}
```

---

## OrderItem

Snapshot of purchased data.

```java
OrderItem
{
    orderId
    productId
    productName
    unitPrice
    quantity
}
```

Reason:
Order history remains correct even if product data changes later.

---

## Coupon

```java
Coupon
{
    code
    discountPercent
    status
    milestoneNumber
}
```

Status:

- AVAILABLE
- REDEEMED

---

## CheckoutRequest

```java
CheckoutRequest
{
    couponCode
}
```

---

# 4. API Design

## Cart APIs

### Create Cart

POST /carts

Response:
201 Created

---

### Get Cart

GET /carts/{cartId}

Response:
200 OK

---

### Add Item

POST /carts/{cartId}/items

---

### Update Item

PUT /carts/{cartId}/items/{productId}

---

### Remove Item

DELETE /carts/{cartId}/items/{productId}

---

# Checkout

POST /carts/{cartId}/checkout

Headers:

```text
Idempotency-Key
```

Body:

```json
{
  "couponCode": "ABC123"
}
```

Responses:

- 201 Created
- 409 Already Checked Out
- 409 Coupon Already Redeemed
- 409 Insufficient Inventory
- 400 Validation Error

---

# Orders

GET /orders/{orderId}

---

# Admin Coupon Generation

POST /admin/coupons/generate

---

# Reporting

GET /admin/reports

---

# 5. Checkout Flow

## Step 1

Load cart.

Validate:

- Exists
- Active
- Has items

---

## Step 2

Acquire row locks.

```sql
SELECT ...
FOR UPDATE
```

Lock:

- Cart
- Products
- Coupon (if supplied)

---

## Step 3

Validate inventory.

```text
inventory >= requested quantity
```

Fail immediately if not.

---

## Step 4

Calculate totals.

Use:

```java
BigDecimal
```

Never use:

```java
double
float
```

---

## Step 5

Validate coupon.

Rules:

- Must exist
- Must be AVAILABLE
- Cannot exceed order total

---

## Step 6

Deduct inventory.

---

## Step 7

Create order snapshot.

Persist:

- product name
- quantity
- unit price

---

## Step 8

Mark coupon redeemed.

Only after order creation succeeds.

---

## Step 9

Mark cart checked out.

---

## Step 10

Commit transaction.

---

# 6. Idempotency Strategy

Problem:

Client retries checkout because response was lost.

Requirement:

Retry must not create another order.

## Solution

Introduce table:

```java
CheckoutRequest
{
    idempotencyKey
    cartId
    orderId
    status
}
```

Flow:

1. Receive key.
2. Check existing record.
3. If already processed:
   - return existing order.
4. Otherwise process normally.
5. Store result.

Result:

Multiple retries return same order.

---

# 7. Inventory Concurrency Strategy

Problem:

Two users buy the last item simultaneously.

Solution:

Lock product rows during checkout.

```sql
SELECT product
FOR UPDATE
```

Only one transaction can modify inventory.

Result:

No overselling.

---

# 8. Coupon Concurrency Strategy

Problem:

Two users redeem same coupon simultaneously.

Solution:

Lock coupon row.

```sql
SELECT coupon
FOR UPDATE
```

Transaction that commits first marks coupon redeemed.

Second transaction fails validation.

Result:

Single successful redemption.

---

# 9. Price Change Semantics

Decision:

Cart shows current product prices.

At checkout:

- Recalculate using latest product price.

Reason:

Avoid selling at stale prices.

Order stores final purchased price snapshot.

Document in DECISIONS.md.

---

# 10. Coupon Generation Semantics

Configuration:

```yaml
reward:
  everyNthOrder: 5
  discountPercent: 10
```

Example:

Order count:

5 → coupon generated

10 → coupon generated

15 → coupon generated

---

Generation logic:

```text
milestone = totalOrders / n
```

Generate only if milestone not rewarded.

Unique constraint:

(milestoneNumber)

Prevents duplicates.

---

# 11. Money Rules

Use:

```java
BigDecimal
```

Scale:

```java
2
```

Rounding:

```java
HALF_UP
```

Rules:

```text
subtotal = sum(itemPrice * quantity)

discount = subtotal * couponPercent

total = subtotal - discount
```

Minimum total:

```text
0
```

Never negative.

---

# 12. Error Model

Standard structure:

```json
{
  "code": "INSUFFICIENT_INVENTORY",
  "message": "Inventory unavailable"
}
```

Examples:

- CART_NOT_FOUND
- PRODUCT_NOT_FOUND
- INVALID_QUANTITY
- INSUFFICIENT_INVENTORY
- CART_ALREADY_CHECKED_OUT
- COUPON_NOT_FOUND
- COUPON_ALREADY_REDEEMED
- IDEMPOTENCY_KEY_REQUIRED

---

# 13. Reporting Strategy

Aggregate from:

- Orders
- Order Items
- Coupons

Return:

```json
{
  "totalOrders": 100,
  "grossRevenue": 5000,
  "discounts": 400,
  "netRevenue": 4600,
  "products": [],
  "couponSummary": {}
}
```

Report never mutates state.

---

# 14. Tests

## Happy Path

- Create cart
- Add item
- Checkout
- Retrieve order

---

## Inventory Failure

- Inventory = 1
- Request quantity = 2

Expect:

409

---

## Concurrent Inventory Checkout

Two threads:

- Both buy last item

Expect:

- one success
- one failure

---

## Idempotent Checkout

Same key sent twice.

Expect:

- one order only

---

## Coupon Double Redemption

Two concurrent requests.

Expect:

- one redemption
- one failure

---

## Coupon Not Consumed On Failure

Inventory validation fails.

Expect:

Coupon remains AVAILABLE.

---

## Milestone Coupon Generation

Order counts:

5, 10, 15

Expect:

Exactly one coupon per milestone.

---

# 15. Deliverables

## README.md

- Setup
- Run instructions
- API documentation
- Example requests

## DECISIONS.md

- Invariants
- Ambiguities
- Concurrency decisions
- Idempotency strategy
- Money handling
- Deferred work
- AI usage

## Source Code

Spring Boot application

## Tests

Business-focused tests including concurrency scenarios.

---

# Deferred (If Time Remains)

- Authentication
- Distributed locking
- Event-driven architecture
- Payment gateway abstraction
- Audit logs
- Metrics & monitoring
- Optimistic locking alternative
- Multi-instance deployment enhancements