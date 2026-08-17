package com.menta.auth.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.auth.application.dto.RequestPasswordResetCommand;
import com.menta.auth.application.dto.RequestPasswordResetResult;
import com.menta.auth.application.dto.ResetPasswordCommand;
import com.menta.auth.application.port.in.RequestPasswordResetUseCase;
import com.menta.auth.application.port.in.ResetPasswordUseCase;
import com.menta.auth.domain.exception.PasswordResetRateLimitedException;
import com.menta.auth.domain.exception.PasswordResetTokenAlreadyUsedException;
import com.menta.auth.domain.exception.PasswordResetTokenExpiredException;
import com.menta.auth.domain.exception.PasswordResetTokenNotFoundException;
import com.menta.auth.domain.exception.SamePasswordException;
import com.menta.auth.domain.exception.WeakPasswordException;
import com.menta.auth.domain.model.PasswordPolicyViolation;

import java.time.Duration;
import java.util.EnumSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PasswordResetControllerTest {

    private MockMvc mockMvc;
    private RequestPasswordResetUseCase requestPasswordResetUseCase;
    private ResetPasswordUseCase resetPasswordUseCase;

    @BeforeEach
    void setUp() {
        requestPasswordResetUseCase = mock(RequestPasswordResetUseCase.class);
        resetPasswordUseCase = mock(ResetPasswordUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PasswordResetController(
                    requestPasswordResetUseCase,
                    resetPasswordUseCase,
                    new ClientFingerprint("172.16.0.0/12")
                ))
            .setControllerAdvice(new PasswordResetExceptionHandler())
            .build();
    }

    @Test
    void forgot_password_is_uniform_for_unknown_active_and_inactive_accounts() throws Exception {
        when(requestPasswordResetUseCase.request(any())).thenReturn(RequestPasswordResetResult.ACKNOWLEDGED);

        for (String email : new String[] {"unknown@example.com", "active@example.com", "locked@example.com"}) {
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));
        }

        org.mockito.ArgumentCaptor<RequestPasswordResetCommand> captor =
            org.mockito.ArgumentCaptor.forClass(RequestPasswordResetCommand.class);
        verify(requestPasswordResetUseCase, org.mockito.Mockito.times(3)).request(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(RequestPasswordResetCommand::email)
            .containsExactly("unknown@example.com", "active@example.com", "locked@example.com");
    }

    @Test
    void forgot_password_rate_limit_has_retry_after() throws Exception {
        when(requestPasswordResetUseCase.request(any()))
            .thenThrow(new PasswordResetRateLimitedException(Duration.ofSeconds(42)));

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"pending@example.com\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", is("42")))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("PASSWORD_RESET_RATE_LIMITED")));
    }

    @Test
    void forgot_password_maps_bean_validation_failures_without_leaking_field_text() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("El formato del email es inválido")
            )));
    }

    @Test
    void reset_password_succeeds_with_empty_200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"raw-token\",\"newPassword\":\"NewSecurePass1\"}"))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        org.mockito.ArgumentCaptor<ResetPasswordCommand> captor =
            org.mockito.ArgumentCaptor.forClass(ResetPasswordCommand.class);
        verify(resetPasswordUseCase).reset(captor.capture());
        assertThat(captor.getValue().rawToken()).isEqualTo("raw-token");
        assertThat(captor.getValue().newPassword()).isEqualTo("NewSecurePass1");
    }

    @Test
    void reset_password_unknown_token_is_404() throws Exception {
        doThrow(new PasswordResetTokenNotFoundException()).when(resetPasswordUseCase).reset(any());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"unknown\",\"newPassword\":\"NewSecurePass1\"}"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("PASSWORD_RESET_TOKEN_NOT_FOUND")));
    }

    @Test
    void reset_password_expired_token_is_410() throws Exception {
        doThrow(new PasswordResetTokenExpiredException()).when(resetPasswordUseCase).reset(any());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"expired\",\"newPassword\":\"NewSecurePass1\"}"))
            .andExpect(status().isGone())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("PASSWORD_RESET_TOKEN_EXPIRED")));
    }

    @Test
    void reset_password_already_used_token_is_410() throws Exception {
        doThrow(new PasswordResetTokenAlreadyUsedException()).when(resetPasswordUseCase).reset(any());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"used\",\"newPassword\":\"NewSecurePass1\"}"))
            .andExpect(status().isGone())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("PASSWORD_RESET_TOKEN_ALREADY_USED")));
    }

    @Test
    void reset_password_weak_password_lists_every_unmet_rule() throws Exception {
        doThrow(new WeakPasswordException(EnumSet.of(
            PasswordPolicyViolation.TOO_SHORT, PasswordPolicyViolation.MISSING_DIGIT
        ))).when(resetPasswordUseCase).reset(any());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"raw-token\",\"newPassword\":\"weak\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("WEAK_PASSWORD")))
            .andExpect(jsonPath("$.detail", is(
                "La contraseña no cumple los requisitos: al menos 8 caracteres, al menos un número."
            )));
    }

    @Test
    void reset_password_same_password_is_400() throws Exception {
        doThrow(new SamePasswordException()).when(resetPasswordUseCase).reset(any());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"raw-token\",\"newPassword\":\"CurrentPass1\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("SAME_PASSWORD")));
    }

    @Test
    void reset_password_rate_limited_is_429() throws Exception {
        doThrow(new PasswordResetRateLimitedException(Duration.ofSeconds(5)))
            .when(resetPasswordUseCase).reset(any());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"raw-token\",\"newPassword\":\"NewSecurePass1\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", is("5")))
            .andExpect(jsonPath("$.code", is("PASSWORD_RESET_RATE_LIMITED")));
    }

    @Test
    void reset_password_maps_bean_validation_failures_without_leaking_field_text() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"\",\"newPassword\":\"NewSecurePass1\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("El token es obligatorio")
            )));
    }
}
