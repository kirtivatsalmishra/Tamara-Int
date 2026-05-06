# Technical Design Document — Tamara Murabaha Repayment Plan

## 1. Requirement Summary

Build a Spring Boot service that accepts a Murabaha purchase request (customer, commodity cost in SAR, commodity category, installment count 2–12, optional promo code) and produces a complete repayment plan: applied profit-margin percentage, profit amount, total payable, base installment amount, a monthly installment schedule starting one calendar month after the purchase date, and a Sharia-compliant contract summary. Margins are commodity-driven (gold 8%, oil 10%, metals 12%, wheat 15%, default 12%), promo codes (`SAVE10`, `SAVE20`) reduce the margin proportionally but never below 5%. Money math must reconcile to the cent — the **last** installment absorbs the rounding remainder. Dates must be handled consistently across the system, and month-end overflow (Jan 31 → Feb 28/29) must roll to the target month's last day.

---

## 2. Proposed Design

A classic layered Spring Boot service: `controller` -> `service` -> `domain` (pure logic) -> `repository` (JPA on H2 file-based store, already wired in `pom.xml` and `application.yml`). The two volatile axes — **profit margin per commodity** and **promo-code adjustment** — are isolated behind `Strategy` interfaces collected as `List<Strategy>` injections so new commodities or promos are added by dropping in a new `@Component`, never by editing existing classes (OCP). Date and money concerns are quarantined in two small infrastructure classes (`SaudiCalendar`, `MoneyAllocator`) so the rest of the codebase has one place to look for the rules.

```
HTTP ──► RepaymentPlanController
              │
              ▼
         RepaymentPlanService ───► RepaymentPlanRepository ──► H2 (file)
              │                          (JPA)
              ├─► MarginResolver ──► List<CommodityMarginPolicy>  (Strategy, OCP)
              ├─► PromoEngine    ──► List<PromoCodePolicy>        (Strategy, OCP)
              ├─► InstallmentScheduler ──► SaudiCalendar          (date rules)
              │                       └── MoneyAllocator          (rounding)
              └─► ContractSummaryWriter                            (Template Method)
```

### Package layout

```
com.interview.dummy.murabaha
├── api                  // controllers + DTOs + exception handler
│   ├── dto              // request/response records
│   └── error            // problem-detail mapping
├── application          // service orchestration (@Service)
├── domain               // pure JDK types — entities + value objects + invariants
│   ├── model            // RepaymentPlan, Installment, Money, CommodityCategory
│   └── policy           // MarginPolicy, PromoPolicy interfaces (the OCP seams)
├── infrastructure
│   ├── persistence      // JPA entities, repositories, converters
│   ├── money            // MoneyAllocator, CurrencyScaleProvider
│   └── time             // SaudiCalendar, ClockConfig
└── policy.impl          // concrete CommodityMarginPolicy / PromoCodePolicy beans
```

The existing `com.interview.dummy.{model,service,repository,controller}` Item scaffold is left untouched; the Murabaha module lives in its own package tree.

---

## 3. Class & Interface Definitions

### 3.1 API layer

```java
// api/dto/CreatePlanRequest.java
public record CreatePlanRequest(
        @NotBlank String customerId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal commodityCost,
        @NotBlank String commodityCategory,
        @NotNull @Min(2) @Max(12) Integer installments,
        @Nullable String promoCode,
        @Nullable LocalDate purchaseDate           // optional; defaults to "today" in business zone
) { }

// api/dto/RepaymentPlanResponse.java
public record RepaymentPlanResponse(
        UUID planId,
        String customerId,
        BigDecimal commodityCost,
        BigDecimal appliedMarginPercent,
        BigDecimal totalProfit,
        BigDecimal totalPayable,
        BigDecimal baseInstallmentAmount,
        List<InstallmentDto> schedule,
        String contractSummary
) { }

public record InstallmentDto(int sequence, LocalDate dueDate, BigDecimal amount) { }
```

```java
// api/RepaymentPlanController.java
@RestController
@RequestMapping("/api/murabaha/plans")
@RequiredArgsConstructor
public class RepaymentPlanController {
    private final RepaymentPlanService service;

    @PostMapping
    public ResponseEntity<RepaymentPlanResponse> create(@Valid @RequestBody CreatePlanRequest req) { /* ... */ }

    @GetMapping("/{planId}")
    public RepaymentPlanResponse get(@PathVariable UUID planId) { /* ... */ }
}
```

### 3.2 Domain model

