# DECISIONS.md

This document records how each invariant from PLAN.md is enforced, the ambiguities encountered
and how they were resolved, and the concurrency/money/idempotency design rationale.

---

## 1. Invariants and How They're Enforced

| Invariant | Enforcement |
|---|---|
| Inventory never negative | DB `CHECK (inventory >= 0)` on `products`, plus `Product.deductInventory` throws if the deduction would go negative. The checkout flow validates `inventory >= quantity` *before* deducting, under a `SELECT ... FOR UPDATE` lock, so the check-then-act is atomic per product row. |
| Total purchased quantity cannot exceed available inventory | Same lock + pre-deduction check, applied per line item inside the single checkout transaction. |
| A cart can be checked out only once | `Cart.status` transitions `ACTIVE -> CHECKED_OUT` under a row lock; a second checkout attempt on a `CHECKED_OUT` cart is rejected (`CART_ALREADY_CHECKED_OUT`) unless it's a genuine idempotent replay (see §3). |
| Invalid products or quantities cannot exist in a cart | `CartService` validates quantity > 0 (`INVALID_QUANTITY`) and product existence (`PRODUCT_NOT_FOUND`) synchronously on every add/update, before any row is written. DB `CHECK (quantity > 0)` backs this up. |
| One successful checkout creates exactly one order | `orders.cart_id` is `UNIQUE`; the whole checkout (inventory deduction, order/order-item creation, coupon redemption, cart status change, idempotency record) is one `@Transactional` method - it either fully commits or fully rolls back. |
| Retried checkout requests must not create duplicate orders | See §3 (Idempotency Mechanism). |
| Order totals remain immutable after creation | `Order`/`OrderItem` entities expose no setters after construction - only a constructor. |
| Each coupon can be redeemed at most once | `Coupon.status` transitions `AVAILABLE -> REDEEMED` under a `SELECT ... FOR UPDATE` lock acquired during checkout; a second concurrent attempt blocks on that lock, then sees `REDEEMED` and fails (`COUPON_ALREADY_REDEEMED`). |
| Failed checkout must not consume a coupon | Coupon redemption is the second-to-last write in the checkout transaction (after inventory/order validation succeeds); any earlier failure (insufficient inventory, cart state, etc.) throws before the coupon is touched, and the whole transaction rolls back. |
| Coupon generation for a milestone occurs only once | `coupons.milestone_number` is `UNIQUE`. See §4 (Milestone Generation). |
| Two concurrent checkouts cannot redeem the same coupon | Same `SELECT ... FOR UPDATE` lock as above. |
| Reports are read-only | `ReportService` only issues `SELECT`/aggregate queries; verified by `ReportReconciliationIT`, which asserts row counts are unchanged before/after calling the report endpoint. |
| Reports reconcile with orders and coupons | `ReportService` computes gross/discount/net revenue directly from `orders`/`order_items`/`coupons` via aggregate queries, not from a cached/denormalized total. |

---

## 2. Concurrency Design

**Lock acquisition order is fixed across every checkout transaction: Cart -> Products (sorted by
id) -> Coupon.** Every transaction acquires locks in this exact order, so no circular wait
(deadlock) is possible regardless of which products or how many carts are involved. Products are
locked with a single `SELECT ... FOR UPDATE ... ORDER BY id` query (`ProductRepository
#findAllByIdForUpdate`) so the DB - not just the Java list - determines lock acquisition order.

Row locks are acquired via `@Lock(LockModeType.PESSIMISTIC_WRITE)` on plain JPQL queries
(`CartRepository#findByIdForUpdate`, `ProductRepository#findAllByIdForUpdate`,
`CouponRepository#findByCodeForUpdate`), translating to `SELECT ... FOR UPDATE` in Postgres.

Optimistic locking (`@Version`) was deliberately **not** used: PLAN.md explicitly calls for
pessimistic locking, and under the expected access pattern (many concurrent checkouts contending
for a *few* hot rows - the last unit of a popular product, a single coupon code) pessimistic
locking avoids the retry storms optimistic locking would cause.

---

## 3. Idempotency Mechanism

**Table:** `checkout_requests(idempotency_key PK, cart_id, order_id, created_at)`. Unlike
PLAN.md's literal sketch, this table has **no `status` column** (see §5, assumption 1).

