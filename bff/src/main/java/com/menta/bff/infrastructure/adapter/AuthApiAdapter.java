package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.dto.LoginCommand;
import com.menta.bff.application.dto.RegisterCommand;
import com.menta.bff.application.dto.RequestPasswordResetCommand;
import com.menta.bff.application.dto.ResendActivationCommand;
import com.menta.bff.application.dto.TokenPairResponse;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.infrastructure.config.AuthProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

/**
 * WebClient adapter for Auth API communication.
 *
 * <p>Implements {@link AuthApiClient} port using Spring WebClient
 * to call Auth API endpoints (login, refresh, logout).
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
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
    private static final String REGISTER_ENDPOINT = "/api/v1/auth/register";
    private static final String ACTIVATE_ENDPOINT = "/api/v1/auth/activate";
    private static final String RESEND_ACTIVATION_ENDPOINT = "/api/v1/auth/resend-activation";

    private static final String FORGOT_PASSWORD_ENDPOINT = "/api/v1/auth/forgot-password";
    private static final String RESET_PASSWORD_ENDPOINT = "/api/v1/auth/reset-password";
    private static final Pattern PROBLEM_CODE = Pattern.compile("\"code\"\\s*:\\s*\"([A-Z_]{1,64})\"");

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final WebClient webClient;
    private final AuthProperties authProperties;

    /**
     * Forwards the canonical client origin to the Auth API as a single-value
     * {@code X-Forwarded-For} (ADR-0035).
     *
     * <p>Single value, deliberately. The Auth API reads the last element of
     * the chain, so relaying the browser-supplied chain would let a caller
     * append entries and choose which address the API ends up fingerprinting.
     * One resolved address removes that choice.</p>
     *
     * <p>When no origin was established the header is omitted entirely rather
     * than sent empty, so the API falls back to the peer address it observes.</p>
     */
    private static void applyClientOrigin(HttpHeaders headers, String clientAddress) {
        if (clientAddress != null && !clientAddress.isBlank()) {
            headers.set(FORWARDED_FOR_HEADER, clientAddress.trim());
        }
    }

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
                    .headers(headers -> applyClientOrigin(headers, command.clientAddress()))
                    .bodyValue(requestBody)
                    .exchange()
                    .timeout(authProperties.getTimeout())
                    .block();

            return handleTokenResponse(response, "login");

        } catch (AuthenticationException | RefreshTokenRevokedException | AccountLockedException
                 | AccountNotActivatedException | RateLimitedException | ServiceUnavailableException e) {
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
    public void logout(String accessToken, String refreshToken) {
        Objects.requireNonNull(accessToken, "accessToken cannot be null");
        Objects.requireNonNull(refreshToken, "refreshToken cannot be null");

        log.debug("Calling Auth API logout endpoint");

        // The endpoint is authenticated: the bearer identifies the caller, the header names the token to revoke.
        // Neither value is ever logged; send() reports transport errors by class only.
        ApiReply reply = send(webClient.post()
                .uri(LOGOUT_ENDPOINT)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .header(REFRESH_TOKEN_HEADER, refreshToken), "logout");

        int status = reply.status();

        // Success cases: 204 No Content, 404 Not Found (already revoked, idempotent)
        if (status == HttpStatus.NO_CONTENT.value() || status == HttpStatus.NOT_FOUND.value()) {
            return;
        }

        log.warn("Auth API logout returned status {}", status);

        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            throw new AuthenticationException("Auth API rejected the logout credentials: " + status);
        }

        // 5xx and any status the contract does not list: the revocation did NOT happen, so never look like success
        throw new ServiceUnavailableException("Auth API unavailable: " + status);
    }

    @Override
    public void register(RegisterCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        log.debug("Calling Auth API register endpoint");

        // LinkedHashMap, not Map.of: the birth date is optional and is omitted (not sent as null) when absent.
        Map<String, String> requestBody = new LinkedHashMap<>();
        requestBody.put("email", command.email());
        requestBody.put("password", command.password());
        requestBody.put("firstName", command.firstName());
        requestBody.put("lastName", command.lastName());
        requestBody.put("phone", command.phone());
        if (command.birthDate() != null) {
            requestBody.put("birthDate", command.birthDate().toString());
        }
        requestBody.put("role", command.role());

        ApiReply reply = send(webClient.post()
                .uri(REGISTER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> applyClientOrigin(headers, command.clientAddress()))
                .bodyValue(requestBody), "register");

        if (reply.status() != HttpStatus.ACCEPTED.value()) {
            throw failure(reply, "register");
        }
    }

    @Override
    public void validateActivationToken(String token) {
        Objects.requireNonNull(token, "token cannot be null");

        log.debug("Calling Auth API activation token validation endpoint");

        // The token is a URI template variable, so it is percent-encoded as one path segment.
        ApiReply reply = send(webClient.get().uri(ACTIVATE_ENDPOINT + "/{token}", token), "validate-activation");

        if (reply.status() != HttpStatus.NO_CONTENT.value()) {
            throw activationFailure(reply, "validate-activation");
        }
    }

    @Override
    public void activate(String token) {
        Objects.requireNonNull(token, "token cannot be null");

        log.debug("Calling Auth API activate endpoint");

        ApiReply reply = send(webClient.post()
                .uri(ACTIVATE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("token", token)), "activate");

        if (reply.status() != HttpStatus.NO_CONTENT.value()) {
            throw activationFailure(reply, "activate");
        }
    }

    @Override
    public void resendActivation(ResendActivationCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        log.debug("Calling Auth API resend-activation endpoint");

        ApiReply reply = send(webClient.post()
                .uri(RESEND_ACTIVATION_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> applyClientOrigin(headers, command.clientAddress()))
                .bodyValue(Map.of("email", command.email())), "resend-activation");

        if (reply.status() != HttpStatus.ACCEPTED.value()) {
            throw failure(reply, "resend-activation");
        }
    }

    @Override
    public void requestPasswordReset(RequestPasswordResetCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        log.debug("Calling Auth API forgot-password endpoint");

        ApiReply reply = send(webClient.post()
                .uri(FORGOT_PASSWORD_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> applyClientOrigin(headers, command.clientAddress()))
                .bodyValue(Map.of("email", command.email())), "forgot-password");

        if (reply.status() != HttpStatus.ACCEPTED.value()) {
            throw failure(reply, "forgot-password");
        }
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        Objects.requireNonNull(token, "token cannot be null");
        Objects.requireNonNull(newPassword, "newPassword cannot be null");

        log.debug("Calling Auth API reset-password endpoint");

        ApiReply reply = send(webClient.post()
                .uri(RESET_PASSWORD_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("token", token, "newPassword", newPassword)), "reset-password");

        if (reply.status() != HttpStatus.OK.value()) {
            throw resetFailure(reply);
        }
    }

    /**
     * Sends a request and keeps only what the callers need: status, Retry-After and, for a 400,
     * the problem {@code code}.
     *
     * <p>The body is released unread — the public account endpoints answer with empty
     * bodies on success and RFC 9457 problems on failure, and neither is ever shown to
     * the browser. Only the {@code code} of a 400 is read, so password recovery can tell a weak
     * password from a malformed request. Transport errors are reported by class only: their message can carry
     * the request URI, which for activation contains the single-use token.
     * </p>
     */
    private ApiReply send(WebClient.RequestHeadersSpec<?> request, String operation) {
        ApiReply reply;
        try {
            reply = request
                    .exchangeToMono(response -> problemCode(response).map(code -> new ApiReply(
                            response.statusCode().value(),
                            response.headers().asHttpHeaders().getFirst(HttpHeaders.RETRY_AFTER), code)))
                    .timeout(authProperties.getTimeout())
                    .block();
        } catch (RuntimeException e) {
            log.error("Auth API {} failed: {}", operation, e.getClass().getSimpleName());
            throw new ServiceUnavailableException("Auth API unavailable: " + e.getClass().getSimpleName(), e);
        }
        if (reply == null) {
            throw new ServiceUnavailableException("Auth API returned null response");
        }
        return reply;
    }

    private static Mono<String> problemCode(ClientResponse response) {
        if (response.statusCode().value() != HttpStatus.BAD_REQUEST.value()) {
            return response.releaseBody().thenReturn("");
        }
        return response.bodyToMono(String.class)
                .map(body -> {
                    Matcher matcher = PROBLEM_CODE.matcher(body);
                    return matcher.find() ? matcher.group(1) : "";
                })
                .defaultIfEmpty("")
                .onErrorReturn("");
    }

    /**
     * Maps an unsuccessful reply of the public account endpoints to a domain exception.
     * Any status the contract does not list degrades to "unavailable" rather than a 500.
     */
    private static RuntimeException failure(ApiReply reply, String operation) {
        int status = reply.status();
        log.warn("Auth API {} returned status {}", operation, status);
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return new RateLimitedException("Auth API rate limit hit", parseRetryAfter(reply.retryAfter()));
        }
        if (status == HttpStatus.BAD_REQUEST.value()) {
            return new InvalidRequestException("Auth API rejected the " + operation + " request");
        }
        return new ServiceUnavailableException("Auth API unavailable: " + status);
    }

    /**
     * Activation answers 400 for an invalid, expired or used token, which is a verdict on the
     * token rather than on the request shape; everything else follows {@link #failure}.
     */
    private static RuntimeException activationFailure(ApiReply reply, String operation) {
        if (reply.status() == HttpStatus.BAD_REQUEST.value()) {
            log.warn("Auth API {} rejected the activation token", operation);
            return new InvalidActivationTokenException("Auth API rejected the activation token");
        }
        return failure(reply, operation);
    }

    /**
     * Password reset tells its causes apart: 404/410 are verdicts on the token and a 400 may be
     * a verdict on the password; everything else follows {@link #failure}.
     */
    private static RuntimeException resetFailure(ApiReply reply) {
        int status = reply.status();
        if (status == HttpStatus.NOT_FOUND.value() || status == HttpStatus.GONE.value()) {
            log.warn("Auth API reset-password rejected the reset token");
            return new InvalidResetTokenException("Auth API rejected the password reset token");
        }
        if (status == HttpStatus.BAD_REQUEST.value() && "WEAK_PASSWORD".equals(reply.code())) {
            return new PasswordRejectedException(PasswordRejectedException.Reason.WEAK);
        }
        if (status == HttpStatus.BAD_REQUEST.value() && "SAME_PASSWORD".equals(reply.code())) {
            return new PasswordRejectedException(PasswordRejectedException.Reason.SAME_AS_CURRENT);
        }
        return failure(reply, "reset-password");
    }

    private static Duration parseRetryAfter(String value) {
        if (value == null) {
            return null;
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        } catch (NumberFormatException e) {
            // Retry-After may also be an HTTP date; the UI only needs a coarse hint.
            return null;
        }
    }

    private record ApiReply(int status, String retryAfter, String code) {
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

        if ("login".equals(operation)) {
            throw loginFailure(status, response.headers().asHttpHeaders().getFirst(HttpHeaders.RETRY_AFTER),
                    errorBody);
        }

        if (status == HttpStatus.UNAUTHORIZED) {
            throw new AuthenticationException("Invalid credentials or refresh token: " + errorBody);
        }

        if (status == HttpStatus.FORBIDDEN) {
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
     * Maps an unsuccessful login answer to a domain exception.
     *
     * <p>423, 429 and 403 ACCOUNT_NOT_ACTIVATED are only answered by the API after the password was
     * verified (423, 403) or as a throttle (429), so each gets its own type. Any status the contract
     * does not list degrades to "unavailable" rather than a server error.
     * </p>
     */
    private static RuntimeException loginFailure(HttpStatus status, String retryAfter, String errorBody) {
        if (status == HttpStatus.UNAUTHORIZED) {
            return new AuthenticationException("Invalid credentials or refresh token: " + errorBody);
        }
        if (status == HttpStatus.LOCKED) {
            return new AccountLockedException("Auth API reports the account as locked");
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            return new RateLimitedException("Auth API login rate limit hit", parseRetryAfter(retryAfter));
        }
        if (status == HttpStatus.FORBIDDEN && "ACCOUNT_NOT_ACTIVATED".equals(problemCodeOf(errorBody))) {
            return new AccountNotActivatedException("Auth API reports the account as not activated");
        }
        return new ServiceUnavailableException("Auth API unavailable: " + status);
    }

    private static String problemCodeOf(String body) {
        if (body == null) {
            return "";
        }
        Matcher matcher = PROBLEM_CODE.matcher(body);
        return matcher.find() ? matcher.group(1) : "";
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
