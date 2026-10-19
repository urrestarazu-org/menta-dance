package com.menta.auth.infrastructure.web.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.auth.application.dto.ActivateAccountCommand;
import com.menta.auth.application.dto.ResendActivationCommand;
import com.menta.auth.application.dto.ResendActivationResult;
import com.menta.auth.application.port.in.ActivateAccountUseCase;
import com.menta.auth.application.port.in.ResendActivationUseCase;
import com.menta.auth.application.port.in.ValidateActivationTokenUseCase;
import com.menta.auth.domain.exception.ActivationRateLimitedException;
import com.menta.auth.domain.exception.ActivationTokenInvalidException;
import com.menta.auth.domain.exception.AuthDegradedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ActivationControllerTest {

    private MockMvc mockMvc;
    private ActivateAccountUseCase activateAccountUseCase;
    private ResendActivationUseCase resendActivationUseCase;
    private ValidateActivationTokenUseCase validateActivationTokenUseCase;

    @BeforeEach
    void setUp() {
        activateAccountUseCase = mock(ActivateAccountUseCase.class);
        resendActivationUseCase = mock(ResendActivationUseCase.class);
        validateActivationTokenUseCase = mock(ValidateActivationTokenUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ActivationController(
                    activateAccountUseCase,
                    resendActivationUseCase,
                    validateActivationTokenUseCase,
                    new ClientFingerprint("172.16.0.0/12")
                ))
            .setControllerAdvice(new ActivationExceptionHandler())
            .build();
    }

    @Test
    void activatesValidTokenWithoutReturningBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"valid-token\"}"))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(activateAccountUseCase).activate(new ActivateAccountCommand("valid-token"));
    }

    @Test
    void returnsRfc9457ProblemForInvalidActivationToken() throws Exception {
        doThrow(new ActivationTokenInvalidException()).when(activateAccountUseCase).activate(any());

        mockMvc.perform(post("/api/v1/auth/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"already-used\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type", is("https://menta.dance/problems/activation_token_invalid")))
            .andExpect(jsonPath("$.code", is("ACTIVATION_TOKEN_INVALID")));
    }

    @Test
    void rejects_blank_activation_token_with_a_generic_rfc9457_problem() throws Exception {
        mockMvc.perform(post("/api/v1/auth/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")))
            .andExpect(jsonPath("$.detail", is("La solicitud contiene datos inválidos.")));
    }

    @Test
    void validates_a_get_activation_token_without_activating_the_account() throws Exception {
        mockMvc.perform(get("/api/v1/auth/activate/valid-token"))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(validateActivationTokenUseCase).validate(new ActivateAccountCommand("valid-token"));
        verify(activateAccountUseCase, never()).activate(any());
    }

    @Test
    void returns_rfc9457_problem_for_an_invalid_get_activation_token() throws Exception {
        doThrow(new ActivationTokenInvalidException()).when(validateActivationTokenUseCase).validate(any());

        mockMvc.perform(get("/api/v1/auth/activate/already-used"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("ACTIVATION_TOKEN_INVALID")));

        verify(activateAccountUseCase, never()).activate(any());
    }

    @Test
    void resend_is_uniform_for_unknown_active_and_pending_accounts() throws Exception {
        when(resendActivationUseCase.resend(any())).thenReturn(ResendActivationResult.ACKNOWLEDGED);

        for (String email : new String[] {"unknown@example.com", "active@example.com", "pending@example.com"}) {
            mockMvc.perform(post("/api/v1/auth/resend-activation")
                    .with(request -> {
                        request.setRemoteAddr("203.0.113.21");
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
        }
    }

    @Test
    void resend_rate_limit_has_retry_after_without_revealing_account_state() throws Exception {
        when(resendActivationUseCase.resend(any()))
            .thenThrow(new ActivationRateLimitedException(java.time.Duration.ofSeconds(17)));

        mockMvc.perform(post("/api/v1/auth/resend-activation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"pending@example.com\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", is("17")))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("ACTIVATION_RATE_LIMITED")));
    }

    @Test
    void resend_returns_retryable_problem_when_the_rate_limiter_is_degraded() throws Exception {
        when(resendActivationUseCase.resend(any())).thenThrow(new AuthDegradedException());

        mockMvc.perform(post("/api/v1/auth/resend-activation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"pending@example.com\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", is("30")))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void resend_maps_bean_validation_failures_to_a_generic_rfc9457_problem_without_leaking_field_text()
        throws Exception {
        mockMvc.perform(post("/api/v1/auth/resend-activation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")))
            .andExpect(jsonPath("$.detail", is("La solicitud contiene datos inválidos.")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("El formato del email es inválido")
            )));
    }

    @Test
    void resend_maps_semantic_input_rejection_to_a_generic_rfc9457_problem() throws Exception {
        when(resendActivationUseCase.resend(any()))
            .thenThrow(new IllegalArgumentException("email secret@example.com must not be exposed"));

        mockMvc.perform(post("/api/v1/auth/resend-activation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"student@example.com\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_ACTIVATION_REQUEST")))
            .andExpect(jsonPath("$.detail", is("La solicitud de activación no es válida.")));
    }
}