```java
// domain/model/Money.java  — value object, immutable
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN);
    }
    public Money add(Money o) { /* assert same currency */ }
    public Money subtract(Money o) { /* ... */ }
}

// domain/model/CommodityCategory.java
public record CommodityCategory(String code) {
    public CommodityCategory { code = code.trim().toLowerCase(Locale.ROOT); }
}

// domain/model/RepaymentPlan.java  (aggregate root)
public final class RepaymentPlan {
    private final UUID id;
    private final String customerId;
    private final Money commodityCost;
    private final BigDecimal appliedMarginPercent;   // e.g. 12.00
    private final Money totalProfit;
    private final Money totalPayable;
    private final List<Installment> schedule;        // unmodifiable
    private final LocalDate purchaseDate;            // business-zone date
    // factory + invariants enforced in constructor: sum(schedule) == totalPayable
}

public record Installment(int sequence, LocalDate dueDate, Money amount) { }
```

### 3.3 OCP seams — policies

```java
// domain/policy/CommodityMarginPolicy.java
public interface CommodityMarginPolicy {
    boolean supports(CommodityCategory category);
    BigDecimal baseMarginPercent();                 // e.g. new BigDecimal("8.00")
}

// domain/policy/PromoCodePolicy.java
public interface PromoCodePolicy {
    boolean supports(String code);
    /** Returns the adjusted margin percent (caller enforces floor). */
    BigDecimal apply(BigDecimal baseMarginPercent);
}

// application/MarginResolver.java
@Component
public class MarginResolver {
    private final List<CommodityMarginPolicy> commodityPolicies;
    private final List<PromoCodePolicy> promoPolicies;
    private final BigDecimal defaultMargin;     // 12.00 from @ConfigurationProperties
    private final BigDecimal floor;             // 5.00

    public BigDecimal resolve(CommodityCategory cat, @Nullable String promoCode) {
        BigDecimal base = commodityPolicies.stream()
                .filter(p -> p.supports(cat)).findFirst()
                .map(CommodityMarginPolicy::baseMarginPercent)
                .orElse(defaultMargin);
        BigDecimal adjusted = (promoCode == null) ? base
                : promoPolicies.stream().filter(p -> p.supports(promoCode)).findFirst()
                    .orElseThrow(() -> new InvalidPromoCodeException(promoCode))
                    .apply(base);
        return adjusted.max(floor);
    }
}
```

Concrete policies (each its own `@Component`):

```java
@Component class GoldMarginPolicy   implements CommodityMarginPolicy { /* 8.00 */ }
@Component class OilMarginPolicy    implements CommodityMarginPolicy { /* 10.00 */ }
@Component class MetalsMarginPolicy implements CommodityMarginPolicy { /* 12.00 */ }
@Component class WheatMarginPolicy  implements CommodityMarginPolicy { /* 15.00 */ }

@Component class Save10PromoPolicy  implements PromoCodePolicy { /* base * 0.90 */ }
@Component class Save20PromoPolicy  implements PromoCodePolicy { /* base * 0.80 */ }
```

### 3.4 Money & dates infrastructure

```java
// infrastructure/money/MoneyAllocator.java
@Component
public class MoneyAllocator {
    /** Splits total into n parts; remainder absorbed on the LAST element. */
    public List<Money> split(Money total, int n) { /* see §6 pseudocode */ }
}

// infrastructure/time/SaudiCalendar.java
@Component
@RequiredArgsConstructor
public class SaudiCalendar {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Riyadh");
    private final Clock clock;                         // ZonedClock at BUSINESS_ZONE
    public LocalDate today() { return LocalDate.now(clock); }
    /** Same day-of-month next month, clamped to that month's last day. */
    public LocalDate addMonthsClamped(LocalDate base, int months) {
        YearMonth target = YearMonth.from(base).plusMonths(months);
        return target.atDay(Math.min(base.getDayOfMonth(), target.lengthOfMonth()));
    }
}

// infrastructure/time/ClockConfig.java
@Configuration
class ClockConfig {
    @Bean Clock clock() { return Clock.system(SaudiCalendar.BUSINESS_ZONE); }
}
```

### 3.5 Scheduler & service

```java
// application/InstallmentScheduler.java
@Component
@RequiredArgsConstructor
public class InstallmentScheduler {
    private final SaudiCalendar calendar;
    private final MoneyAllocator allocator;
    public List<Installment> build(Money totalPayable, int n, LocalDate purchaseDate) { /* ... */ }
}

// application/RepaymentPlanService.java
@Service
@RequiredArgsConstructor
public class RepaymentPlanService {
    private final MarginResolver marginResolver;
    private final InstallmentScheduler scheduler;
    private final ContractSummaryWriter summaryWriter;
    private final RepaymentPlanRepository repository;
    private final SaudiCalendar calendar;

    @Transactional
    public RepaymentPlan create(CreatePlanRequest req) { /* orchestrate */ }
    public RepaymentPlan get(UUID id) { /* ... */ }
}
```

### 3.6 Persistence (JPA on existing H2 file store)

