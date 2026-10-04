package com.menta.billing.infrastructure.outbox;

import java.time.Instant;

/**
 * Identifier generator returning a fresh 26-char ULID per outbox row.
 * Production default is the auth-side implementation (same impl class
 * on both sides of the table — see design §1 cross-module JPA strategy).
 */
public interface BillingUlidGenerator {

    /** Returns a fresh 26-character ULID to be used as the id of an outbox row. */
    String next();
}
