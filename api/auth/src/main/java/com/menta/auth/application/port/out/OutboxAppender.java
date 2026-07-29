package com.menta.auth.application.port.out;

/**
 * Cross-module port to append an outbox event in the SAME transaction as the
 * domain mutation (ADR-0027 post-commit durable-local pattern).
 *
 * The infrastructure adapter (:api:auth infrastructure layer) writes the row
 * to common_outbox_events using the active JPA transaction, with the column
 * values supplied here. Mockito-friendly: pure Java interface, no framework.
 */
public interface OutboxAppender {

    /**
     * Append an outbox event. MUST participate in the caller's transaction so
     * the COMMIT is shared with the originating domain mutation.
     *
     * @param eventType  e.g. "auth.AuthUserLoggedIn"; never null.
     * @param aggregateId e.g. jti UUID or familyId hex; never null.
     * @param payload    JSON-serialized body; never null.
     */
    void append(String eventType, String aggregateId, String payload);
}
