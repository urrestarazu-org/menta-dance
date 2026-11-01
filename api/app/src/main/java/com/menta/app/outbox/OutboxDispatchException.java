package com.menta.app.outbox;

/**
 * A row that cannot be dispatched no matter how healthy Redis is: no handler claims its event
 * type, several do, or its payload is unreadable (#672).
 *
 * <p>The distinction matters to the reconciler heartbeat, which means "Redis responds". A failure
 * of this kind says nothing about Redis, so it must never keep the heartbeat from being written;
 * any other exception raised by a handler may be a real side-effect failure and stays fail-closed
 * (ADR-0026). It extends {@link IllegalStateException} because that is what these conditions
 * raised before the type existed.</p>
 */
public class OutboxDispatchException extends IllegalStateException {

    public OutboxDispatchException(String message) {
        super(message);
    }

    public OutboxDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
