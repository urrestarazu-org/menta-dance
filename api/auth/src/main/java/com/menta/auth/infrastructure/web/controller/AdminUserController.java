package com.menta.auth.infrastructure.web.controller;

import com.menta.auth.application.dto.ListUsersQuery;
import com.menta.auth.application.port.in.ListUsersUseCase;
import com.menta.auth.domain.model.Role;
import com.menta.auth.infrastructure.web.dto.UserPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for the administration of users (#378): paged listing.
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
}
