package com.menta.auth.infrastructure.web.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.auth.application.port.in.RegisterUserUseCase;
import com.menta.auth.domain.model.Role;
import com.menta.auth.infrastructure.web.dto.RegisterUserRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * UserController unit tests at the web boundary (MockMvc standalone setup).
 *
 * POST /api/v1/users/register predates the auth-account-activation change
 * and used to be wired to a working registration flow. Since task 1.5 it
 * would otherwise delegate to placeholder infrastructure adapters
 * (see AuthConfiguration) that throw UnsupportedOperationException. Per the
 * plan's own task 3.1 ("exponer POST /api/v1/auth/register y mantener alias
 * temporal /api/v1/users/register sobre el mismo port-in"), this route stays
 * disabled — returning 503 without ever reaching the use case — until PR3
 * rewires it against real, working adapters.
 */
class UserControllerTest {

    private MockMvc mockMvc;
    private RegisterUserUseCase registerUserUseCase;

    private static final String REGISTER_URL = "/api/v1/users/register";

    @BeforeEach
    void setUp() {
        this.registerUserUseCase = mock(RegisterUserUseCase.class);
        UserController controller = new UserController(registerUserUseCase);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void register_returns_503_and_never_invokes_the_use_case() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String body = objectMapper.writeValueAsString(
            new RegisterUserRequest("user@example.com", "SecurePass123!", Role.STUDENT)
        );

        mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(registerUserUseCase);
    }
}
