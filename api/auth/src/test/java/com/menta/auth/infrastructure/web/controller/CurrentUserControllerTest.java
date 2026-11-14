package com.menta.auth.infrastructure.web.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.auth.application.dto.CurrentUser;
import com.menta.auth.application.port.in.GetCurrentUserUseCase;
import com.menta.auth.domain.exception.UserNotFoundException;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * MockMvc slice for the own-account endpoint (#749). That only authenticated callers reach it belongs to
 * {@code SecurityConfig}, not to this controller (see {@code SecurityConfigTest}).
 */
class CurrentUserControllerTest {

    private static final String URL = "/api/v1/users/me";
    private static final UUID USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private GetCurrentUserUseCase getCurrentUserUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        getCurrentUserUseCase = mock(GetCurrentUserUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CurrentUserController(getCurrentUserUseCase))
            .setControllerAdvice(new CurrentUserExceptionHandler())
            .build();
    }

    private static Authentication principal(Role role) {
        return new UsernamePasswordAuthenticationToken(
            USER_ID.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void returns_only_the_id_names_and_role_of_the_token_owner(Role role) throws Exception {
        when(getCurrentUserUseCase.get(USER_ID)).thenReturn(new CurrentUser(USER_ID, "Ada", "Lovelace", role));

        mockMvc.perform(get(URL).principal(principal(role)))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", aMapWithSize(4)))
            .andExpect(jsonPath("$.userId", is(USER_ID.toString())))
            .andExpect(jsonPath("$.firstName", is("Ada")))
            .andExpect(jsonPath("$.lastName", is("Lovelace")))
            .andExpect(jsonPath("$.role", is(role.name())))
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.status").doesNotExist())
            .andExpect(jsonPath("$.phone").doesNotExist())
            .andExpect(jsonPath("$.birthDate").doesNotExist());
    }

    @Test
    void returns_null_names_for_an_account_without_profile() throws Exception {
        when(getCurrentUserUseCase.get(USER_ID)).thenReturn(new CurrentUser(USER_ID, null, null, Role.ADMIN));

        mockMvc.perform(get(URL).principal(principal(Role.ADMIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", aMapWithSize(4)))
            .andExpect(jsonPath("$.firstName", nullValue()))
            .andExpect(jsonPath("$.lastName", nullValue()))
            .andExpect(jsonPath("$.role", is("ADMIN")));
    }

    @Test
    void maps_a_token_whose_account_no_longer_exists_to_401_authentication_required() throws Exception {
        when(getCurrentUserUseCase.get(USER_ID)).thenThrow(new UserNotFoundException(UserId.of(USER_ID)));

        mockMvc.perform(get(URL).principal(principal(Role.STUDENT)))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status", is(401)))
            .andExpect(jsonPath("$.title", is("Unauthorized")))
            .andExpect(jsonPath("$.type", is("https://menta.dance/problems/authentication_required")))
            .andExpect(jsonPath("$.code", is("AUTHENTICATION_REQUIRED")))
            .andExpect(jsonPath("$.detail", is("Authentication is required.")));
    }
}