```java
// infrastructure/persistence/RepaymentPlanEntity.java
@Entity @Table(name = "repayment_plan")
class RepaymentPlanEntity {
    @Id UUID id;
    String customerId;
    @Column(precision = 19, scale = 4) BigDecimal commodityCost;
    @Column(precision = 7, scale = 4)  BigDecimal appliedMarginPercent;
    @Column(precision = 19, scale = 4) BigDecimal totalProfit;
    @Column(precision = 19, scale = 4) BigDecimal totalPayable;
    String currencyCode;
    LocalDate purchaseDate;
    Instant createdAt;
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC") List<InstallmentEntity> installments;
}

@Entity @Table(name = "installment")
class InstallmentEntity {
    @Id @GeneratedValue Long id;
    @ManyToOne(fetch = FetchType.LAZY) RepaymentPlanEntity plan;
    int sequence;
    LocalDate dueDate;
    @Column(precision = 19, scale = 4) BigDecimal amount;
    String status;         // SCHEDULED, PAID, ... (future-proof; not in MVP)
}
```

Repository: `interface RepaymentPlanRepository extends JpaRepository<RepaymentPlanEntity, UUID>`, with a `PlanMapper` translating to/from the domain aggregate so the domain stays free of JPA annotations.

### 3.7 Contract summary

```java
// application/ContractSummaryWriter.java  (Template Method seam)
public interface ContractSummaryWriter {
    String write(RepaymentPlan plan);
}
@Component class EnglishContractSummaryWriter implements ContractSummaryWriter { /* ... */ }
```

---

## 4. Impacted Spring Components

- `[NEW]` `RepaymentPlanController` (`@RestController`)
- `[NEW]` `RepaymentPlanService` (`@Service`)
- `[NEW]` `MarginResolver`, `InstallmentScheduler`, `MoneyAllocator`, `SaudiCalendar`, `ContractSummaryWriter` (all `@Component`)
- `[NEW]` `GoldMarginPolicy`, `OilMarginPolicy`, `MetalsMarginPolicy`, `WheatMarginPolicy` (`@Component`)
- `[NEW]` `Save10PromoPolicy`, `Save20PromoPolicy` (`@Component`)
- `[NEW]` `RepaymentPlanRepository` (`@Repository` via Spring Data)
- `[NEW]` `RepaymentPlanEntity`, `InstallmentEntity` (JPA `@Entity`)
- `[NEW]` `PlanMapper` (`@Component`)
- `[NEW]` `MurabahaProperties` (`@ConfigurationProperties("murabaha")`) — holds defaults: `default-margin-percent: 12.00`, `min-margin-percent: 5.00`, `currency-code: SAR`, `business-zone: Asia/Riyadh`
- `[NEW]` `ClockConfig` (`@Configuration`) — single `Clock` bean pinned to Asia/Riyadh
- `[NEW]` `JacksonConfig` (`@Configuration`) — registers `JavaTimeModule`, disables `WRITE_DATES_AS_TIMESTAMPS`, sets ISO-8601 formatters
- `[NEW]` `GlobalExceptionHandler` (`@RestControllerAdvice`) — RFC 7807 problem-detail responses
- `[CONFIG]` `application.yml` — add `murabaha:` block, Jackson date settings; H2 datasource is already correctly configured (file mode at `./data/interviewdb`)
- `[CONFIG]` `pom.xml` — add `jackson-datatype-jsr310` (transitively present via Spring Boot 3.4.1, but state explicitly)
- Existing `Item*` classes: untouched (separate package). They can be deleted in a follow-up; not in scope.

---

## 5. Pattern & Reasoning

| # | Pattern | Where | Why (OCP / SOLID) | Why not the alternative |
|---|---------|-------|-------------------|--------------------------|
| 1 | **Strategy + collection injection** | `CommodityMarginPolicy`, `PromoCodePolicy` | Adding "silver" or `SAVE30` is a new `@Component` — zero edits to `MarginResolver`. Pure OCP. SRP: each policy owns one rule. | An `if/switch` in `MarginResolver` is shorter today but every new commodity or promo rewrites the resolver, with regressions and re-testing. A `Map<String, BigDecimal>` config table works for static margins but breaks the moment a margin needs derived logic (tiered rates, market-price coupling). |
| 2 | **Value Object** | `Money`, `CommodityCategory` | Encapsulates currency + scale invariant; impossible to construct an unscaled `Money`. SRP. | Passing raw `BigDecimal` everywhere lets a 4-decimal value leak into a 2-decimal currency — exactly the rounding bug we're trying to prevent. |
| 3 | **Aggregate Root + Factory invariant** | `RepaymentPlan` constructor asserts `sum(schedule) == totalPayable` | Makes the reconciliation rule a *type-system* guarantee — no caller can persist a broken plan. | Validating in the service is easy to forget; the invariant must travel with the data. |
| 4 | **Template Method (single-method)** | `ContractSummaryWriter` interface + `EnglishContractSummaryWriter` impl | Future Arabic copy or per-region wording is a new bean (DI-selected by `@Profile` or `@ConditionalOnProperty`). | A `String.format` inline in the service is fast to write but hostile to localisation and Sharia-compliance review cycles, which the team will absolutely have. |
| 5 | **Repository** (Spring Data) | `RepaymentPlanRepository` | Standard, zero boilerplate. DIP: service depends on the interface, not Hibernate. | Hand-rolled DAO buys nothing on H2/JPA. |
| 6 | **Configuration Properties** | `MurabahaProperties` | Default margin (12%), floor (5%), currency, time zone are policy, not code. Changing the floor in regulation should be a YAML edit. | Hard-coded constants force a redeploy for what is a parameter change. |

