package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.LessonAccessDecision;
import com.menta.virtual.application.dto.LessonAccessDecisionDto;
import com.menta.virtual.application.dto.PublicLessonStreamResult;
import com.menta.virtual.application.dto.PublicLessonStreamView;
import com.menta.virtual.application.dto.PublicStreamQuality;
import com.menta.virtual.application.port.in.GetPublicLessonStreamUseCase;
import com.menta.virtual.application.port.out.BunnyNetSignatureService;
import com.menta.virtual.application.port.out.Clock;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.domain.model.VirtualModule;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link GetPublicLessonStreamUseCase} (US-VIRTUAL-004).
 * Resolves a lesson, asks the cross-module billing port whether the
 * caller can play it, and produces a signed stream URL with a hard-coded
 * 4-hour TTL.
 *
 * <h2>Outcome map</h2>
 * <ul>
 *   <li>missing / malformed / unpublished-parent id → throws
 *       {@link LessonNotFoundException} (anti-enumeration combines all
 *       three on the public surface, see
 *       {@link com.menta.virtual.application.usecase.GetPublicLessonUseCaseImpl}).</li>
 *   <li>Authorized for any caller (free OR premium-with-entitlement) →
 *       {@link PublicLessonStreamResult.Authorized}.</li>
 *   <li>Caller without an active entitlement AND the lesson is not
 *       playable for free → {@link PublicLessonStreamResult.AccessDenied}.</li>
 * </ul>
 *
 * <h2>Free lesson policy</h2>
 * The MVP defines "free" as "playable for any caller". An anonymous
 * visitor is allowed a stream for a free lesson because they have no
 * entitlement to deny. A premium lesson denied to an anonymous visitor
 * falls into the {@code AccessDenied} branch with the same message a
 * paying-but-expired customer would see — the orchestrator budgeted the
 * distinction (issue #50 escenario 2 requires a dedicated
 * {@code "SUBSCRIPTION_EXPIRED"} string) to a follow-up that adds a
 * richer entitlement-detail port to billing.
 *
 * <h2>Streaming details</h2>
 * <ul>
 *   <li>TTL = now + 4 hours. The orchestrator chose 4h so a viewer can
 *       reasonably pause and resume within the same session; future
 *       issues can shorten / tighten without touching this use case.</li>
 *   <li>Quality ladder is a fixed list
 *       ({@code 1080p/720p/480p/360p}); a real Bunny.net manifest
 *       reader can replace {@link #QUALITY_LADDER} without changing
 *       the record or the controller.</li>
 *   <li>{@code type} is hard-coded {@code "HLS"} on the orchestrator's
 *       instruction; no format branch in this MVP.</li>
 * </ul>
 *
 * <h2>Clock</h2>
 * Injected so tests can pin "now" without system-time flakiness. The
 * bean binding is in
 * {@link com.menta.virtual.infrastructure.config.VirtualConfiguration}.
 */
@Component
public class GetPublicLessonStreamUseCaseImpl implements GetPublicLessonStreamUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(GetPublicLessonStreamUseCaseImpl.class);

    /** 4 hours from issue time. Configurable later if a UX review asks. */
    static final Duration SIGNED_URL_TTL = Duration.ofHours(4);

    /** Wire-format hint: HLS is the only stream type the MVP speaks. */
    static final String STREAM_TYPE = "HLS";

    /**
     * Static adaptive ladder, by orchestrator's decision (#50 “fuera de
     * scope”: qualities adaptativas reales derivadas del manifest).
     */
    static final List<PublicStreamQuality> QUALITY_LADDER = List.of(
        PublicStreamQuality.of("1080p", 5_000_000L),
        PublicStreamQuality.of("720p",  2_500_000L),
        PublicStreamQuality.of("480p",  1_000_000L),
        PublicStreamQuality.of("360p",    500_000L)
    );

    /** Hard-coded reference to the catalog's billing plans endpoint. */
    static final String BILLING_PLANS_URL = "/api/v1/billing/plans";

    private final VirtualLessonRepository lessonRepository;
    private final VirtualModuleRepository moduleRepository;
    private final LessonAccessPolicy accessPolicy;
    private final BunnyNetSignatureService signatureService;
    private final Clock clock;

    public GetPublicLessonStreamUseCaseImpl(
        VirtualLessonRepository lessonRepository,
        VirtualModuleRepository moduleRepository,
        LessonAccessPolicy accessPolicy,
        BunnyNetSignatureService signatureService,
        Clock clock
    ) {
        this.lessonRepository = lessonRepository;
        this.moduleRepository = moduleRepository;
        this.accessPolicy = accessPolicy;
        this.signatureService = signatureService;
        this.clock = clock;
    }

    @Override
    public PublicLessonStreamResult get(String lessonId, UUID actingUserId) {
        LessonId parsed = parseLessonIdOrNull(lessonId);
        if (parsed == null) {
            throw new LessonNotFoundException();
        }
        VirtualLesson lesson = lessonRepository.findById(parsed)
            .orElseThrow(LessonNotFoundException::new);
        VirtualModule module = moduleRepository.findById(lesson.getModuleId())
            .orElseThrow(LessonNotFoundException::new);

        if (accessPolicy.decide(lesson, module, actingUserId) == LessonAccessDecision.SUBSCRIPTION_REQUIRED) {
            LOG.debug(
                "stream denied: lessonId={} courseId={} actingUserId={} isFree={}",
                parsed, lesson.getCourseId(), actingUserId, lesson.isFree()
            );
            return new PublicLessonStreamResult.AccessDenied(
                LessonAccessDecisionDto.requiresSubscription(BILLING_PLANS_URL)
            );
        }

        Instant expiresAt = clock.now().plus(SIGNED_URL_TTL);
        String signedUrl = signatureService.generateSignedUrl(
            lesson.getVideoId(), expiresAt.getEpochSecond()
        );
        return new PublicLessonStreamResult.Authorized(
            new PublicLessonStreamView(
                signedUrl,
                STREAM_TYPE,
                QUALITY_LADDER,
                expiresAt,
                lesson.getId().toString(),
                lesson.getTitle(),
                formatDuration(lesson.getDurationMinutes())
            )
        );
    }

    /**
     * Mirror of
     * {@link GetPublicLessonUseCaseImpl#parseLessonId(String)}: a
     * malformed id is reported as {@code null} so the public surface
     * collapses it to {@link LessonNotFoundException} → 404
     * (anti-enumeration).
     */
    private static LessonId parseLessonIdOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LessonId.of(raw);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /**
     * Render {@code durationMinutes} as {@code mm:ss}. Manually
     * duplicated from
     * {@link GetPublicLessonUseCaseImpl#formatDuration(int)} so this
     * class does not break encapsulation on that private formatter —
     * exchanging them for a shared util component is tracked outside
     * issue #50 (US-VIRTUAL-004).
     */
    private static String formatDuration(int durationMinutes) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", durationMinutes, 0);
    }
}
