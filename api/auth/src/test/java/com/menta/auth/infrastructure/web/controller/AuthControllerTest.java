package com.menta.auth.infrastructure.web.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.auth.application.dto.LoginCommand;
import com.menta.auth.application.dto.LogoutCommand;
import com.menta.auth.application.dto.RefreshCommand;
import com.menta.auth.application.dto.TokenPair;
import com.menta.auth.application.port.in.LoginUseCase;
import com.menta.auth.application.port.in.LogoutUseCase;
import com.menta.auth.application.port.in.RefreshTokenUseCase;
import com.menta.auth.domain.exception.AuthDegradedException;
import com.menta.auth.domain.exception.InvalidCredentialsException;
import com.menta.auth.domain.exception.LockedUserException;
import com.menta.auth.domain.exception.RefreshTokenCompromisedException;
import com.menta.auth.infrastructure.web.dto.LoginRequest;
import com.menta.auth.infrastructure.web.dto.LogoutRequest;
import com.menta.auth.infrastructure.web.dto.RefreshRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * AuthController unit tests at the web boundary (MockMvc standalone setup).
 *
 * Standalone setup keeps the test focused on the controller's contract:
 *   - request body validation & mapping to command DTOs;
 *   - @ExceptionHandler status code & body fan-out;
 *   - response wire shape (snake_case JSON for the auth-login spec).
 *
 * Security wiring and full-stack integration coverage live in
 * AuthFlowIntegrationTest.
 *
 * RED-first contract for AuthController:
 *   - POST /auth/login 200 with TokenResponse (access_token, refresh_token, token_type, expires_in).
 *   - POST /auth/login 401 on InvalidCredentialsException.
 *   - POST /auth/login 423 on LockedUserException.
 *   - POST /auth/login 503 + Retry-After: 30 on AuthDegradedException.
 *   - POST /auth/refresh 200 with TokenResponse.
 *   - POST /auth/refresh 401 on RefreshTokenCompromisedException.
 *   - POST /auth/logout 204 on success.
 *   - POST /auth/logout 401 on RefreshTokenCompromisedException.
 */
class AuthControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private LoginUseCase loginUseCase;
    private RefreshTokenUseCase refreshTokenUseCase;
    private LogoutUseCase logoutUseCase;

    private static final String LOGIN_URL = "/auth/login";
    private static final String REFRESH_URL = "/auth/refresh";
    private static final String LOGOUT_URL = "/auth/logout";

    @BeforeEach
    void setUp() {
        this.loginUseCase = mock(LoginUseCase.class);
        this.refreshTokenUseCase = mock(RefreshTokenUseCase.class);
        this.logoutUseCase = mock(LogoutUseCase.class);
        this.objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        AuthController controller = new AuthController(
            loginUseCase, refreshTokenUseCase, logoutUseCase
        );
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }

    @Test
    void login_returns_200_with_token_pair_on_success() throws Exception {
        TokenPair pair = new TokenPair(
            "compact-jwt",
            UUID.randomUUID().toString(),
            TokenPair.TOKEN_TYPE_BEARER,
            Duration.ofMinutes(15)
        );
        when(loginUseCase.execute(any(LoginCommand.class))).thenReturn(pair);

        String body = objectMapper.writeValueAsString(
            new LoginRequest("user@example.com", "SecurePass123!")
        );

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.access_token", is("compact-jwt")))
            .andExpect(jsonPath("$.refresh_token", notNullValue()))
            .andExpect(jsonPath("$.token_type", is("Bearer")))
            .andExpect(jsonPath("$.expires_in", notNullValue()));
    }

    @Test
    void login_returns_401_on_invalid_credentials() throws Exception {
        when(loginUseCase.execute(any(LoginCommand.class)))
            .thenThrow(new InvalidCredentialsException());

        String body = objectMapper.writeValueAsString(
            new LoginRequest("user@example.com", "WrongPass!")
        );

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code", is("INVALID_CREDENTIALS")));
    }

    @Test
    void login_returns_423_on_locked_user() throws Exception {
        when(loginUseCase.execute(any(LoginCommand.class)))
            .thenThrow(new LockedUserException(UUID.randomUUID()));

        String body = objectMapper.writeValueAsString(
            new LoginRequest("locked@example.com", "CorrectPass!")
        );

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isLocked())
            .andExpect(jsonPath("$.code", is("USER_LOCKED")));
    }

    @Test
    void login_returns_503_with_retry_after_on_auth_degraded() throws Exception {
        when(loginUseCase.execute(any(LoginCommand.class)))
            .thenThrow(new AuthDegradedException());

        String body = objectMapper.writeValueAsString(
            new LoginRequest("user@example.com", "CorrectPass!")
        );

        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", is("30")))
            .andExpect(jsonPath("$.code", is("AUTH_DEGRADED")));
    }

    @Test
    void refresh_returns_200_with_token_pair_on_success() throws Exception {
        TokenPair pair = new TokenPair(
            "rotated-jwt",
            UUID.randomUUID().toString(),
            TokenPair.TOKEN_TYPE_BEARER,
            Duration.ofMinutes(15)
        );
        when(refreshTokenUseCase.execute(any(RefreshCommand.class))).thenReturn(pair);

        String body = objectMapper.writeValueAsString(
            new RefreshRequest(UUID.randomUUID().toString())
        );

        mockMvc.perform(post(REFRESH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token", is("rotated-jwt")))
            .andExpect(jsonPath("$.refresh_token", notNullValue()))
            .andExpect(jsonPath("$.token_type", is("Bearer")));
    }

    @Test
    void refresh_returns_401_on_refresh_compromised() throws Exception {
        when(refreshTokenUseCase.execute(any(RefreshCommand.class)))
            .thenThrow(new RefreshTokenCompromisedException());

        String body = objectMapper.writeValueAsString(
            new RefreshRequest("compromised-refresh")
        );

        mockMvc.perform(post(REFRESH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code", is("REFRESH_TOKEN_COMPROMISED")));
    }

    @Test
    void logout_returns_204_on_success() throws Exception {
        String body = objectMapper.writeValueAsString(
            new LogoutRequest(UUID.randomUUID().toString())
        );

        mockMvc.perform(post(LOGOUT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isNoContent());
    }

    @Test
    void logout_returns_401_on_refresh_compromised() throws Exception {
        doThrow(new RefreshTokenCompromisedException())
            .when(logoutUseCase).execute(any(LogoutCommand.class));

        String body = objectMapper.writeValueAsString(
            new LogoutRequest("compromised-refresh")
        );

        mockMvc.perform(post(LOGOUT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code", is("REFRESH_TOKEN_COMPROMISED")));
    }
}
