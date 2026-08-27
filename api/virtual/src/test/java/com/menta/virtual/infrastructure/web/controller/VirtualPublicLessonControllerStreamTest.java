package com.menta.virtual.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.PublicLessonStreamResult;
import com.menta.virtual.application.dto.PublicLessonStreamView;
import com.menta.virtual.application.dto.PublicStreamQuality;
import com.menta.virtual.application.port.in.GetPublicLessonStreamUseCase;
import com.menta.virtual.application.port.in.GetPublicLessonUseCase;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.infrastructure.web.dto.PublicLessonStreamResponse;
import com.menta.virtual.domain.exception.ForbiddenLessonAccessException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Coverage for {@link VirtualPublicLessonController#getStream}. Distinct
 * from {@code VirtualPublicLessonControllerTest} because the stream
 * endpoint has its own contract:
 * <ul>
 *   <li>{@link PublicLessonStreamResult.Authorized} → {@code 200 OK} with
 *       {@link PublicLessonStreamResponse};</li>
 *   <li>{@link PublicLessonStreamResult.AccessDenied} →
 *       {@link ForbiddenLessonAccessException}, which the shared public
 *       advice maps to a 403 RFC 9457 problem;</li>
 *   <li>missing or malformed id → {@link LessonNotFoundException}
 *       propagated up to the public advice chain
 *       ({@link VirtualPublicLessonExceptionHandler}) for a 404
 *       ProblemDetail.</li>
 * </ul>
 */
class VirtualPublicLessonControllerStreamTest {

    private final GetPublicLessonUseCase getPublicLessonUseCase = mock(GetPublicLessonUseCase.class);
    private final GetPublicLessonStreamUseCase streamUseCase = mock(GetPublicLessonStreamUseCase.class);
    private final VirtualPublicLessonController controller = new VirtualPublicLessonController(
        getPublicLessonUseCase, streamUseCase
    );

    private static Authentication anonymous() {
        return new UsernamePasswordAuthenticationToken(
            "anonymousUser", null,
            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
    }

    private static Authentication authOf(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
            userId.toString(), null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    private static PublicLessonStreamView authorizedView(String lessonId, String videoId) {
        return new PublicLessonStreamView(
            "https://vz.b-cdn.net/12345/" + videoId,
            "HLS",
            List.of(
                new PublicStreamQuality("1080p", 5_000_000L),
                new PublicStreamQuality("720p",  2_500_000L),
                new PublicStreamQuality("480p",  1_000_000L),
                new PublicStreamQuality("360p",    500_000L)
            ),
            Instant.parse("2026-08-22T22:00:00Z"),
            lessonId,
            "Caminata",
            "15:00"
        );
    }

    @Test
    void stream_for_anonymous_caller_on_a_free_lesson_returns_200_with_signed_url() {
        String lessonId = UUID.randomUUID().toString();
        PublicLessonStreamView view = authorizedView(lessonId, "vid-target");

        // MVP policy: free lessons → anyone can stream. Anonymous is filtered
        // by the controller's actingUserIdOrNull to null, which the use case
        // treats as "no entitlement → let the free-branch play".
        when(streamUseCase.get(eq(lessonId), any())).thenReturn(
            new PublicLessonStreamResult.Authorized(view)
        );

        ResponseEntity<Object> response = controller.getStream(lessonId, anonymous());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(PublicLessonStreamResponse.class);
        PublicLessonStreamResponse body = (PublicLessonStreamResponse) response.getBody();
        assertNotNull(body.stream());
        assertThat(body.stream().url()).isEqualTo("https://vz.b-cdn.net/12345/vid-target");
        assertThat(body.stream().type()).isEqualTo("HLS");
        assertThat(body.stream().qualities()).hasSize(4);
        assertThat(body.stream().expiresAt()).isEqualTo(Instant.parse("2026-08-22T22:00:00Z"));
        assertThat(body.lesson().lessonId()).isEqualTo(lessonId);
        assertThat(body.lesson().title()).isEqualTo("Caminata");
        assertThat(body.lesson().duration()).isEqualTo("15:00");
    }

    @Test
    void stream_for_authenticated_entitled_caller_returns_200_with_signed_url() {
        String lessonId = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();
        PublicLessonStreamView view = authorizedView(lessonId, "vid-premium");

        when(streamUseCase.get(eq(lessonId), eq(userId))).thenReturn(
            new PublicLessonStreamResult.Authorized(view)
        );

        ResponseEntity<Object> response = controller.getStream(lessonId, authOf(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(PublicLessonStreamResponse.class);
    }

    @Test
    void stream_for_authenticated_caller_without_entitlement_uses_the_shared_403_problem_path() {
        String lessonId = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();

        when(streamUseCase.get(eq(lessonId), eq(userId))).thenReturn(
            new PublicLessonStreamResult.AccessDenied(null)
        );

        assertThatThrownBy(() -> controller.getStream(lessonId, authOf(userId)))
            .isInstanceOf(ForbiddenLessonAccessException.class);
    }

    @Test
    void stream_for_anonymous_caller_on_a_premium_lesson_uses_the_shared_403_problem_path() {
        String lessonId = UUID.randomUUID().toString();

        when(streamUseCase.get(eq(lessonId), any())).thenReturn(
            new PublicLessonStreamResult.AccessDenied(null)
        );

        assertThatThrownBy(() -> controller.getStream(lessonId, anonymous()))
            .isInstanceOf(ForbiddenLessonAccessException.class);
    }

    @Test
    void stream_for_missing_lesson_id_propagates_LessonNotFoundException_for_handler_chain() {
        String lessonId = UUID.randomUUID().toString();
        when(streamUseCase.get(eq(lessonId), any()))
            .thenThrow(new LessonNotFoundException());

        // The handler chain turns this into a 404 ProblemDetail — the controller
        // does not catch it. Verifying the throw keeps the anti-enumeration
        // discipline observable at the controller layer.
        assertThatThrownBy(() -> controller.getStream(lessonId, anonymous()))
            .isInstanceOf(LessonNotFoundException.class);
    }

    @Test
    void stream_for_malformed_lesson_id_propagates_LessonNotFoundException_for_handler_chain() {
        when(streamUseCase.get(eq("not-a-uuid"), any()))
            .thenThrow(new LessonNotFoundException());

        assertThatThrownBy(() -> controller.getStream("not-a-uuid", anonymous()))
            .isInstanceOf(LessonNotFoundException.class);
    }
}