---

## 6. Date Handling — Dedicated Section

This is concern #1 the user called out. The rule, in one sentence: **`LocalDate` is the canonical representation everywhere a "due date" is meant; `Instant` is used only for audit timestamps; everything is interpreted in `Asia/Riyadh` (`SaudiCalendar.BUSINESS_ZONE`).**

### 6.1 Type per layer

| Layer | Type | Rationale |
|-------|------|-----------|
| Domain (`Installment.dueDate`, `RepaymentPlan.purchaseDate`) | `java.time.LocalDate` | A "due date" is a calendar concept, not an instant. A SAR installment due 2026-06-15 is due *that calendar day in Riyadh* regardless of whether the server is in Frankfurt. No time, no zone. |
| Domain audit (`createdAt`) | `java.time.Instant` | An audit fact is a point on the timeline; UTC by definition. |
| Persistence | `LocalDate` ⇄ JPA `DATE`, `Instant` ⇄ `TIMESTAMP WITH TIME ZONE` (H2 stores as UTC) | Hibernate 6 maps both natively. No `java.util.Date`, ever. |
| API request | `LocalDate` (ISO-8601, `yyyy-MM-dd`) for `purchaseDate` | Callers must not have to know our zone to express a calendar date. |
| API response | `LocalDate` for due dates, `Instant` (ISO-8601 `…Z`) for any timestamp | Same reasoning, symmetric. |
| External integrations (future) | Convert at the boundary into one of the above; never let `Date` or `Calendar` leak in | One place to translate is one place to test. |

### 6.2 Time-zone strategy

- **Single business zone:** `Asia/Riyadh` (UTC+03:00, no DST). Defined as a constant on `SaudiCalendar` and surfaced via `MurabahaProperties` so it can be overridden per environment.
- **"Today" is a business-zone concept.** The injected `Clock` bean is `Clock.system(Asia/Riyadh)`. `SaudiCalendar.today()` is the only sanctioned way to obtain the current date — no `LocalDate.now()` calls anywhere else (enforced by code review / ArchUnit if added).
- **Audit timestamps in UTC**, rendered as ISO-8601 with `Z` suffix.
- **DST:** Saudi Arabia does not observe DST, so the zone is fixed-offset. Code is still written generically (using `ZoneId`) so that adding a future region with DST does not require a rewrite.
- **Hijri calendar:** the problem statement uses Gregorian calendar dates ("Jan 31"). Hijri is not in scope. If introduced later, it is a *presentation* concern handled by a `LocaleAwareDateRenderer` in the API layer; the domain remains Gregorian `LocalDate` so installment math is deterministic.

### 6.3 Serialization

- Register `com.fasterxml.jackson.datatype.jsr310.JavaTimeModule`.
- Disable `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` (no epoch numbers).
- `LocalDate` ⇄ `"2026-06-15"`.
- `Instant` ⇄ `"2026-06-15T08:30:00Z"`.
- No custom formatters. ISO-8601 only. This is non-negotiable; custom formats are the #1 source of front-end/back-end skew.

```yaml
spring:
  jackson:
    serialization:
      write-dates-as-timestamps: false
    date-format: ""    # ensure no global override of jsr310
```

### 6.4 Common pitfalls — explicit guidance

- **Never** use `java.util.Date`, `java.sql.Date`, `java.util.Calendar`, `SimpleDateFormat`. They are mutable, zone-leaky, and not thread-safe.
- **Never** use `LocalDateTime` for a wall-clock that needs zone semantics — it pretends to be a timestamp but isn't. Use `ZonedDateTime` or `Instant`.
- **Never** call `LocalDate.now()` without an injected `Clock`. Tests must inject `Clock.fixed(...)` to make scheduling deterministic. This is *the* reason `SaudiCalendar` exists.
- **End-of-month installments:** handled by `SaudiCalendar.addMonthsClamped(base, n)` — uses `YearMonth.lengthOfMonth()` to clamp. Worked examples:

