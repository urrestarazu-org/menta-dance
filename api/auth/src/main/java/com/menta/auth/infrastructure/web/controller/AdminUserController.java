package com.menta.auth.infrastructure.web.controller;

import com.menta.auth.application.dto.ChangeUserRoleCommand;
import com.menta.auth.application.dto.ListUsersQuery;
import com.menta.auth.application.port.in.ChangeUserRoleUseCase;
import com.menta.auth.application.port.in.ListUsersUseCase;
import com.menta.auth.domain.model.Role;
import com.menta.auth.infrastructure.web.dto.ChangeUserRoleRequest;
import com.menta.auth.infrastructure.web.dto.UserPageResponse;
import com.menta.auth.infrastructure.web.dto.UserSummaryResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for the administration of users (#378): paged listing and role change.
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
