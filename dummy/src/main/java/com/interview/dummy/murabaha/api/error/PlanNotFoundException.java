package com.interview.dummy.murabaha.api.error;

import java.util.UUID;

/** Thrown when a GET targets a plan id that has no row in the store. */
public class PlanNotFoundException extends RuntimeException {

    private final UUID planId;

    public PlanNotFoundException(UUID planId) {
        super("Plan not found: " + planId);
        this.planId = planId;
    }

    public UUID getPlanId() {
        return planId;
    }
}
