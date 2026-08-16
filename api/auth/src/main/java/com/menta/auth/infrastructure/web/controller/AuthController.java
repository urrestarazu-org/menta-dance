package com.menta.auth.infrastructure.web.controller;

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
import com.menta.auth.domain.exception.LoginRateLimitedException;
import com.menta.auth.domain.exception.RefreshTokenCompromisedException;
import com.menta.auth.infrastructure.web.ProblemDetails;
import com.menta.auth.infrastructure.web.dto.LoginRequest;
import com.menta.auth.infrastructure.web.dto.TokenResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;

/**
 * REST controller for the auth domain.
 *
 * Three endpoints wired to the application use cases via their port-in
 * contracts (LoginUseCase, RefreshTokenUseCase, LogoutUseCase). The
 * controller maps:
 *   - HTTP request body @Valid DTO → application command;
 *   - application TokenPair → wire-shaped TokenResponse (snake_case JSON);
 *   - domain exceptions → status codes via @ExceptionHandler.
 *
 * Status mapping (spec):
 *   login → 200 / 401 / 423 / 503 (Retry-After)
 *   refresh → 200 / 401 / 503
 *   logout → 204 / 401 / 503
 *
 * Security note:
 *   - refresh_token is returned in X-Refresh-Token HTTP header (not in body)
 *   - access_token is returned in response body
 *
 * SecurityConfig registers JwtAuthenticationFilter + RoleAuthorizationManager
 * bean; the controller does NOT enforce roles itself — paths under
 * /api/v1/auth/login and /api/v1/auth/refresh are public. Logout is protected
 * by Spring Security and additionally receives the refresh only in a header.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String RECONCILER_RETRY_SECONDS = "30";
    static final String REFRESH_TOKEN_HEADER = "X-Refresh-Token";

    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ClientFingerprint clientFingerprint;

    public AuthController(
        LoginUseCase loginUseCase,
        RefreshTokenUseCase refreshTokenUseCase,
        LogoutUseCase logoutUseCase,
        ClientFingerprint clientFingerprint
    ) {
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.clientFingerprint = clientFingerprint;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        // The application layer never sees the servlet request: infrastructure
        // derives an opaque origin fingerprint and passes it as a value. Since
        // ADR-0035 the BFF forwards the real client address, so this identifies
        // the user's origin rather than the BFF itself.
        LoginCommand command = new LoginCommand(
            request.email(), request.password(), clientFingerprint.from(httpRequest)
        );
        TokenPair pair = loginUseCase.execute(command);
        return ResponseEntity.ok()
                .header(REFRESH_TOKEN_HEADER, pair.refreshToken())
                .body(toResponse(pair));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
        @RequestHeader(name = REFRESH_TOKEN_HEADER) String refreshToken
    ) {
        RefreshCommand command = new RefreshCommand(requireRefreshToken(refreshToken));
        TokenPair pair = refreshTokenUseCase.execute(command);
        return ResponseEntity.ok()
                .header(REFRESH_TOKEN_HEADER, pair.refreshToken())
                .body(toResponse(pair));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @RequestHeader(name = REFRESH_TOKEN_HEADER) String refreshToken
    ) {
        LogoutCommand command = new LogoutCommand(requireRefreshToken(refreshToken));
        logoutUseCase.execute(command);
        return ResponseEntity.noContent().build();
    }

    // -- @ExceptionHandler fan-out ---------------------------------------

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ProblemDetails.response(HttpStatus.UNAUTHORIZED, "Authentication failed.", ex.getErrorCode());
    }

    @ExceptionHandler(RefreshTokenCompromisedException.class)
    public ResponseEntity<ProblemDetail> handleRefreshCompromised(
        RefreshTokenCompromisedException ex
    ) {
        return ProblemDetails.response(HttpStatus.UNAUTHORIZED, "Authentication failed.", ex.getErrorCode());
    }

    @ExceptionHandler(LockedUserException.class)
    public ResponseEntity<ProblemDetail> handleLockedUser(LockedUserException ex) {
        return ProblemDetails.response(HttpStatus.LOCKED, "The user account is locked.", ex.getErrorCode());
    }

    /**
     * 429 — temporary throttle, deliberately distinct from the 423 above. The
     * account state is untouched and the window expires on its own;
     * {@code Retry-After} says when. The message is identical whether or not
     * the email exists, so the throttle cannot be used to enumerate accounts.
     */
    @ExceptionHandler(LoginRateLimitedException.class)
    public ResponseEntity<ProblemDetail> handleLoginRateLimited(LoginRateLimitedException ex) {
        long seconds = Math.max(1, ex.getRetryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ProblemDetails.body(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many login attempts; retry later.",
                ex.getErrorCode()
            ));
    }

    @ExceptionHandler(AuthDegradedException.class)
    public ResponseEntity<ProblemDetail> handleAuthDegraded(AuthDegradedException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, RECONCILER_RETRY_SECONDS)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(ProblemDetails.body(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Authentication is temporarily unavailable.",
                ex.getErrorCode()
            ));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        MissingRequestHeaderException.class,
        HttpMessageNotReadableException.class,
        AuthRequestValidationException.class
    })
    public ResponseEntity<ProblemDetail> handleInvalidRequest(Exception ex) {
        return ProblemDetails.response(
            HttpStatus.BAD_REQUEST, "The authentication request is invalid.", "INVALID_AUTH_REQUEST"
        );
    }

    private static TokenResponse toResponse(TokenPair pair) {
        return new TokenResponse(
            pair.accessToken(),
            pair.tokenType(),
            pair.expiresIn()
        );
    }

    private static String requireRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthRequestValidationException();
        }
        return refreshToken;
    }
}
