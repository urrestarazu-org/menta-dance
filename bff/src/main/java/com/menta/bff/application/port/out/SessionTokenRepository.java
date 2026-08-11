package com.menta.bff.application.port.out;

import com.menta.bff.domain.model.SessionTokens;

import java.util.Optional;

/**
 * Port for storing and retrieving session tokens.
 * <p>
 * Abstracts the session storage mechanism (Spring Session + Redis in production).
 * Part of Clean Architecture application layer - defines what the use cases need,
 * not how it's implemented.
 * </p>
 * <p>
 * Thread safety: implementations MUST be thread-safe, as HTTP sessions can be
 * accessed concurrently by refresh filters and user requests.
 * </p>
 */
public interface SessionTokenRepository {

    /**
     * Stores tokens in the current HTTP session.
     * <p>
     * MUST be called within an active HTTP request context.
     * Overwrites any existing tokens in the session.
     * </p>
     *
     * @param tokens Tokens to store (access token, refresh token, expiration)
     * @throws IllegalStateException if no HTTP session is available
     */
    void store(SessionTokens tokens);

    /**
     * Loads tokens from the current HTTP session.
     * <p>
     * MUST be called within an active HTTP request context.
     * </p>
     *
     * @return Tokens if present in session, empty if not found or session doesn't exist
     * @throws IllegalStateException if no HTTP session is available
     */
    Optional<SessionTokens> load();

    /**
     * Clears tokens from the current HTTP session.
     * <p>
     * Idempotent - calling multiple times is safe.
     * MUST be called within an active HTTP request context.
     * </p>
     *
     * @throws IllegalStateException if no HTTP session is available
     */
    void clear();
}
