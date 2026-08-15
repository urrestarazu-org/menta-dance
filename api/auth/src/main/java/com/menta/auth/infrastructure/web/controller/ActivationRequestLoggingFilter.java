package com.menta.auth.infrastructure.web.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Emits a minimal access event for activation requests without ever writing an
 * opaque activation token or query string to application logs.
 */
@Component
public class ActivationRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ActivationRequestLoggingFilter.class);
    private static final String ACTIVATION_PREFIX = "/api/v1/auth/activate/";
    private static final String REDACTED_ACTIVATION_URI = ACTIVATION_PREFIX + "[REDACTED]";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        filterChain.doFilter(request, response);
        if (request.getRequestURI().startsWith(ACTIVATION_PREFIX)) {
            LOG.info("Activation request completed: method={}, uri={}, status={}",
                request.getMethod(), REDACTED_ACTIVATION_URI, response.getStatus());
        }
    }
}
