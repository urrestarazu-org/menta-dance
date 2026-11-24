package com.menta.virtual.infrastructure.web.controller;

import com.menta.virtual.application.dto.LibraryVideoOrder;
import com.menta.virtual.application.dto.VideoLibraryQuery;
import com.menta.virtual.application.port.in.ListLibraryVideosUseCase;
import com.menta.virtual.infrastructure.web.dto.VirtualVideoPageResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for the video library listing (ADR-0049, #840). {@code SecurityConfig} grants ADMIN and INSTRUCTOR
 * access to this prefix; which videos each one sees is decided by the use case (an instructor only sees the videos
 * used by lessons of their own courses). The library key never appears in a request or a response.
 */
@RestController
@RequestMapping("/api/v1/admin/virtual/videos")
@VirtualManagementEndpoint
public class VirtualVideoAdminController {

    private final ListLibraryVideosUseCase listLibraryVideosUseCase;

    public VirtualVideoAdminController(ListLibraryVideosUseCase listLibraryVideosUseCase) {
        this.listLibraryVideosUseCase = listLibraryVideosUseCase;
    }

    /**
     * Lists the videos, {@code page} zero-based. {@code size} is between 10 and 100, never clamped; {@code orderBy}
     * is {@code date} (newest first, the default) or {@code title}; a blank {@code search} means no filter.
     */
    @GetMapping
    public ResponseEntity<VirtualVideoPageResponse> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "" + ListLibraryVideosUseCase.DEFAULT_PAGE_SIZE) int size,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String orderBy,
        Authentication authentication
    ) {
        VideoLibraryQuery query = new VideoLibraryQuery(page, size, search, LibraryVideoOrder.parse(orderBy));
        return ResponseEntity.ok(VirtualVideoPageResponse.from(listLibraryVideosUseCase.list(
            query, UUID.fromString(authentication.getName()), isAdmin(authentication)
        )));
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals);
    }
}