| Purchase date | Installment 1 | Installment 2 | Installment 3 |
|---------------|---------------|---------------|---------------|
| 2026-01-31    | 2026-02-28    | 2026-03-31    | 2026-04-30    |
| 2024-01-31 (leap) | 2024-02-29 | 2024-03-31    | 2024-04-30    |
| 2026-03-15    | 2026-04-15    | 2026-05-15    | 2026-06-15    |

  Note an important subtlety: each installment is computed from the *original* `purchaseDate + i months`, not from the *previous* installment. This avoids the well-known drift bug where Jan 31 → Feb 28 → Mar 28 (wrong). Computed correctly: Jan 31 → Feb 28 → **Mar 31**.

### 6.5 Trade-off table — time zone strategy

| Approach | Pros | Cons | Recommendation |
|----------|------|------|----------------|
| **Store/transport everything in UTC, render in Asia/Riyadh** | Universal convention; multi-region ready | "Due date" is a calendar concept — converting `LocalDate` → `Instant` requires picking a time-of-day, which is meaningless and fragile around midnight | Reject for due dates; use only for audit `Instant` |
| **Pin entire system to Asia/Riyadh and use `LocalDate` for due dates** (chosen) | Matches the business reality (one country, one zone, no DST). No spurious time-of-day. Tests are simple. | Future expansion to another zone needs a `tenant.zoneId` field on the plan; the design supports this because zone is a `Clock` bean, not a magic constant | **Recommended** |
| **Use `OffsetDateTime` everywhere** | Carries zone info on the wire | Overkill for a calendar date; encourages developers to think in instants when they shouldn't | Reject |

---

## 7. Installment Split / Rounding — Dedicated Section

This is concern #2. The rule: **last installment absorbs the remainder; everything is `BigDecimal` with currency-driven scale; rounding mode is `HALF_EVEN` for division but the allocator does not actually round the remainder away — it computes it exactly.**

### 7.1 Why `BigDecimal`, not `double` (or `float`)

`double` is base-2 floating point. `0.1 + 0.2` is not `0.3`. Summing 36 installment amounts can accumulate cents of error, which is unacceptable for money. `BigDecimal` is base-10 with explicit scale — it is the only sane choice. The cost of object allocation is irrelevant at this volume.

### 7.2 Currency-driven scale

```java
@Component
public class CurrencyScaleProvider {
    public int scaleOf(Currency c) { return c.getDefaultFractionDigits(); }   // SAR -> 2, JPY -> 0, KWD -> 3
}
```

Scale is **never** hard-coded as `2`. `Currency.getInstance("SAR").getDefaultFractionDigits()` returns 2; `KWD` returns 3 (Kuwaiti Dinar has three minor units); `JPY` returns 0. The `Money` value object enforces this on construction.

### 7.3 Algorithm — chosen approach

Compute the floor of `total / N` at currency scale; assign that to installments 1..N-1; assign `total - sum(previous)` to installment N. This is the simplest algorithm that (a) reconciles exactly, (b) puts the adjustment on the last installment as the spec demands, and (c) keeps every other installment equal.

```text
function split(total: BigDecimal, n: int, scale: int) -> List<BigDecimal>:
    require n >= 1
    require total.scale() <= scale
    base = total.divide(BigDecimal.valueOf(n), scale, RoundingMode.FLOOR)
    out  = new ArrayList(n)
    for i in 0 .. n-2:
        out.add(base)
    sumOfFirstNMinusOne = base.multiply(BigDecimal.valueOf(n - 1))
    last = total.subtract(sumOfFirstNMinusOne)        // exact, no rounding
    out.add(last)
    assert out.stream().reduce(ZERO, BigDecimal::add).equals(total)
    return out
```

Worked examples (SAR, scale = 2):

| Total | N | Result | Sum check |
|-------|---|--------|-----------|
| 100.00 | 3 | 33.33, 33.33, 33.34 | 100.00 |
| 100.00 | 6 | 16.66, 16.66, 16.66, 16.66, 16.66, 16.70 | 100.00 |
| 99.99  | 4 | 24.99, 24.99, 24.99, 25.02 | 99.99 |
| 1000.01 | 12 | 83.33 × 11, 83.38 | 1000.01 |

Note **`RoundingMode.FLOOR`**, not `HALF_EVEN`, when computing the per-installment base. We *want* the early installments to be the smaller, even amount and the last to absorb everything; `HALF_EVEN` could push the base up by one cent and force the last installment to be *less* than the base — semantically odd ("the last one is smaller"). Using `FLOOR` guarantees `last >= base`, which is what the problem statement implies (33.33, 33.33, 33.**34**).

### 7.4 Trade-off table — split algorithm

