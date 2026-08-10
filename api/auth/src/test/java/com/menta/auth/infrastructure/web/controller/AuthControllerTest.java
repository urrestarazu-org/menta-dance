package com.menta.auth.infrastructure.web.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * AuthController unit tests at the web boundary (MockMvc standalone setup).
 *
 * Standalone setup keeps the test focused on the controller's contract:
 *   - request body validation & mapping to command DTOs;
 *   - @ExceptionHandler RFC 9457 status code & body fan-out;
 *   - response wire shape (snake_case JSON for the auth-login spec).
 *
 * Security wiring and full-stack integration coverage live in
 * AuthFlowIntegrationTest.
 *
 * RED-first contract for AuthController:
 *   - POST /api/v1/auth/login 200 with TokenResponse.
 *   - Refresh and logout accept their sensitive token only via X-Refresh-Token.
 *   - Validation and domain errors use application/problem+json.
 */
class AuthControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private LoginUseCase loginUseCase;
    private RefreshTokenUseCase refreshTokenUseCase;
    private LogoutUseCase logoutUseCase;

    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String REFRESH_URL = "/api/v1/auth/refresh";
    private static final String LOGOUT_URL = "/api/v1/auth/logout";
    private static final String REFRESH_TOKEN_HEADER = "X-Refresh-Token";

    @BeforeEach
    void setUp() {
        this.loginUseCase = mock(LoginUseCase.class);
        this.refreshTokenUseCase = mock(RefreshTokenUseCase.class);
        this.logoutUseCase = mock(LogoutUseCase.class);
        this.objectMapper = Jackson2ObjectMapperBuilder.json()
            .modules(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
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
            .andExpect(header().exists("X-Refresh-Token"))
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
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type", is("https://menta.dance/problems/invalid_credentials")))
            .andExpect(jsonPath("$.title", is("Unauthorized")))
            .andExpect(jsonPath("$.status", is(401)))
            .andExpect(jsonPath("$.detail", is("Authentication failed.")))
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
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
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
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type", is("https://menta.dance/problems/auth_degraded")))
            .andExpect(jsonPath("$.title", is("Service Unavailable")))
            .andExpect(jsonPath("$.status", is(503)))
            .andExpect(jsonPath("$.detail", is("Authentication is temporarily unavailable.")))
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

        String refreshToken = UUID.randomUUID().toString();

        mockMvc.perform(post(REFRESH_URL)
                .header(REFRESH_TOKEN_HEADER, refreshToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token", is("rotated-jwt")))
            .andExpect(header().exists("X-Refresh-Token"))
            .andExpect(jsonPath("$.token_type", is("Bearer")));
        verify(refreshTokenUseCase).execute(new RefreshCommand(refreshToken));
    }

    @Test
    void refresh_returns_401_on_refresh_compromised() throws Exception {
        when(refreshTokenUseCase.execute(any(RefreshCommand.class)))
            .thenThrow(new RefreshTokenCompromisedException());

        mockMvc.perform(post(REFRESH_URL)
                .header(REFRESH_TOKEN_HEADER, "compromised-refresh"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("REFRESH_TOKEN_COMPROMISED")));
    }

    @Test
    void logout_returns_204_on_success() throws Exception {
        String refreshToken = UUID.randomUUID().toString();

        mockMvc.perform(post(LOGOUT_URL)
                .header(REFRESH_TOKEN_HEADER, refreshToken))
            .andExpect(status().isNoContent());
        verify(logoutUseCase).execute(new LogoutCommand(refreshToken));
    }

    @Test
    void logout_returns_401_on_refresh_compromised() throws Exception {
        doThrow(new RefreshTokenCompromisedException())
            .when(logoutUseCase).execute(any(LogoutCommand.class));

        mockMvc.perform(post(LOGOUT_URL)
                .header(REFRESH_TOKEN_HEADER, "compromised-refresh"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("REFRESH_TOKEN_COMPROMISED")));
    }

    @Test
    void refresh_rejects_a_missing_header_with_an_rfc_9457_problem() throws Exception {
        mockMvc.perform(post(REFRESH_URL))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type", is("https://menta.dance/problems/invalid_auth_request")))
            .andExpect(jsonPath("$.title", is("Bad Request")))
            .andExpect(jsonPath("$.status", is(400)))
            .andExpect(jsonPath("$.detail", is("The authentication request is invalid.")))
            .andExpect(jsonPath("$.code", is("INVALID_AUTH_REQUEST")));
    }

    @Test
    void refresh_rejects_an_empty_or_whitespace_refresh_token_header() throws Exception {
        for (String refreshToken : new String[] {"", "   "}) {
            mockMvc.perform(post(REFRESH_URL)
                    .header(REFRESH_TOKEN_HEADER, refreshToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code", is("INVALID_AUTH_REQUEST")));
        }
    }

    @Test
    void logout_rejects_an_empty_or_whitespace_refresh_token_header() throws Exception {
        for (String refreshToken : new String[] {"", "   "}) {
            mockMvc.perform(post(LOGOUT_URL)
                    .header(REFRESH_TOKEN_HEADER, refreshToken))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code", is("INVALID_AUTH_REQUEST")));
        }
    }

    @Test
    void login_validation_returns_an_rfc_9457_problem() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_AUTH_REQUEST")));
    }

    @Test
    void login_rejects_malformed_json_with_an_rfc_9457_problem() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type", is("https://menta.dance/problems/invalid_auth_request")))
            .andExpect(jsonPath("$.title", is("Bad Request")))
            .andExpect(jsonPath("$.status", is(400)))
            .andExpect(jsonPath("$.detail", is("The authentication request is invalid.")))
            .andExpect(jsonPath("$.code", is("INVALID_AUTH_REQUEST")));
    }

    @Test
    void refresh_does_not_accept_a_refresh_token_from_json() throws Exception {
        mockMvc.perform(post(REFRESH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"must-not-be-read\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void logout_does_not_accept_a_refresh_token_from_json() throws Exception {
        mockMvc.perform(post(LOGOUT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"must-not-be-read\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_AUTH_REQUEST")));
    }

    @Test
    void legacy_auth_routes_are_not_mapped() throws Exception {
        mockMvc.perform(post("/auth/login"))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/auth/refresh"))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/auth/logout"))
            .andExpect(status().isNotFound());
    }
}
