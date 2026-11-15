package com.github.bonsaidata.authorizer.exception;

public class DuplicateIdempotencyConflictException extends RuntimeException {
    public DuplicateIdempotencyConflictException(String key) {
        super("Transaction with Idempotency-Key '" + key + "' is currently being processed by another thread");
    }
}
