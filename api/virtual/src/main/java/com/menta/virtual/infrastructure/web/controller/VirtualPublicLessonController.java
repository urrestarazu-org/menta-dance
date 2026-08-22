package com.menta.virtual.infrastructure.web.controller;

import com.menta.virtual.application.dto.PublicLessonFreeView;
import com.menta.virtual.application.dto.PublicLessonPremiumAccessibleView;
import com.menta.virtual.application.dto.PublicLessonRequiresSubscriptionView;
import com.menta.virtual.application.dto.PublicLessonStreamResult;
import com.menta.virtual.application.dto.PublicLessonView;
import com.menta.virtual.application.port.in.GetPublicLessonStreamUseCase;
import com.menta.virtual.application.port.in.GetPublicLessonUseCase;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.infrastructure.web.dto.PublicLessonAccessDto;
import com.menta.virtual.infrastructure.web.dto.PublicLessonFreeResponse;
import com.menta.virtual.infrastructure.web.dto.PublicLessonPremiumAccessibleResponse;
import com.menta.virtual.infrastructure.web.dto.PublicLessonRequiresSubscriptionResponse;
import com.menta.virtual.infrastructure.web.dto.PublicLessonResponse;
import com.menta.virtual.infrastructure.web.dto.PublicLessonStreamResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read of a single virtual lesson (US-VIRTUAL-003) and its
 * signed streaming URL (US-VIRTUAL-004). Routes live under
 * {@code /api/v1/virtual/lessons/**}, which {@code SecurityConfig}
 * permits for anonymous and authenticated callers alike.
 *
 * <p>The detail route {@code GET /api/v1/virtual/lessons/{lessonId}}
 * maps to {@link LessonNotFoundException} → 404
 * {@code application/problem+json} via
 * {@link VirtualPublicLessonExceptionHandler}, with three positive
 * 200 branches per #48:</p>
 * <ul>
 *   <li>{@link PublicLessonFreeResponse} → free preview, no {@code videoId};</li>
 *   <li>{@link PublicLessonPremiumAccessibleResponse} → premium body
 *       WITH {@code videoId};</li>
 *   <li>{@link PublicLessonRequiresSubscriptionResponse} → preview with
 *       {@code access.allowed=false}.</li>
 * </ul>
 *
 * <p>The streaming route {@code GET /api/v1/virtual/lessons/{lessonId}/stream}
 * (US-VIRTUAL-004) is a different shape:</p>
 * <ul>
 *   <li>{@link PublicLessonStreamResult.Authorized} → 200 OK with
 *       {@link PublicLessonStreamResponse} ({@code {stream, lesson}});</li>
 *   <li>{@link PublicLessonStreamResult.AccessDenied} → 403 Forbidden
 *       with the same {@link PublicLessonAccessDto} body the public
 *       lesson detail already exposes for its access-decision 200.</li>
 * </ul>
 *
 * <p>The 403 wire shape intentionally mirrors the
 * {@code PublicLessonRequiresSubscriptionResponse.access} block: the
 * orchestrator's document (#50 / #48) budgets a richer
 * {@code SUBSCRIPTION_EXPIRED} discriminator to a follow-up that
 * extends the cross-module entitlement port. Until then,
 * anonymous and expired callers receive the same
 * {@code SUBSCRIPTION_REQUIRED} message.</p>
 */
@RestController
@RequestMapping("/api/v1/virtual/lessons")
@PublicVirtualEndpoint
public class VirtualPublicLessonController {

    private final GetPublicLessonUseCase getPublicLessonUseCase;
    private final GetPublicLessonStreamUseCase getPublicLessonStreamUseCase;

    public VirtualPublicLessonController(
        GetPublicLessonUseCase getPublicLessonUseCase,
        GetPublicLessonStreamUseCase getPublicLessonStreamUseCase
    ) {
        this.getPublicLessonUseCase = getPublicLessonUseCase;
        this.getPublicLessonStreamUseCase = getPublicLessonStreamUseCase;
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
     * US-VIRTUAL-004: signed streaming URL for the requested lesson.
     *
     * <p>The use case throws {@link LessonNotFoundException} for any
     * unresolvable id (malformed, missing row, parent course
     * unpublished) — handled by the public advice chain as a 404
     * ProblemDetail, mirroring the detail route. The 403 branch is
     * returned inline (sealed result → {@code 403} with the
     * {@link PublicLessonAccessDto} body) rather than via a thrown
     * {@link com.menta.virtual.domain.exception.ForbiddenLessonAccessException},
     * because the wire shape required by the spec
     * ({@code {access: {...}}}) is not a Spring RFC 9457 ProblemDetail.
     * Adding yet another exception type to keep the handler chain would
     * not buy anything the sealed result already gives us.</p>
     */
    @GetMapping("/{lessonId}/stream")
    public ResponseEntity<Object> getStream(
        @PathVariable String lessonId,
        Authentication authentication
    ) {
        UUID actingUserId = actingUserIdOrNull(authentication);
        PublicLessonStreamResult result = getPublicLessonStreamUseCase.get(lessonId, actingUserId);

        if (result instanceof PublicLessonStreamResult.Authorized authorized) {
            return ResponseEntity.ok(PublicLessonStreamResponse.from(authorized.view()));
        }
        if (result instanceof PublicLessonStreamResult.AccessDenied denied) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(PublicLessonAccessDto.from(denied.access()));
        }
        // Sealed at compile time — the instanceof chain covers every permitted subtype.
        throw new IllegalStateException("unhandled stream result type: " + result.getClass());
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
