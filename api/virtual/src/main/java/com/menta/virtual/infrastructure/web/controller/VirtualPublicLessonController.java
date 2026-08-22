package com.menta.virtual.infrastructure.web.controller;

import com.menta.virtual.application.dto.PublicLessonFreeView;
import com.menta.virtual.application.dto.PublicLessonPremiumAccessibleView;
import com.menta.virtual.application.dto.PublicLessonRequiresSubscriptionView;
import com.menta.virtual.application.dto.PublicLessonView;
import com.menta.virtual.application.port.in.GetPublicLessonUseCase;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.infrastructure.web.dto.PublicLessonFreeResponse;
import com.menta.virtual.infrastructure.web.dto.PublicLessonPremiumAccessibleResponse;
import com.menta.virtual.infrastructure.web.dto.PublicLessonRequiresSubscriptionResponse;
import com.menta.virtual.infrastructure.web.dto.PublicLessonResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read of a single virtual lesson (US-VIRTUAL-003). The route is
 * {@code GET /api/v1/virtual/lessons/{lessonId}} and lives outside the
 * {@code /api/v1/admin/virtual/lessons/**} prefix covered by
 * {@code SecurityConfig}'s ADMIN/INSTRUCTOR rule — the public matcher,
 * added in this PR, sits at the top of the permitAll list right next to
 * the catalog one.
 *
 * <p>An empty view is mapped to {@link LessonNotFoundException} → 404
 * {@code application/problem+json} via
 * {@link VirtualPublicLessonExceptionHandler}. A premium lesson requested
 * by an anonymous caller is mapped to
 * {@link com.menta.virtual.domain.exception.ForbiddenLessonAccessException}
 * → 403 {@code application/problem+json} by the same handler chain.</p>
 *
 * <p>The three positive branches are dispatched as follows:</p>
 * <ul>
 *   <li>{@link PublicLessonFreeResponse} → 200 OK; the visitor sees the
 *       lesson body without a {@code videoId};</li>
 *   <li>{@link PublicLessonPremiumAccessibleResponse} → 200 OK; the
 *       visitor sees the lesson body WITH {@code videoId};</li>
 *   <li>{@link PublicLessonRequiresSubscriptionResponse} → 200 OK;
 *       the visitor (now identified) sees the preview body, with
 *       {@code access.allowed=false} and a {@code plansUrl}.</li>
 * </ul>
 *
 * <p>The orchestrator's decision (#48): anonymous premium → 403
 * ProblemDetail, identified-but-no-entitlement → 200 with the explicit
 * {@code access.allowed=false} flag. Implemented below.</p>
 */
@RestController
@RequestMapping("/api/v1/virtual/lessons")
@PublicVirtualEndpoint
public class VirtualPublicLessonController {

    private final GetPublicLessonUseCase getPublicLessonUseCase;

    public VirtualPublicLessonController(GetPublicLessonUseCase getPublicLessonUseCase) {
        this.getPublicLessonUseCase = getPublicLessonUseCase;
    }

    @GetMapping("/{lessonId}")
    public ResponseEntity<PublicLessonResponse> get(
        @PathVariable String lessonId,
        Authentication authentication
    ) {
        UUID actingUserId = actingUserIdOrNull(authentication);
        Optional<PublicLessonView> view = getPublicLessonUseCase.get(lessonId, actingUserId);
        // The use case already collapses "missing" and "malformed" to empty; the public
        // exception handler turns the resulting LessonNotFoundException into the same
        // 404 response a well-formed-but-missing id would produce (anti-enumeration).
        PublicLessonView resolved = view.orElseThrow(LessonNotFoundException::new);

        PublicLessonResponse body = translate(resolved);
        return ResponseEntity.ok(body);
    }

    /**
     * Map the application-layer sealed parent onto the matching
     * wire-layer sealed parent. Sealed hierarchy split on purpose so the
     * controller never accidentally surfaces an application DTO on the
     * JSON response (which would leak the {@code videoId} nullability).
     */
    private static PublicLessonResponse translate(PublicLessonView resolved) {
        if (resolved instanceof PublicLessonFreeView free) {
            return PublicLessonFreeResponse.from(free);
        }
        if (resolved instanceof PublicLessonPremiumAccessibleView premium) {
            return PublicLessonPremiumAccessibleResponse.from(premium);
        }
        if (resolved instanceof PublicLessonRequiresSubscriptionView gated) {
            return PublicLessonRequiresSubscriptionResponse.from(gated);
        }
        // Sealed at compile time — the instanceof chain covers every permitted subtype.
        throw new IllegalStateException("unhandled view type: " + resolved.getClass());
    }

    private static UUID actingUserIdOrNull(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return null;
        }
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException malformedPrincipal) {
            // Unknown identity token shape — fall back to anonymous treatment.
            return null;
        }
    }
}