| Approach | Pros | Cons | Recommendation |
|----------|------|------|----------------|
| **Floor-base + last-absorbs-remainder** (chosen) | Matches spec verbatim ("last installment absorbs the rounding"). Trivial to implement, audit, and explain to a Sharia-compliance reviewer. Last installment is always >= base. | Concentrates the "ugly" cents on one date — accounting prefers it that way. | **Recommended** |
| **Largest-remainder method** | Distributes the remainder (max 1 minor unit per installment) across the N installments with the largest fractional parts | Spreads the unevenness, but the spec explicitly says "last installment". Also: with equal divisors, all remainders are equal and the rule is ambiguous. | Reject — contradicts spec |
| **Distribute remainder across early installments** | "Pay the cents up front" | Same: contradicts spec. | Reject |
| **Banker's rounding (`HALF_EVEN`) per installment** | Minimises cumulative bias over many plans | Does not reconcile to the cent for any single plan unless paired with a remainder step. The problem we have to solve is per-plan reconciliation, not statistical bias. | Reject as standalone; we still use `HALF_EVEN` for `Money` construction so externally-supplied amounts behave well. |
| **Use `MathContext` with arbitrary precision** | Looks "more precise" | Precision and scale are different concepts; this leaves scale ambiguous and is the source of many subtle money bugs. | Reject |

### 7.5 Interaction with the "last day" date

