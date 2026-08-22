package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.shared.billing.VirtualCourseEntitlementPort;
import com.menta.virtual.application.dto.LessonAccessDecisionDto;
import com.menta.virtual.application.dto.PublicStreamQuality;
import com.menta.virtual.application.dto.PublicLessonStreamResult;
import com.menta.virtual.application.dto.PublicLessonStreamView;
import com.menta.virtual.application.port.out.BunnyNetSignatureService;
import com.menta.virtual.application.port.out.Clock;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.infrastructure.cdn.BunnyNetProperties;
import com.menta.virtual.infrastructure.cdn.StringFormatBunnyNetSignatureService;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Coverage for {@link GetPublicLessonStreamUseCaseImpl}. Pins {@code now}
 * with a fixed Clock so the {@code expiresAt = now + 4h} assertion never
 * leaks system time.
 *
 * <p>Mirrors the discipline of the existing {@link GetPublicLessonUseCaseImplTest}:
 * "do not consult dependencies you would have skipped" is verified with
 * {@code verify(..., never())} on the cross-module billing port and the
 * {@link BunnyNetSignatureService}. The implementation is expected to
 * short-circuit on a missing lesson BEFORE either dependency is touched.</p>
 */
class GetPublicLessonStreamUseCaseImplTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-22T18:00:00Z");
    private static final String PULL_ZONE_HOSTNAME = "vz-test.b-cdn.net";
    private static final String VIDEO_LIBRARY_ID = "12345";

    private final VirtualLessonRepository lessonRepository = mock(VirtualLessonRepository.class);
    private final VirtualCourseEntitlementPort entitlementPort = mock(VirtualCourseEntitlementPort.class);
    private final BunnyNetSignatureService signatureService = mock(BunnyNetSignatureService.class);
    private final Clock fixedClock = new Clock() {
        @Override public Instant now() { return java.time.Clock.fixed(FIXED_NOW, ZoneOffset.UTC).instant(); }
    };
    private final GetPublicLessonStreamUseCaseImpl useCase = new GetPublicLessonStreamUseCaseImpl(
        lessonRepository, entitlementPort, signatureService, fixedClock
    );

    private static BunnyNetSignatureService configuredService() {
        BunnyNetProperties props = new BunnyNetProperties();
        props.setPullZoneHostname(PULL_ZONE_HOSTNAME);
        props.setVideoLibraryId(VIDEO_LIBRARY_ID);
        return new StringFormatBunnyNetSignatureService(props);
    }

    private static VirtualLesson lesson(
        LessonId id, CourseId courseId, String videoId, boolean free, int durationMinutes
    ) {
        return new VirtualLesson(
            id, com.menta.virtual.domain.model.ModuleId.generate(), courseId,
            "Caminata", "Aprendé a caminar", videoId, durationMinutes, free, 2
        );
    }

    @Test
    void premium_lesson_for_authenticated_entitled_caller_returns_authorized_view() {
        CourseId courseId = CourseId.generate();
        LessonId lessonId = LessonId.generate();
        UUID userId = UUID.randomUUID();

        when(lessonRepository.findById(lessonId)).thenReturn(
            java.util.Optional.of(lesson(lessonId, courseId, "vid-target", false, 15))
        );
        when(entitlementPort.hasActiveEntitlement(eq(userId), eq(courseId.getValue().toString())))
            .thenReturn(true);
        when(signatureService.generateSignedUrl(eq("vid-target"), anyLong()))
            .thenReturn(PULL_ZONE_HOSTNAME + "/" + VIDEO_LIBRARY_ID + "/" + "vid-target");

        PublicLessonStreamResult result = useCase.get(lessonId.toString(), userId);

        assertThat(result).isInstanceOf(PublicLessonStreamResult.Authorized.class);
        PublicLessonStreamView view = ((PublicLessonStreamResult.Authorized) result).view();

        // Stream URL must come from the signature service, byte-for-byte, with the TTL epoch-second forwarded.
        verify(signatureService).generateSignedUrl(eq("vid-target"), eq(FIXED_NOW.plus(Duration.ofHours(4)).getEpochSecond()));
        assertThat(view.streamUrl()).isEqualTo(PULL_ZONE_HOSTNAME + "/" + VIDEO_LIBRARY_ID + "/" + "vid-target");
        assertThat(view.type()).isEqualTo("HLS");
        assertThat(view.lessonId()).isEqualTo(lessonId.toString());
        assertThat(view.lessonTitle()).isEqualTo("Caminata");
        assertThat(view.lessonDurationFormatted()).isEqualTo("15:00");
        assertThat(view.expiresAt()).isCloseTo(FIXED_NOW.plus(Duration.ofHours(4)), within(100, java.time.temporal.ChronoUnit.MILLIS));
        assertThat(view.qualities())
            .extracting(PublicStreamQuality::label, PublicStreamQuality::bitrate)
            .containsExactly(
                org.assertj.core.api.Assertions.tuple("1080p", 5_000_000L),
                org.assertj.core.api.Assertions.tuple("720p",  2_500_000L),
                org.assertj.core.api.Assertions.tuple("480p",  1_000_000L),
                org.assertj.core.api.Assertions.tuple("360p",    500_000L)
            );
    }

    @Test
    void free_lesson_for_anonymous_caller_returns_authorized_view_without_entitlement_consult() {
        CourseId courseId = CourseId.generate();
        LessonId lessonId = LessonId.generate();

        when(lessonRepository.findById(lessonId)).thenReturn(
            java.util.Optional.of(lesson(lessonId, courseId, "vid-target", true, 10))
        );
        when(signatureService.generateSignedUrl(eq("vid-target"), anyLong()))
            .thenReturn(PULL_ZONE_HOSTNAME + "/" + VIDEO_LIBRARY_ID + "/" + "vid-target");

        PublicLessonStreamResult result = useCase.get(lessonId.toString(), null);

        assertThat(result).isInstanceOf(PublicLessonStreamResult.Authorized.class);
        // Critical invariant: a free lesson never consults the billing port — same discipline as #48.
        verify(entitlementPort, never()).hasActiveEntitlement(any(), anyString());
    }

    @Test
    void premium_lesson_for_anonymous_caller_returns_access_denied_without_entitlement_consult() {
        CourseId courseId = CourseId.generate();
        LessonId lessonId = LessonId.generate();

        when(lessonRepository.findById(lessonId)).thenReturn(
            java.util.Optional.of(lesson(lessonId, courseId, "vid-target", false, 15))
        );

        PublicLessonStreamResult result = useCase.get(lessonId.toString(), null);

        assertThat(result).isInstanceOf(PublicLessonStreamResult.AccessDenied.class);
        LessonAccessDecisionDto access = ((PublicLessonStreamResult.AccessDenied) result).access();
        assertThat(access.allowed()).isFalse();
        assertThat(access.reason()).isEqualTo("SUBSCRIPTION_REQUIRED");
        assertThat(access.plansUrl()).isEqualTo("/api/v1/billing/plans");
        // Anonymous → no entitlement candidate → port must never be called.
        verify(entitlementPort, never()).hasActiveEntitlement(any(), anyString());
        // And no signature either: the use case rejected before signing.
        verify(signatureService, never()).generateSignedUrl(any(), anyLong());
    }

    @Test
    void premium_lesson_for_authenticated_caller_without_entitlement_returns_access_denied() {
        CourseId courseId = CourseId.generate();
        LessonId lessonId = LessonId.generate();
        UUID userId = UUID.randomUUID();

        when(lessonRepository.findById(lessonId)).thenReturn(
            java.util.Optional.of(lesson(lessonId, courseId, "vid-target", false, 15))
        );
        when(entitlementPort.hasActiveEntitlement(eq(userId), eq(courseId.getValue().toString())))
            .thenReturn(false);

        PublicLessonStreamResult result = useCase.get(lessonId.toString(), userId);

        assertThat(result).isInstanceOf(PublicLessonStreamResult.AccessDenied.class);
        LessonAccessDecisionDto access = ((PublicLessonStreamResult.AccessDenied) result).access();
        assertThat(access.allowed()).isFalse();
        assertThat(access.reason()).isEqualTo("SUBSCRIPTION_REQUIRED");
        verify(signatureService, never()).generateSignedUrl(any(), anyLong());
    }

    @Test
    void malformed_lesson_id_collapses_to_LessonNotFoundException() {
        assertThatThrownBy(() -> useCase.get("", UUID.randomUUID()))
            .isInstanceOf(LessonNotFoundException.class);
        assertThatThrownBy(() -> useCase.get("not-a-uuid", UUID.randomUUID()))
            .isInstanceOf(LessonNotFoundException.class);

        // Critical invariant: the use case must NOT touch any dependency on a bad id.
        verify(lessonRepository, never()).findById(any());
        verify(entitlementPort, never()).hasActiveEntitlement(any(), anyString());
        verify(signatureService, never()).generateSignedUrl(any(), anyLong());
    }

    @Test
    void missing_lesson_id_collapses_to_LessonNotFoundException_without_entitlement_or_signature() {
        LessonId missing = LessonId.generate();
        when(lessonRepository.findById(missing)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> useCase.get(missing.toString(), UUID.randomUUID()))
            .isInstanceOf(LessonNotFoundException.class);
        verify(entitlementPort, never()).hasActiveEntitlement(any(), anyString());
        verify(signatureService, never()).generateSignedUrl(any(), anyLong());
    }

    @Test
    void string_format_placeholder_service_builds_url_with_pull_zone_library_and_videoId() {
        // Sanity check: the placeholder service produces the documented
        // shape so a downstream test or future HMAC impl has a stable
        // contract to migrate against.
        BunnyNetSignatureService placeholder = configuredService();

        assertThat(placeholder.generateSignedUrl("abc-123", 1_700_000_000L))
            .isEqualTo(PULL_ZONE_HOSTNAME + "/" + VIDEO_LIBRARY_ID + "/" + "abc-123");
    }
}
