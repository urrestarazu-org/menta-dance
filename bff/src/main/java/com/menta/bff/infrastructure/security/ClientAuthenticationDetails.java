package com.menta.bff.infrastructure.security;

import java.io.Serializable;

/**
 * Request-derived detail attached to the {@code Authentication} during form
 * login (ADR-0035).
 *
 * <p>Spring Security's {@code AuthenticationDetailsSource} is the supported
 * seam for carrying request-scoped facts into an {@code AuthenticationProvider}.
 * Using it keeps the provider free of {@code RequestContextHolder} lookups,
 * which are thread-bound, invisible in the constructor, and awkward to test.</p>
 *
 * <p>Serializable because the authenticated token is stored in the
 * Redis-backed session.</p>
 *
 * @param clientAddress canonical originating address resolved by
 *     {@link ClientOriginResolver}; never a raw proxy chain.
 */
public record ClientAuthenticationDetails(String clientAddress) implements Serializable {

    private static final long serialVersionUID = 1L;
}
