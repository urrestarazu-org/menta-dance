package com.menta.auth.infrastructure.web.controller;

import com.menta.auth.application.port.in.GetCurrentUserUseCase;
import com.menta.auth.infrastructure.web.dto.CurrentUserResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for the authenticated user's own account (#749).
 *
 * <p>It lives apart from {@link UserController}, whose public registration and activation endpoints are mapped
 * by {@link ActivationExceptionHandler}; {@code SecurityConfig} requires authentication for this path, whatever
 * the role.
 */
@RestController
@RequestMapping("/api/v1/users")
@CurrentUserEndpoint
@RequiredArgsConstructor
public class CurrentUserController {

    private final GetCurrentUserUseCase getCurrentUserUseCase;

    /** Returns the id, names and role of the owner of the access token; never someone else's data. */
    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(CurrentUserResponse.from(getCurrentUserUseCase.get(userId)));
    }
}
