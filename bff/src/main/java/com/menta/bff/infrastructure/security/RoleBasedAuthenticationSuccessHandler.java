package com.menta.bff.infrastructure.security;

import com.menta.bff.application.management.PostLoginLanding;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

/**
 * Sends the user to the right home after a successful login.
 *
 * <p>A saved request is honored first (the anonymous "Suscribirme" entry point, #361). Without one,
 * the landing depends on the role: staff go to the management home and everyone else to the
 * dashboard. The decision lives in {@link PostLoginLanding}; this handler only applies it.
 * </p>
 */
public class RoleBasedAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {
        return PostLoginLanding.pathFor(authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList());
    }
}
