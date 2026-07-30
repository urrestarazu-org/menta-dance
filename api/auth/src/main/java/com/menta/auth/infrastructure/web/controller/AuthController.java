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
import com.menta.auth.domain.exception.RefreshTokenCompromisedException;
import com.menta.auth.infrastructure.web.dto.ErrorResponse;
import com.menta.auth.infrastructure.web.dto.LoginRequest;
import com.menta.auth.infrastructure.web.dto.LogoutRequest;
import com.menta.auth.infrastructure.web.dto.RefreshRequest;
import com.menta.auth.infrastructure.web.dto.TokenResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
 * SecurityConfig registers JwtAuthenticationFilter + RoleAuthorizationManager
 * bean; the controller does NOT enforce roles itself — paths under
 * /auth/{login,refresh,logout} are public (Spring Security permitAll) so
 * clients without a JWT can authenticate.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    static final String RECONCILER_RETRY_SECONDS = "30";

    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;

    public AuthController(
        LoginUseCase loginUseCase,
        RefreshTokenUseCase refreshTokenUseCase,
        LogoutUseCase logoutUseCase
    ) {
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginCommand command = new LoginCommand(request.email(), request.password());
        TokenPair pair = loginUseCase.execute(command);
        return ResponseEntity.ok(toResponse(pair));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshCommand command = new RefreshCommand(request.refreshToken());
        TokenPair pair = refreshTokenUseCase.execute(command);
        return ResponseEntity.ok(toResponse(pair));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        LogoutCommand command = new LogoutCommand(request.refreshToken());
        logoutUseCase.execute(command);
        return ResponseEntity.noContent().build();
    }

    // -- @ExceptionHandler fan-out ---------------------------------------

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(RefreshTokenCompromisedException.class)
    public ResponseEntity<ErrorResponse> handleRefreshCompromised(
        RefreshTokenCompromisedException ex
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(LockedUserException.class)
    public ResponseEntity<ErrorResponse> handleLockedUser(LockedUserException ex) {
        return ResponseEntity.status(HttpStatus.LOCKED)
            .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(AuthDegradedException.class)
    public ResponseEntity<ErrorResponse> handleAuthDegraded(AuthDegradedException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, RECONCILER_RETRY_SECONDS)
            .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    private static TokenResponse toResponse(TokenPair pair) {
        return new TokenResponse(
            pair.accessToken(),
            pair.refreshToken(),
            pair.tokenType(),
            pair.expiresIn()
        );
    }
}
