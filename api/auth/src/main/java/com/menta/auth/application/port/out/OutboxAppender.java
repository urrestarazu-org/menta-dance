package com.menta.auth.application.port.out;

/**
 * Application port for the Transactional Outbox Pattern (ADR-0030).
 *
 * <h2>What is the Transactional Outbox Pattern?</h2>
 * <p>
 * When a business operation needs to both mutate local state AND notify other
 * systems, we face the "dual-write problem": if we commit first and publish fails,
 * the event is lost; if we publish first and commit fails, consumers see a phantom
 * event. The Transactional Outbox solves this by writing the event to a database
 * table <em>in the same transaction</em> as the domain mutation.
 * </p>
 *
 * <h2>How it works</h2>
 * <pre>{@code
 * @Transactional
 * public void login(User user) {
 *     refreshTokenRepository.save(token);        // domain mutation
 *     outboxAppender.append("auth.UserLoggedIn", // outbox event
 *                           jti, payload);
 *     // COMMIT: both writes succeed or both fail
 * }
 * }</pre>
 *
 * <p>
 * A background reconciler polls {@code common_outbox_events} for PENDING rows,
 * processes them, and marks them PROCESSED. This guarantees <b>at-least-once
 * delivery</b>: events may be processed more than once (if the reconciler crashes
 * between processing and marking), but they are never lost.
 * </p>
 *
 * <h2>Clean Architecture placement</h2>
 * <p>
 * This interface lives in the <b>application layer</b> (port/out) because it
 * defines WHAT the application needs (append an event) without specifying HOW
 * (JPA, JDBC, etc.). The infrastructure layer provides {@code OutboxJpaAppender}
 * as the concrete implementation. This makes use cases unit-testable with a mock.
 * </p>
 *
 * @see <a href="https://microservices.io/patterns/data/transactional-outbox.html">
 *      Transactional Outbox Pattern</a>
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
