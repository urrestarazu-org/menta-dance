package com.menta.virtual.infrastructure.web.controller;

import com.menta.virtual.application.dto.LibraryVideoOrder;
import com.menta.virtual.application.dto.VideoLibraryQuery;
import com.menta.virtual.application.port.in.DeleteLibraryVideoUseCase;
import com.menta.virtual.application.port.in.ListLibraryVideosUseCase;
import com.menta.virtual.application.port.in.RenameLibraryVideoUseCase;
import com.menta.virtual.infrastructure.web.dto.RenameVirtualVideoRequest;
import com.menta.virtual.infrastructure.web.dto.VirtualVideoPageResponse;
import com.menta.virtual.infrastructure.web.dto.VirtualVideoResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for the video library management (ADR-0049): the listing (#840) and the rename and delete (#841).
 * {@code SecurityConfig} grants the listing and the rename to ADMIN and INSTRUCTOR and the delete to ADMIN only;
 * which videos each one sees and may rename is decided by the use cases (an instructor only has the videos used by
 * lessons of their own courses). The library key never appears in a request or a response.
 */
@RestController
@RequestMapping("/api/v1/admin/virtual/videos")
@VirtualManagementEndpoint
@RequiredArgsConstructor
public class VirtualVideoAdminController {

    private final ListLibraryVideosUseCase listLibraryVideosUseCase;
    private final RenameLibraryVideoUseCase renameLibraryVideoUseCase;
    private final DeleteLibraryVideoUseCase deleteLibraryVideoUseCase;

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

    /** Renames a video: an admin any, an instructor only a video used by a lesson of their own courses. */
    @PutMapping("/{videoId}")
    public ResponseEntity<VirtualVideoResponse> rename(
        @PathVariable String videoId, @Valid @RequestBody RenameVirtualVideoRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(VirtualVideoResponse.from(renameLibraryVideoUseCase.rename(
            videoId, request.title(), UUID.fromString(authentication.getName()), isAdmin(authentication)
        )));
    }

    /** Deletes a video no lesson uses (admin only); {@code 409 VIDEO_IN_USE} lists the lessons that use it. */
    @DeleteMapping("/{videoId}")
    public ResponseEntity<Void> delete(@PathVariable String videoId, Authentication authentication) {
        deleteLibraryVideoUseCase.delete(
            videoId, UUID.fromString(authentication.getName()), isAdmin(authentication)
        );
        return ResponseEntity.noContent().build();
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals);
    }
}
