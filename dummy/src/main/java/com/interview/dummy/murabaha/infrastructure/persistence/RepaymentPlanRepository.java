package com.interview.dummy.murabaha.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RepaymentPlanRepository extends JpaRepository<RepaymentPlanEntity, UUID> {

    Optional<RepaymentPlanEntity> findByIdempotencyKey(String idempotencyKey);
}
