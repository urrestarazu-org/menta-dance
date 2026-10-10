package com.menta.bff.infrastructure.web.controller;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Decides whether a checkout URL returned by the Billing API may be used as a redirect target.
 *
 * <p>The browser is sent to whatever the API names, so the BFF only follows a URL that is a
 * well-formed absolute {@code https} URL with a host. Plain {@code http} is accepted only for a
 * loopback host ({@code localhost}, {@code 127.0.0.1}, {@code [::1]}), which is what the local
 * Mercado Pago simulator uses; any other {@code http} host, a missing host, a different scheme
 * ({@code javascript:}, {@code data:}) or a malformed URL is refused.
 * </p>
 */
final class CheckoutUrlPolicy {

    private CheckoutUrlPolicy() {
    }

    static boolean isAllowed(String url) {
        if (url == null) {
            return false;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return false;
        }
        String host = uri.getHost();
        if (uri.getScheme() == null || host == null || host.isBlank()) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return "https".equals(scheme) || "http".equals(scheme) && isLoopback(host);
    }

    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
    }
}
