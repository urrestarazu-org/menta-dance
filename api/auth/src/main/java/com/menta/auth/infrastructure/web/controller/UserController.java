package com.menta.auth.infrastructure.web.controller;

import com.menta.auth.application.dto.RegisterUserCommand;
import com.menta.auth.application.port.in.RegisterUserUseCase;
import com.menta.auth.infrastructure.web.dto.RegisterUserRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            new RegisterUserCommand(request.email(), request.password(), request.role(), clientFingerprint)
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> handleSemanticRegistrationRejection(IllegalArgumentException exception) {
        return ResponseEntity.accepted().build();
    }
}
