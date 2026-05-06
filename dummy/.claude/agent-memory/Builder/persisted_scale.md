---
name: BigDecimal persisted scale vs domain scale
description: JPA rehydrates BigDecimal at the JDBC column scale; mapper must normalise or JSON differs between insert-time and read-time responses
type: feedback
---

When a JPA `@Column(precision=7, scale=4)` holds `12.00`, on read Hibernate returns `12.0000`. If the in-memory aggregate constructed before save still carries scale 2, two API responses for the *same persisted plan* will serialise differently (`12.00` first, `12.0000` second). This breaks idempotent-replay byte-equality assertions.

**Why:** discovered while wiring the Idempotency-Key replay path on the Murabaha module — the duplicate-POST integration test asserted byte-equal response bodies and surfaced this.

**How to apply:** In any `Mapper.toDomain(...)`, normalise BigDecimal fields whose column scale exceeds the domain/display scale. For percentages: `value.stripTrailingZeros()` then clamp scale to a sensible minimum (2 here). For Money amounts: re-scale to `CurrencyScaleProvider.scaleOf(currency)` so KWD vs SAR works correctly. Don't rely on Hibernate to remember the original scale.
