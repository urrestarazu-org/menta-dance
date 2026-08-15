package com.menta.auth.infrastructure.web.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.auth.application.port.in.RegisterUserUseCase;
import com.menta.auth.application.dto.UserResult;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.exception.DuplicateRegistrationException;
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
 * Both registration URLs are a temporary compatibility pair and must use the
 * same input port rather than maintaining separate registration flows.
 */
class UserControllerTest {

    private MockMvc mockMvc;
    private RegisterUserUseCase registerUserUseCase;

    private static final String LEGACY_REGISTER_URL = "/api/v1/users/register";
    private static final String CANONICAL_REGISTER_URL = "/api/v1/auth/register";

    @BeforeEach
    void setUp() {
        this.registerUserUseCase = mock(RegisterUserUseCase.class);
        UserController controller = new UserController(
            registerUserUseCase, new ClientFingerprint("172.16.0.0/12")
        );
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ActivationExceptionHandler())
            .build();
    }

    @Test
    void canonical_and_legacy_registration_routes_delegate_to_the_same_input_port() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String body = objectMapper.writeValueAsString(
            new RegisterUserRequest("user@example.com", "SecurePass123!", Role.STUDENT)
        );

        when(registerUserUseCase.register(any())).thenReturn(new UserResult(
            "id", "user@example.com", Role.STUDENT, UserStatus.PENDING_ACTIVATION, java.time.LocalDateTime.MIN
        ));

        for (String url : new String[] {CANONICAL_REGISTER_URL, LEGACY_REGISTER_URL}) {
            mockMvc.perform(post(url)
                    .with(request -> {
                        request.setRemoteAddr("203.0.113.11");
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isAccepted());
        }

        verify(registerUserUseCase, org.mockito.Mockito.times(2)).register(any());
    }

    @Test
    void duplicate_registration_has_the_same_uniform_acknowledgement_as_a_fresh_registration() throws Exception {
        when(registerUserUseCase.register(any())).thenThrow(new DuplicateRegistrationException());

        mockMvc.perform(post(CANONICAL_REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"existing@example.com\",\"password\":\"SecurePass123!\",\"role\":\"STUDENT\"}"))
            .andExpect(status().isAccepted())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
    }

    @Test
    void register_maps_bean_validation_failures_to_a_generic_rfc9457_problem_without_leaking_field_text()
        throws Exception {
        mockMvc.perform(post(CANONICAL_REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"\",\"role\":\"STUDENT\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .jsonPath("$.code", org.hamcrest.Matchers.is("INVALID_REQUEST")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .content().string(org.hamcrest.Matchers.not(
                    org.hamcrest.Matchers.containsString("Invalid email format")
                )));
    }

    @Test
    void semantic_registration_rejection_still_returns_the_uniform_acknowledgement() throws Exception {
        when(registerUserUseCase.register(any())).thenThrow(new IllegalArgumentException("email must not be exposed"));

        mockMvc.perform(post(CANONICAL_REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"student@example.com\",\"password\":\"SecurePass123!\",\"role\":\"STUDENT\"}"))
            .andExpect(status().isAccepted())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
    }
}
