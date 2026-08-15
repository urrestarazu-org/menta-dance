package com.menta.auth.infrastructure.web.controller;

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
        // TODO(task 3.1): rewire this route against the real register flow
        // once PR2's adapters land. Until then it MUST NOT reach
        // registerUserUseCase, which is still backed by placeholder
        // infrastructure adapters (see AuthConfiguration) that throw
        // UnsupportedOperationException.
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}
