package com.menta.auth.infrastructure.web.controller;

import com.menta.auth.application.dto.RegisterUserCommand;
import com.menta.auth.application.dto.UserResult;
import com.menta.auth.application.port.in.RegisterUserUseCase;
import com.menta.auth.infrastructure.web.dto.RegisterUserRequest;
import com.menta.auth.infrastructure.web.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user operations.
 * Infrastructure layer - contains Spring annotations.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final RegisterUserUseCase registerUserUseCase;

    public UserController(RegisterUserUseCase registerUserUseCase) {
        this.registerUserUseCase = registerUserUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        RegisterUserCommand command = new RegisterUserCommand(
            request.email(),
            request.password(),
            request.role()
        );

        UserResult result = registerUserUseCase.register(command);

        UserResponse response = new UserResponse(
            result.id(),
            result.email(),
            result.role(),
            result.status()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
