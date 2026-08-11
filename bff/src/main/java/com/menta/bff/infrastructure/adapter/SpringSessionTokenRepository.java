package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.port.out.SessionTokenRepository;
import com.menta.bff.domain.model.SessionTokens;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Spring Session adapter for storing authentication tokens.
 * <p>
 * Implements {@link SessionTokenRepository} using HttpSession attributes.
 * Tokens are stored in Redis via Spring Session Data Redis.
 * </p>
 * <p>
 * Part of Clean Architecture infrastructure layer.
 * </p>
 * <p>
 * Thread safety: HttpSession is thread-safe in Servlet containers.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringSessionTokenRepository implements SessionTokenRepository {

    /**
     * Session attribute key for storing tokens.
     * Uses uppercase with underscore for clarity (follows Redis key conventions).
     */
    private static final String SESSION_ATTRIBUTE_KEY = "AUTH_TOKENS";

    /**
     * Supplier for current HTTP session.
     * Using Supplier allows lazy evaluation in request scope.
     */
    private final Supplier<HttpSession> httpSessionSupplier;

    @Override
    public void store(SessionTokens tokens) {
        Objects.requireNonNull(tokens, "tokens cannot be null");

        HttpSession session = getSession();
        session.setAttribute(SESSION_ATTRIBUTE_KEY, tokens);

        log.debug("Stored tokens in session: {}", session.getId());
    }

    @Override
    public Optional<SessionTokens> load() {
        HttpSession session = getSession();
        Object attribute = session.getAttribute(SESSION_ATTRIBUTE_KEY);

        if (attribute instanceof SessionTokens tokens) {
            log.debug("Loaded tokens from session: {}", session.getId());
            return Optional.of(tokens);
        }

        log.debug("No tokens found in session: {}", session.getId());
        return Optional.empty();
    }

    @Override
    public void clear() {
        HttpSession session = getSession();
        session.removeAttribute(SESSION_ATTRIBUTE_KEY);

        log.debug("Cleared tokens from session: {}", session.getId());
    }

    /**
     * Gets current HTTP session or throws if unavailable.
     *
     * @return Current HTTP session
     * @throws IllegalStateException if no HTTP session is available
     */
    private HttpSession getSession() {
        HttpSession session = httpSessionSupplier.get();

        if (session == null) {
            throw new IllegalStateException(
                    "No HTTP session available - this operation must be called within an HTTP request context"
            );
        }

        return session;
    }
}
