package com.menta.bff.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

/** Unit tests of {@link RoleBasedAuthenticationSuccessHandler}: where each role lands after login (#354). */
@DisplayName("RoleBasedAuthenticationSuccessHandler")
class RoleBasedAuthenticationSuccessHandlerTest {

    private final RoleBasedAuthenticationSuccessHandler handler = new RoleBasedAuthenticationSuccessHandler();

    private static Authentication authenticated(String... authorities) {
        return new UsernamePasswordAuthenticationToken("user@example.com", null,
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private String landingOf(MockHttpServletRequest request, Authentication authentication) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response, authentication);
        return response.getRedirectedUrl();
    }

    @Test
    @DisplayName("staff roles land on the management home")
    void staffLandOnManagement() throws Exception {
        for (String role : List.of("ROLE_ADMIN", "ROLE_INSTRUCTOR", "ROLE_RECEPTIONIST")) {
            assertThat(landingOf(new MockHttpServletRequest(), authenticated(role)))
                    .as(role).isEqualTo("/gestion");
        }
    }

    @Test
    @DisplayName("a student lands on the dashboard")
    void studentLandsOnDashboard() throws Exception {
        assertThat(landingOf(new MockHttpServletRequest(), authenticated("ROLE_STUDENT")))
                .isEqualTo("/dashboard");
    }

    @Test
    @DisplayName("an unknown role lands on the dashboard")
    void unknownRoleLandsOnDashboard() throws Exception {
        assertThat(landingOf(new MockHttpServletRequest(), authenticated("ROLE_SUPERUSER")))
                .isEqualTo("/dashboard");
    }

    @Test
    @DisplayName("a saved request wins over the role landing, for staff and students alike")
    void savedRequestIsRespected() throws Exception {
        for (String role : List.of("ROLE_STUDENT", "ROLE_ADMIN")) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("GET");
            request.setServletPath("/plans/suscribirme");
            request.setRequestURI("/plans/suscribirme");
            MockHttpServletResponse saveResponse = new MockHttpServletResponse();
            new HttpSessionRequestCache().saveRequest(request, saveResponse);

            assertThat(landingOf(request, authenticated(role))).as(role)
                    .startsWith("http://localhost/plans/suscribirme");
        }
    }
}
