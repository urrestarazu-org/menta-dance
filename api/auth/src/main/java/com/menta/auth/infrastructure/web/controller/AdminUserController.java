package com.menta.auth.infrastructure.web.controller;

import com.menta.auth.application.dto.ChangeUserRoleCommand;
import com.menta.auth.application.dto.CreateUserCommand;
import com.menta.auth.application.dto.ListUsersQuery;
import com.menta.auth.application.port.in.ChangeUserRoleUseCase;
import com.menta.auth.application.port.in.CreateUserUseCase;
import com.menta.auth.application.port.in.ListUsersUseCase;
import com.menta.auth.domain.model.Role;
import com.menta.auth.infrastructure.web.dto.ChangeUserRoleRequest;
import com.menta.auth.infrastructure.web.dto.CreateUserRequest;
import com.menta.auth.infrastructure.web.dto.UserPageResponse;
import com.menta.auth.infrastructure.web.dto.UserSummaryResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for the administration of users (#378): paged listing, account creation and role change.
 *
 * <p>{@code SecurityConfig}'s generic {@code /api/v1/admin/**} rule already restricts this path to
 * {@code ROLE_ADMIN}; no matcher is added here.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@AdminUserEndpoint
@RequiredArgsConstructor
public class AdminUserController {

    private final ListUsersUseCase listUsersUseCase;
    private final ChangeUserRoleUseCase changeUserRoleUseCase;
    private final CreateUserUseCase createUserUseCase;

    /**
     * Lists users ordered by email. {@code size} above the maximum is rejected with {@code 400}, never clamped;
     * a blank {@code q} means no text filter.
     */
    @GetMapping
    public ResponseEntity<UserPageResponse> list(
        @RequestParam(required = false) Role role,
        @RequestParam(name = "q", required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "" + ListUsersQuery.DEFAULT_PAGE_SIZE) int size
    ) {
        return ResponseEntity.ok(UserPageResponse.from(
            listUsersUseCase.list(new ListUsersQuery(role, search, page, size))
        ));
    }

    /**
     * Creates a staff account that its holder activates by email, choosing their own password. Answers
     * {@code 201} with the pending account; there is no {@code Location} because no endpoint reads a single user.
     */
    @PostMapping
    public ResponseEntity<UserSummaryResponse> create(
        @Valid @RequestBody CreateUserRequest request,
        Authentication authentication
    ) {
        UUID actorId = UUID.fromString(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserSummaryResponse.from(
            createUserUseCase.create(new CreateUserCommand(
                actorId, request.email(), request.firstName(), request.lastName(), request.role()
            ))
        ));
    }

    /** Changes the user's role and closes every session that carries the old one. */
    @PatchMapping("/{userId}/role")
    public ResponseEntity<UserSummaryResponse> changeRole(
        @PathVariable UUID userId,
        @Valid @RequestBody ChangeUserRoleRequest request,
        Authentication authentication
    ) {
        UUID actorId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(UserSummaryResponse.from(
            changeUserRoleUseCase.changeRole(new ChangeUserRoleCommand(actorId, userId, request.role()))
        ));
    }
}
