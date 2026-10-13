package com.menta.bff.infrastructure.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

/**
 * Unit tests of {@link DashboardController} (#492).
 *
 * <p>Spring Security's {@code AuthenticationPrincipalArgumentResolver} is registered, as the real
 * application does, so the controller receives what the security context carries.
 * </p>
 */
class DashboardControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController())
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
}
