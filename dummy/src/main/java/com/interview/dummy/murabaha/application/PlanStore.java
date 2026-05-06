package com.interview.dummy.murabaha.application;

import com.interview.dummy.murabaha.domain.model.RepaymentPlan;

import java.util.Optional;
import java.util.UUID;

/**
 * Application-level port for persisting and retrieving {@link RepaymentPlan}
 * aggregates. The service depends on this interface, not on Spring Data, so
 * the domain remains JPA-free.
 *
 * <p>The {@link PlanRequestFingerprint} captures the raw request inputs and is
 * persisted alongside the plan. On a replayed {@code Idempotency-Key} the
 * service compares the stored fingerprint to the new request to decide between
 * a true replay and a 409 conflict.
 */
public interface PlanStore {

    /**
     * @param plan            the constructed aggregate
     * @param fingerprint     captured input fingerprint (never null)
     * @param idempotencyKey  may be null when the caller did not request idempotency
     */
    RepaymentPlan save(RepaymentPlan plan, PlanRequestFingerprint fingerprint, String idempotencyKey);

    Optional<RepaymentPlan> findById(UUID id);

    Optional<StoredPlan> findByIdempotencyKey(String idempotencyKey);

    /** Plan + its persisted fingerprint, used for idempotency-replay comparison. */
    record StoredPlan(RepaymentPlan plan, PlanRequestFingerprint fingerprint) { }
}