**Key insight:** the cart row lock already required for inventory correctness *is* the blocking
mechanism for concurrent retries - no polling loop is needed.

1. Plain (non-locking) read: `SELECT ... FROM checkout_requests WHERE idempotency_key = :key`.
   If found, the row is immutable/terminal (see §5), so the stored order is returned immediately.
2. If not found, the checkout transaction opens and locks the cart (`SELECT ... FOR UPDATE`). A
   concurrent retry with the *same* key targets the *same* cart, so it blocks here until the
   first attempt's transaction commits or rolls back. This is the "block and wait" behavior.
3. After acquiring the cart lock:
   - If `cart.status == CHECKED_OUT`: re-read `checkout_requests` by idempotency key. Found ->
     this is the retry that was blocked in step 2; return the now-committed order. Not found ->
     genuine conflict (a *different* successful checkout already used this cart) ->
     `CART_ALREADY_CHECKED_OUT`.
   - If `cart.status == ACTIVE`: proceed with the full checkout flow.
4. If the *original* attempt failed and rolled back (e.g. insufficient inventory), the cart
   reverts to `ACTIVE` and no `checkout_requests` row is ever committed, so a blocked retry simply
   proceeds fresh once unblocked - failed attempts are always safely retryable from scratch.
5. `UNIQUE(idempotency_key)` remains as a DB-level backstop for the edge case of the same key
   reused across two *different* carts (see §5, assumption 2). Postgres blocks the second
   inserter on the unique index until the first transaction resolves, then either raises a
   conflict (if committed) or lets it through (if rolled back). A conflict surfaces as
   `DataIntegrityViolationException`, mapped by `GlobalExceptionHandler` to `409
   IDEMPOTENCY_KEY_CONFLICT` (rolling back the whole attempt - nothing is left consumed).

---

## 4. Milestone Coupon Generation

`RewardMilestoneService.evaluateAndGenerateIfDue()` computes `milestone = totalOrders /
everyNthOrder` and attempts to insert a coupon for that milestone if one doesn't already exist.

