package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.DashboardView;
import com.menta.bff.application.usecase.GetDashboardViewUseCase;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Dashboard controller - authenticated endpoint.
 *
 * <p>Requires authentication to access. The greeting uses the {@link Authentication} name: the
 * BFF authentication provider carries the user's email as principal (a {@code String}), not a
 * {@code UserDetails}, so the name is read from the authentication itself (#492).
 * </p>
 *
 * <p>A student also gets the subscription card (#363), read through {@link GetDashboardViewUseCase}
 * with the access token the {@link TokenRefreshFilter} put on the request, and the response is
 * marked {@code Cache-Control: no-store} because it carries per-user state. A student whose request
 * carries no token fails closed like the plans page, but without breaking the page: no Billing call
 * is made and the card shows the unavailable notice. Any other role keeps the dashboard without the
 * card and without any Billing call.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private static final String STUDENT_AUTHORITY = "ROLE_STUDENT";

    private final GetDashboardViewUseCase getDashboardViewUseCase;

    @GetMapping("/dashboard")
    public String dashboard(
            Authentication authentication, HttpServletRequest request, HttpServletResponse response, Model model) {
        if (authentication != null) {
            model.addAttribute("username", authentication.getName());
        }
        if (isStudent(authentication)) {
            String accessToken = (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
            DashboardView subscription = accessToken == null || accessToken.isBlank()
                    ? new DashboardView.Unavailable()
                    : getDashboardViewUseCase.execute(accessToken);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            model.addAttribute("subscription", subscription);
        }
        return "dashboard";
    }

    private static boolean isStudent(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> STUDENT_AUTHORITY.equals(authority.getAuthority()));
    }
}
