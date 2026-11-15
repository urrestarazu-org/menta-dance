package com.menta.bff.infrastructure.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.menta.bff.application.dto.DashboardView;
import com.menta.bff.application.usecase.GetDashboardViewUseCase;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

/**
 * Unit tests of {@link DashboardController} (#492, #363).
 *
 * <p>Spring Security's {@code AuthenticationPrincipalArgumentResolver} is registered, as the real
 * application does, so the controller receives what the security context carries.
 * </p>
 */
class DashboardControllerTest {

    private static final String TOKEN = "access-token-abc";

    private final GetDashboardViewUseCase getDashboardViewUseCase = mock(GetDashboardViewUseCase.class);

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(getDashboardViewUseCase))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setViewResolvers(new InternalResourceViewResolver("/templates/", ".html"))
            .build();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("greets the signed-in user by the email the authentication carries as principal")
    void greetsWithTheAuthenticationPrincipal() throws Exception {
        // BffAuthenticationProvider builds the token with the email (a String) as principal, not a UserDetails.
        var authentication = new UsernamePasswordAuthenticationToken(
                "alumno@menta.dance", null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        mockMvc.perform(get("/dashboard").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("username", "alumno@menta.dance"));
    }

    @Test
    @DisplayName("renders without a username when there is no authentication")
    void rendersWithoutUsernameWhenAnonymous() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeDoesNotExist("username"));
    }

    private static UsernamePasswordAuthenticationToken signedIn(String authority) {
        var authentication = new UsernamePasswordAuthenticationToken(
                "user@menta.dance", null, List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    @Test
    @DisplayName("a student gets the subscription card read with the request's access token, never cached")
    void studentGetsTheSubscriptionCard() throws Exception {
        DashboardView card = new DashboardView.Active("Plan Mensual", "31/10/2026", "Vence en 17 días", false);
        when(getDashboardViewUseCase.execute(TOKEN)).thenReturn(card);

        mockMvc.perform(get("/dashboard").principal(signedIn("ROLE_STUDENT"))
                        .requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(model().attribute("subscription", card))
                .andExpect(model().attribute("username", "user@menta.dance"));
        verify(getDashboardViewUseCase).execute(TOKEN);
    }

    @ParameterizedTest(name = "a {0} keeps the dashboard without a subscription card or a Billing call")
    @ValueSource(strings = {"ROLE_ADMIN", "ROLE_INSTRUCTOR", "ROLE_RECEPTIONIST"})
    void staffGetsNoSubscriptionCard(String authority) throws Exception {
        mockMvc.perform(get("/dashboard").principal(signedIn(authority))
                        .requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeDoesNotExist("subscription"))
                .andExpect(model().attribute("username", "user@menta.dance"));
        verify(getDashboardViewUseCase, never()).execute(any());
    }

    @ParameterizedTest(name = "a student whose token is \"{0}\" fails closed to the unavailable card")
    @ValueSource(strings = {"", "  "})
    void studentWithBlankTokenGetsTheUnavailableCard(String token) throws Exception {
        mockMvc.perform(get("/dashboard").principal(signedIn("ROLE_STUDENT"))
                        .requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(model().attribute("subscription", new DashboardView.Unavailable()));
        verify(getDashboardViewUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("a student with no token at all fails closed to the unavailable card without a Billing call")
    void studentWithoutTokenGetsTheUnavailableCard() throws Exception {
        mockMvc.perform(get("/dashboard").principal(signedIn("ROLE_STUDENT")))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("subscription", new DashboardView.Unavailable()))
                .andExpect(model().attribute("username", "user@menta.dance"));
        verify(getDashboardViewUseCase, never()).execute(any());
    }
}
