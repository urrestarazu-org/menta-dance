package com.menta.auth.infrastructure.web.controller;

import com.menta.auth.application.dto.RegisterUserCommand;
import com.menta.auth.application.port.in.RegisterUserUseCase;
import com.menta.auth.infrastructure.web.dto.RegisterUserRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user operations.
 * Infrastructure layer - contains Spring annotations.
 */
@RestController
@RequestMapping({"/api/v1/auth", "/api/v1/users"})
@PublicActivationEndpoint
public class UserController {

    private final RegisterUserUseCase registerUserUseCase;
    private final ClientFingerprint clientFingerprint;

    public UserController(RegisterUserUseCase registerUserUseCase, ClientFingerprint clientFingerprint) {
        this.registerUserUseCase = registerUserUseCase;
        this.clientFingerprint = clientFingerprint;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(
        @Valid @RequestBody RegisterUserRequest request,
        HttpServletRequest servletRequest
    ) {
        String clientFingerprint = this.clientFingerprint.from(servletRequest);
        registerUserUseCase.register(
            new RegisterUserCommand(
                request.email(), request.password(), request.role(),
                request.firstName(), request.lastName(), request.phone(), request.birthDate(),
                clientFingerprint
            )
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> handleSemanticRegistrationRejection(IllegalArgumentException exception) {
        return ResponseEntity.accepted().build();
    }

    /**
     * A controller-local handler beats every advice and also matches an exception through its cause chain, so the
     * handler above would turn an unreadable body whose cause is an {@code IllegalArgumentException} into the
     * anti-enumeration {@code 202}. Matching the exact type here keeps a decoding failure a {@code 400}.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadableBody() {
        return AuthRequestExceptionHandler.invalidRequest();
    }
}
