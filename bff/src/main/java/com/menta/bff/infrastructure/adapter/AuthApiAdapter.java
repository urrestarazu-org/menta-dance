package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.dto.LoginCommand;
import com.menta.bff.application.dto.TokenPairResponse;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.infrastructure.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;

/**
 * WebClient adapter for Auth API communication.
 * <p>
 * Implements {@link AuthApiClient} port using Spring WebClient
 * to call Auth API endpoints (login, refresh, logout).
 * </p>
 * <p>
 * Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthApiAdapter implements AuthApiClient {

    private static final String REFRESH_TOKEN_HEADER = "X-Refresh-Token";
    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";
    private static final String REFRESH_ENDPOINT = "/api/v1/auth/refresh";
    private static final String LOGOUT_ENDPOINT = "/api/v1/auth/logout";

    private final WebClient webClient;
    private final AuthProperties authProperties;

    @Override
    public TokenPairResponse login(LoginCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        log.debug("Calling Auth API login endpoint");

        try {
            Map<String, String> requestBody = Map.of(
                    "email", command.email(),
                    "password", command.password()
            );

            ClientResponse response = webClient.post()
                    .uri(LOGIN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .exchange()
                    .timeout(authProperties.getTimeout())
                    .block();

            return handleTokenResponse(response, "login");

        } catch (AuthenticationException | RefreshTokenRevokedException e) {
            // Re-throw domain exceptions without wrapping
            throw e;
        } catch (WebClientResponseException e) {
            throw mapHttpException(e, "login");
        } catch (Exception e) {
            log.error("Auth API login failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Auth API unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public TokenPairResponse refresh(String refreshToken) {
        Objects.requireNonNull(refreshToken, "refreshToken cannot be null");

        log.debug("Calling Auth API refresh endpoint");

        try {
            ClientResponse response = webClient.post()
                    .uri(REFRESH_ENDPOINT)
                    .header(REFRESH_TOKEN_HEADER, refreshToken)
                    .exchange()
                    .timeout(authProperties.getTimeout())
                    .block();

            return handleTokenResponse(response, "refresh");

        } catch (AuthenticationException | RefreshTokenRevokedException e) {
            // Re-throw domain exceptions without wrapping
            throw e;
        } catch (WebClientResponseException e) {
            throw mapHttpException(e, "refresh");
        } catch (Exception e) {
            log.error("Auth API refresh failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Auth API unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public void logout(String refreshToken) {
        Objects.requireNonNull(refreshToken, "refreshToken cannot be null");

        log.debug("Calling Auth API logout endpoint");

        try {
            ClientResponse response = webClient.post()
                    .uri(LOGOUT_ENDPOINT)
                    .header(REFRESH_TOKEN_HEADER, refreshToken)
                    .exchange()
                    .timeout(authProperties.getTimeout())
                    .block();

            if (response == null) {
                throw new ServiceUnavailableException("Auth API returned null response");
            }

            HttpStatus status = (HttpStatus) response.statusCode();

            // Success cases: 204 No Content, 404 Not Found (idempotent)
            if (status == HttpStatus.NO_CONTENT || status == HttpStatus.NOT_FOUND) {
                return;
            }

            // Service unavailable
            if (status.is5xxServerError()) {
                throw new ServiceUnavailableException("Auth API unavailable: " + status);
            }

            // Other errors
            String errorBody = response.bodyToMono(String.class).block();
            log.warn("Auth API logout returned unexpected status {}: {}", status, errorBody);

        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
                throw new ServiceUnavailableException("Auth API unavailable", e);
            }
            // Ignore other errors for logout (fail-open)
            log.warn("Auth API logout failed (ignored): {}", e.getMessage());
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Auth API logout failed: {}", e.getMessage());
            throw new ServiceUnavailableException("Auth API unavailable: " + e.getMessage(), e);
        }
    }

    /**
     * Handles token pair response from login/refresh endpoints.
     */
    private TokenPairResponse handleTokenResponse(ClientResponse response, String operation) {
        if (response == null) {
            throw new ServiceUnavailableException("Auth API returned null response");
        }

        HttpStatus status = (HttpStatus) response.statusCode();

        // Success case
        if (status.is2xxSuccessful()) {
            String refreshToken = response.headers().asHttpHeaders().getFirst(REFRESH_TOKEN_HEADER);

            if (refreshToken == null) {
                throw new RuntimeException("Auth API did not return " + REFRESH_TOKEN_HEADER + " header");
            }

            Map<String, Object> body = response.bodyToMono(Map.class).block();
            if (body == null) {
                throw new RuntimeException("Auth API returned empty response body");
            }

            String accessToken = (String) body.get("access_token");
            Object expiresInObj = body.get("expires_in");
            long expiresIn = parseExpiresIn(expiresInObj);

            return new TokenPairResponse(accessToken, refreshToken, expiresIn);
        }

        // Error cases
        String errorBody = response.bodyToMono(String.class).block();
        log.warn("Auth API {} returned status {}: {}", operation, status, errorBody);

        if (status == HttpStatus.UNAUTHORIZED) {
            throw new AuthenticationException("Invalid credentials or refresh token: " + errorBody);
        }

        if (status == HttpStatus.LOCKED) {
            throw new RefreshTokenRevokedException("Token family revoked: " + errorBody);
        }

        if (status.is5xxServerError()) {
            throw new ServiceUnavailableException("Auth API unavailable: " + status);
        }

        throw new RuntimeException("Auth API " + operation + " failed with status " + status + ": " + errorBody);
    }

    /**
     * Parses expires_in field from Auth API response.
     * Auth API returns expires_in as ISO 8601 Duration string (e.g., "PT15M").
     */
    private long parseExpiresIn(Object expiresInObj) {
        if (expiresInObj == null) {
            return 900L; // Default 15 minutes
        }

        if (expiresInObj instanceof Number) {
            return ((Number) expiresInObj).longValue();
        }

        if (expiresInObj instanceof String) {
            try {
                java.time.Duration duration = java.time.Duration.parse((String) expiresInObj);
                return duration.getSeconds();
            } catch (Exception e) {
                log.warn("Failed to parse expires_in '{}', using default 900s", expiresInObj);
                return 900L;
            }
        }

        log.warn("Unexpected expires_in type: {}, using default 900s", expiresInObj.getClass());
        return 900L;
    }

    /**
     * Maps WebClientResponseException to domain exceptions.
     */
    private RuntimeException mapHttpException(WebClientResponseException e, String operation) {
        HttpStatus status = (HttpStatus) e.getStatusCode();

        if (status == HttpStatus.UNAUTHORIZED) {
            return new AuthenticationException("Invalid credentials or refresh token", e);
        }

        if (status == HttpStatus.LOCKED) {
            return new RefreshTokenRevokedException("Token family revoked", e);
        }

        if (status.is5xxServerError()) {
            return new ServiceUnavailableException("Auth API unavailable: " + status, e);
        }

        return new RuntimeException("Auth API " + operation + " failed: " + status, e);
    }
}
