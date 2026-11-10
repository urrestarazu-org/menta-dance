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

/** Unit tests of {@link ManagementHomeController} (#354). */
class ManagementHomeControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ManagementHomeController())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setViewResolvers(new InternalResourceViewResolver("/templates/", ".html"))
            .build();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /gestion renders the management home greeting the signed-in user")
    void rendersHomeWithTheUsername() throws Exception {
        var authentication = new UsernamePasswordAuthenticationToken(
                "docente@menta.dance", null, List.of(new SimpleGrantedAuthority("ROLE_INSTRUCTOR")));

        mockMvc.perform(get("/gestion").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(view().name("gestion/home"))
                .andExpect(model().attribute("username", "docente@menta.dance"));
    }

    @Test
    @DisplayName("renders without a username when there is no authentication")
    void rendersWithoutUsernameWhenAnonymous() throws Exception {
        mockMvc.perform(get("/gestion"))
                .andExpect(status().isOk())
                .andExpect(view().name("gestion/home"))
                .andExpect(model().attributeDoesNotExist("username"));
    }
}