The "last day" of the schedule is `SaudiCalendar.addMonthsClamped(purchaseDate, N)`. Tying this back to §6: because each due date is computed from `purchaseDate + i months` (not from the prior installment), the final due date is deterministic and stable. Worked example: purchase on 2026-01-31, N = 3 → installments due 2026-02-28, 2026-03-31, 2026-04-30. The last installment also happens to be the one that absorbs the rounding remainder — so "the rounding remainder lives on the last day" (the user's phrasing) is naturally true by construction.

### 7.6 Persistence scale vs. presentation scale

Persisted `BigDecimal` columns are declared `precision = 19, scale = 4` to allow headroom for currencies with three minor units and to avoid silent truncation. Domain `Money` values are always at currency scale (2 for SAR). The mapper between domain and entity asserts this on read/write, so a future bug that writes a 4-scale value will fail loudly rather than silently corrupt totals.

---

## 8. API Surface

### 8.1 `POST /api/murabaha/plans`

Request:
```json
{
  "customerId": "CUST-7421",
  "commodityCost": "5000.00",
  "commodityCategory": "gold",
  "installments": 6,
  "promoCode": "SAVE10",
  "purchaseDate": "2026-05-06"
}
```

Response `201 Created`:
```json
{
  "planId": "9b1f...-uuid",
  "customerId": "CUST-7421",
  "commodityCost": "5000.00",
  "appliedMarginPercent": "7.20",
  "totalProfit": "360.00",
  "totalPayable": "5360.00",
  "baseInstallmentAmount": "893.33",
  "schedule": [
    { "sequence": 1, "dueDate": "2026-06-06", "amount": "893.33" },
    { "sequence": 2, "dueDate": "2026-07-06", "amount": "893.33" },
    { "sequence": 3, "dueDate": "2026-08-06", "amount": "893.33" },
    { "sequence": 4, "dueDate": "2026-09-06", "amount": "893.33" },
    { "sequence": 5, "dueDate": "2026-10-06", "amount": "893.33" },
    { "sequence": 6, "dueDate": "2026-11-06", "amount": "893.35" }
  ],
  "contractSummary": "This Murabaha contract confirms our purchase of the commodity on your behalf for SAR 5000.00. We are selling it to you at a total price of SAR 5360.00, which includes our profit of SAR 360.00. This amount is to be paid in 6 equal monthly installments of SAR 893.33."
}
```

(Note: 8% × 0.90 = 7.20%, which is above the 5% floor; SAVE10 reduces the *margin*, not the cost, exactly as specified.)

### 8.2 `GET /api/murabaha/plans/{planId}`

Returns the same shape; `404` if absent.

### 8.3 Error responses (RFC 7807 `application/problem+json`)

| HTTP | `type` slug | When |
|------|-------------|------|
| 400 | `validation-failed` | Bean Validation errors (negative cost, N out of [2,12], blank fields) |
| 400 | `invalid-promo-code` | Unknown promo |
| 404 | `plan-not-found` | GET on missing UUID |
| 500 | `internal-error` | Anything else; details suppressed |

---

## 9. Persistence Approach

The project already ships with H2 in **file mode** (`jdbc:h2:file:./data/interviewdb;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1`) and Spring Data JPA. We use it as-is:

- **Two tables:** `repayment_plan` (one row per plan) and `installment` (N rows per plan, FK + cascade).
- **`ddl-auto: update`** is fine for the interview-grade scope; for production we'd switch to Flyway/Liquibase.
- **Concurrency:** writes to a single plan are wrapped in `@Transactional`. The aggregate is created once and rarely updated, so optimistic-locking via `@Version` is *not* added in MVP but is called out as a follow-up if "payment" mutations are introduced.
- **Durability:** H2 file mode survives JVM restarts; the `./data` directory is the single artefact to back up.
- **The previous `Item` scaffold is left untouched** — it gives us a sanity check that the existing wiring still works after we add the Murabaha module.

Trade-off: a JSON-file repository would also satisfy the brief, but H2+JPA is already wired and gives transactionality for free. Re-implementing on top of files is gratuitous.

---

## 10. Domain Invariants

The aggregate constructor enforces these — failing any throws `DomainException`, mapped to a 500 (because they indicate a programmer error, not user input):

1. `commodityCost.amount > 0`.
2. `2 <= installments.size() <= 12`.
3. `5.00 <= appliedMarginPercent <= 100.00`.
4. `totalProfit = commodityCost * appliedMarginPercent / 100`, rounded `HALF_EVEN` to currency scale.
5. `totalPayable = commodityCost + totalProfit`.
6. `sum(installments.amount) == totalPayable` (exact `BigDecimal.compareTo == 0`).
7. Each `installment.dueDate == purchaseDate + sequence months` (clamped to month length).
8. Sequences are `1..N`, contiguous, monotonically increasing.

Bean Validation handles the user-input layer (HTTP 400). Domain invariants are the second line of defense.

---

## 11. Validation, Error Handling, and Edge Cases

| Edge case | Where handled | Behavior |
|-----------|---------------|----------|
| Negative / zero / non-numeric `commodityCost` | Bean Validation `@DecimalMin("0.01")` + Jackson | 400, `validation-failed` |
| `installments < 2 or > 12` | `@Min(2) @Max(12)` | 400 |
| Blank `customerId` / `commodityCategory` | `@NotBlank` | 400 |
| Unknown commodity category | `MarginResolver` falls back to default 12% | 200 (per spec) |
| Unknown promo code | `MarginResolver` throws `InvalidPromoCodeException` | 400 |
| Promo would push margin below 5% | `MarginResolver.resolve(...).max(floor)` | floor applied silently |
| Purchase on Jan 31 with N=3 | `SaudiCalendar.addMonthsClamped` | Feb 28/29, Mar 31, Apr 30 |
| Currency with 3 minor units (future) | `CurrencyScaleProvider` | Scale derived dynamically |
| Plan not found | Service throws `PlanNotFoundException` | 404 |
| Concurrent identical requests | Each gets a fresh UUID | Idempotency key not in MVP — flagged as open question |

**Out of scope for MVP** (called out so we don't accidentally extend): early payment, late payment, partial payment, payment cancellation. The `InstallmentEntity.status` column exists but is always `SCHEDULED`. This keeps the OCP seams clear: a future `PaymentService` will mutate installment status without touching plan creation.

---

## 12. Testing Strategy

| Test type | Target | Tool |
|-----------|--------|------|
| Unit | `MoneyAllocator.split` — table-driven cases incl. 100/3, 100/6, 99.99/4, scale=3 currency | JUnit 5 parameterised |
| Unit | `SaudiCalendar.addMonthsClamped` — Jan 31, leap years, mid-month, 12-month rollover | JUnit 5 parameterised |
| Unit | `MarginResolver` — each commodity, default fallback, both promos, floor-clamp, unknown promo | JUnit 5 + Mockito for policy lists |
| Unit | Each `CommodityMarginPolicy` and `PromoCodePolicy` | Trivial but cheap |
| Unit | `RepaymentPlan` constructor — every invariant has a failing test | JUnit 5 |
| Slice | `@WebMvcTest(RepaymentPlanController.class)` — request validation, JSON shape, problem-detail bodies | Spring Boot Test |
| Integration | `@SpringBootTest` with H2, fixed `Clock` bean — full POST → DB → GET round trip | Spring Boot Test |
| Integration | Date-determinism: inject `Clock.fixed(Instant.parse("2026-01-31T08:00:00Z"), Asia/Riyadh)`, assert schedule | Spring Boot Test |
| Property | (Optional) `MoneyAllocator.split(total, n).sum() == total` for random `total ∈ [0.01, 10_000_000.00]`, `n ∈ [1, 12]` | jqwik |
| Architectural (optional) | No code outside `infrastructure.time` calls `LocalDate.now()` / `Instant.now()` | ArchUnit |

The fixed `Clock` bean + `SaudiCalendar` + `MoneyAllocator` together make the entire system **deterministic**, which is the single biggest payoff of the design.

---

## 13. Implementation Phases — Builder Agent Task Breakdown

Each phase is independently shippable and has a clear "done" gate.

**Phase 0 — Wiring**
- Add `murabaha` package skeleton, `MurabahaProperties`, `ClockConfig`, `JacksonConfig`, `GlobalExceptionHandler`.
- Update `application.yml` with `murabaha:` block and Jackson date settings.
- Smoke-test: app still boots, `/h2-console` still works, existing `Item` endpoints still pass.

**Phase 1 — Money & Date primitives** (highest-leverage, lowest-risk)
- Implement `Money`, `CurrencyScaleProvider`, `MoneyAllocator`.
- Implement `SaudiCalendar`.
- Full unit-test coverage on both. *Gate: 100% of allocator/calendar tests green.*

**Phase 2 — Margin policies**
- Define `CommodityMarginPolicy`, `PromoCodePolicy` interfaces.
- Implement Gold/Oil/Metals/Wheat + Save10/Save20.
- Implement `MarginResolver` with floor logic.
- Unit tests for each policy and resolver. *Gate: every cell of the spec's margin table has a passing test.*

**Phase 3 — Domain aggregate**
- Implement `CommodityCategory`, `Installment`, `RepaymentPlan` with all invariants.
- Tests for each invariant. *Gate: aggregate cannot be constructed in an invalid state.*

**Phase 4 — Scheduler & service**
- Implement `InstallmentScheduler` (composes calendar + allocator).
- Implement `RepaymentPlanService.create(...)` and `.get(...)`.
- Unit tests with mocked repository, fixed clock.

**Phase 5 — Persistence**
- Implement `RepaymentPlanEntity`, `InstallmentEntity`, `RepaymentPlanRepository`, `PlanMapper`.
- Round-trip integration test. *Gate: created plan survives JVM restart on file-mode H2.*

**Phase 6 — Contract summary**
- `EnglishContractSummaryWriter`. Unit-tested against the spec's verbatim template.

**Phase 7 — API & errors**
- `RepaymentPlanController`, request/response DTOs, `GlobalExceptionHandler` with RFC 7807.
- `@WebMvcTest` slice covering all error cases in §11.

**Phase 8 — End-to-end**
- Full `@SpringBootTest` with the worked example from §8.1 — assert exact JSON.
- Manual `curl` smoke script committed under `docs/`.

**Phase 9 — Optional hardening**
- ArchUnit tests forbidding `LocalDate.now()` outside `infrastructure.time`.
- Property-based tests on `MoneyAllocator`.
- Optimistic-lock annotation on `RepaymentPlanEntity` (preparing for future payment mutations).

---

## 14. Defense of Design (read aloud to stakeholders)

- **The two volatile axes — commodities and promos — are extension points, not edits.** Adding "silver" or `RAMADAN25` is a single new `@Component`; `MarginResolver` and the rest of the system are untouched. That is OCP literally enforced by the dependency-injection container.
- **Money math is wrong by default in Java; we make it right in one place.** All currency arithmetic flows through `Money` and `MoneyAllocator`. There is one algorithm, one rounding mode, one scale source. A junior developer cannot accidentally introduce `double` or hard-code `2`.
- **Time is a single, injected concept.** Every `LocalDate.now()` call would be a bug; `SaudiCalendar` is the only sanctioned source, backed by a `Clock` bean that tests freeze. The Jan-31 → Feb-28 → Mar-31 case is a unit test, not a hope.
- **The aggregate enforces its own invariants.** The reconciliation rule "sum of installments equals total payable" is checked in the constructor of `RepaymentPlan`, so no code path — service, repository, future REST endpoint, or batch job — can persist a broken plan.
- **Persistence is boring on purpose.** H2 file mode + JPA is already in the project and gives us transactionality and durability with no new infrastructure. We do not invent a JSON-file repository when H2 is sitting there.
- **Sharia-compliance language lives behind an interface.** Tomorrow's Arabic translation, or compliance-team-mandated rewording, is a new `ContractSummaryWriter` bean, not a `git blame` archaeology dig through `String.format` calls.

The cost of these abstractions is small (eight small interfaces, one value object, one calendar, one allocator). Each one pays for itself the first time its rule changes — and in a regulated financial product, *every* rule changes.

---

## 15. Open Questions / Assumptions

1. **Idempotency.** The spec does not mention idempotency keys. Assumed every POST creates a new plan, even on duplicate submissions. If duplicates are a concern, add an `Idempotency-Key` header backed by a unique constraint.
2. **Plan retrieval.** A `GET /{planId}` endpoint is not in the spec but is added because the response includes a "unique repayment plan identifier" — implying it is meant to be looked up later. Confirm.
3. **Customer existence.** The spec gives a `customerId` but does not require validation against a customer registry. Assumed treated as an opaque string.
4. **`purchaseDate` source.** Assumed optional in the request; defaults to `SaudiCalendar.today()` if omitted. Confirm — the spec is silent.
5. **Currency.** Hard-spec'd as SAR per the problem statement. The `Money`/`CurrencyScaleProvider` design is multi-currency-ready, but the API only accepts SAR until told otherwise.
6. **Locale of contract summary.** English only in MVP; Arabic version is a follow-up `ContractSummaryWriter` bean.
7. **Authentication / authorization.** Out of scope unless told otherwise; if added, Spring Security with bearer tokens fits cleanly without touching the domain.
8. **Hijri-calendar due dates.** Treated as out of scope (presentation concern, not domain). Confirm.