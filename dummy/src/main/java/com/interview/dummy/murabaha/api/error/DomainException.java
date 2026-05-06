package com.interview.dummy.murabaha.api.error;

/**
 * Thrown when a domain invariant is violated. These represent programmer errors
 * (e.g. an aggregate constructed in an inconsistent state) and surface as 500.
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}
