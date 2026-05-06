package com.interview.dummy.murabaha.api.error;

/**
 * Thrown when an {@code Idempotency-Key} is replayed with a request body that
 * differs from the original. Surfaced as 409 so the caller can correct the body
 * or rotate the key.
 */
public class IdempotencyKeyConflictException extends RuntimeException {

    private final String idempotencyKey;

    public IdempotencyKeyConflictException(String idempotencyKey) {
        super("Idempotency key already used with a different request body: " + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
