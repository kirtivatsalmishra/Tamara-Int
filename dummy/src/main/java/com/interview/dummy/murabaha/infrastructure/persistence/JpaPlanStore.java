package com.interview.dummy.murabaha.infrastructure.persistence;

import com.interview.dummy.murabaha.application.PlanRequestFingerprint;
import com.interview.dummy.murabaha.application.PlanStore;
import com.interview.dummy.murabaha.domain.model.RepaymentPlan;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed implementation of {@link PlanStore}. Wraps the Spring Data
 * repository so the application layer remains free of persistence types.
 */
@Component
public class JpaPlanStore implements PlanStore {

    private final RepaymentPlanRepository repository;
    private final PlanMapper mapper;

    public JpaPlanStore(RepaymentPlanRepository repository, PlanMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public RepaymentPlan save(RepaymentPlan plan, PlanRequestFingerprint fingerprint, String idempotencyKey) {
        RepaymentPlanEntity entity = mapper.toEntity(plan, fingerprint, idempotencyKey);
        RepaymentPlanEntity saved = repository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<RepaymentPlan> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<StoredPlan> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey)
                .map(entity -> new StoredPlan(mapper.toDomain(entity), mapper.toFingerprint(entity)));
    }
}