**The actual exactly-once guarantee is `UNIQUE(milestone_number)`, not locking.** When two
concurrent checkouts both complete an order that crosses the same milestone boundary, there is no
existing coupon row to lock (it doesn't exist yet) - so a lock cannot arbitrate the race. Both
transactions attempt the insert; the database allows exactly one to succeed, and the loser's
`saveAndFlush` throws `DataIntegrityViolationException`.

This method runs in its own `REQUIRES_NEW` transaction, scheduled via
`TransactionSynchronizationManager.registerSynchronization(...).afterCommit(...)` from inside
`CheckoutService.checkout()`, so it only ever runs after the triggering order has actually
committed (never for a checkout that ultimately fails). The caller catches
`DataIntegrityViolationException` and treats it as "another transaction already generated this
milestone's coupon" - expected and safe to ignore, not an error. Catching happens in the
**caller**, not inside the `REQUIRES_NEW` method itself: once Postgres aborts a transaction after
a constraint violation, only `ROLLBACK` is valid on that connection, so the failing transaction
must be allowed to roll back via Spring's own AOP exception handling rather than swallowed
in-place (which would leave the physical transaction in an unusable aborted state and fail at
commit).

The admin endpoint (`POST /admin/coupons/generate`) calls the identical method, so it is a safe,
idempotent manual trigger - calling it when nothing is due, or when a concurrent checkout already
generated the coupon, simply reports `generated: false` rather than erroring or duplicating.

---

## 5. Ambiguities and Assumptions

1. **`checkout_requests` has no `status` column**, despite PLAN.md's sketch showing one. A row is
   inserted only as the final step of a successful checkout, in the same transaction as order
   creation. A failed or interrupted attempt rolls back entirely (see §3), so existence of a row
   always means "completed successfully" - an explicit status enum would be redundant state that
   could never actually be observed as anything but the terminal value, given no audit/monitoring
   requirement exists in scope (see Deferred Work).

2. **Idempotency-Key scope is global** (`UNIQUE(idempotency_key)` alone, not scoped per cart).
   This matches common industry idempotency-key semantics (e.g. Stripe) - a key identifies one
   logical request attempt. A consequence: if a client reuses a key across two different carts
   (misuse, not a supported use case), the service does not attempt to detect or compare the
   original request's parameters against the new one; it either returns the original stored order
   (if the reuse is sequential, not racing) or produces a `409 IDEMPOTENCY_KEY_CONFLICT` (if
   genuinely racing two first-time inserts). PLAN.md does not specify request-fingerprint
   comparison, and adding it would be speculative scope beyond what's stated.

3. **Concurrent retry blocking, not fail-fast.** PLAN.md's idempotency flow doesn't specify what
   happens when a retry arrives while the original attempt is still mid-transaction. This
   implementation blocks the retry on the cart's row lock until the original resolves, then
   replays its result (see §3) - chosen over fail-fast-with-409 for a better client experience,
   at the cost of the retry's HTTP request taking as long as the original to respond.

4. **No product-management API.** PLAN.md's API surface (§4) has no endpoint for creating or
   updating products, even though carts/checkout depend on products existing. Since inventing an
   unrequested CRUD surface would violate the "don't implement features not required by the
   current task" rule, products are provisioned directly via `ProductRepository` in tests, and via
   a plain (non-Flyway) SQL script at `src/main/resources/db/seed/seed-demo-products.sql` for
   local/manual exploration - see README.md.

5. **Milestone coupon generation is both automatic and admin-triggerable**, using the identical
   underlying method (confirmed with the requester): automatic generation after every successful
   checkout satisfies the invariant unconditionally; the admin endpoint is a safe, idempotent
   manual trigger (e.g. for backfill) that can never duplicate a milestone's coupon.

6. **Coupon discount cannot make the total negative** ("cannot exceed order total" in PLAN.md §5)
   is interpreted as: `total = max(0, subtotal - discount)`, per the explicit money rule in
   PLAN.md §11, rather than as a separate validation that rejects the checkout. Since coupons are
   defined only as a percentage (not a flat amount), a percentage discount can never exceed 100%
   of the subtotal by construction; the floor-at-zero rule is what actually guards against a
   negative total in principle (e.g. a hypothetical >100% discount coupon).

7. **Cart item mutation is blocked once the cart is checked out.** PLAN.md doesn't say explicitly,
   but "a cart can be checked out only once" combined with "order totals remain immutable" implies
   a checked-out cart's contents must not change afterward; `CartService` rejects add/update/
   remove on a non-`ACTIVE` cart with `CART_ALREADY_CHECKED_OUT`.

8. **Adding an item already in the cart increments its quantity** rather than rejecting the
   request or replacing the quantity (that behavior is reserved for `PUT
   /carts/{cartId}/items/{productId}`, which sets quantity absolutely). This is the common
   "add to cart" semantic and keeps POST/PUT meaningfully distinct.

---

## 6. Money Handling

All monetary values use `BigDecimal`, scale 2, `RoundingMode.HALF_UP`, centralized in
`support.Money` so rounding/scale rules exist in exactly one place:

- `lineTotal = unitPrice * quantity`, normalized.
- `discount = subtotal * couponPercent / 100`, normalized.
- `total = max(0, subtotal - discount)`, normalized.

`double`/`float` are never used for money anywhere in the codebase.

---

## 7. Deferred Work (per PLAN.md's explicit list)

Authentication, distributed locking, event-driven architecture, payment gateway abstraction,
audit logs, metrics & monitoring, optimistic-locking alternative, multi-instance deployment
enhancements. Additionally out of scope, following directly from PLAN.md's API surface: any
product-management (create/update/delete) API.

---

## 8. AI Usage

This implementation (design, code, tests, and this document) was produced with Claude (Anthropic)
acting as the implementing agent, following the process defined in `AGENT.md` and the functional
specification in `PLAN.md`. Architectural ambiguities that materially affected design (idempotency
scope and retry semantics, milestone-generation trigger) were surfaced to and confirmed by the
requester before implementation rather than resolved unilaterally; lower-impact ambiguities were
resolved per AGENT.md's stated priority order (correctness > data integrity > simplicity >
testability > maintainability > performance > developer convenience) and documented above rather
than left undocumented.
