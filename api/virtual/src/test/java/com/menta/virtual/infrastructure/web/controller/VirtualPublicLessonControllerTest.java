package com.menta.virtual.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.PublicCourseRef;
import com.menta.virtual.application.dto.PublicLessonDetailView;
import com.menta.virtual.application.dto.PublicLessonFreeView;
import com.menta.virtual.application.dto.PublicLessonNavigation;
import com.menta.virtual.application.dto.PublicLessonNavigationRef;
import com.menta.virtual.application.dto.PublicLessonPremiumAccessibleView;
import com.menta.virtual.application.dto.PublicLessonSubscriptionPrompt;
import com.menta.virtual.application.dto.PublicModuleRef;
import com.menta.virtual.application.port.in.GetPublicLessonStreamUseCase;
import com.menta.virtual.application.port.in.GetPublicLessonUseCase;
import com.menta.virtual.domain.exception.ForbiddenLessonAccessException;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.infrastructure.web.dto.PublicLessonFreeResponse;
import com.menta.virtual.infrastructure.web.dto.PublicLessonPremiumAccessibleResponse;
import com.menta.virtual.infrastructure.web.dto.PublicLessonRequiresSubscriptionResponse;
import com.menta.virtual.infrastructure.web.dto.PublicLessonResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Coverage for {@link VirtualPublicLessonController}. Distinct from the
 * existing admin controller tests because the public read has a different
 * authorisation surface (anonymous callers are first-class), different
 * access decisions (the three branches collapse to two HTTP statuses —
 * 200 OK or 403 ProblemDetail from the same handler), and a different
 * exception hierarchy.
 */
class VirtualPublicLessonControllerTest {

    private final GetPublicLessonUseCase useCase = mock(GetPublicLessonUseCase.class);
    private final GetPublicLessonStreamUseCase streamUseCase = mock(GetPublicLessonStreamUseCase.class);
    private final VirtualPublicLessonController controller = new VirtualPublicLessonController(
        useCase, streamUseCase
    );

    private static Authentication anonymous() {
        // Render the SecurityContext the way JwtAuthenticationFilter leaves it for an
        // unauthenticated request: an AnonymousAuthenticationToken-shaped principal
        // whose getName() returns "anonymousUser".
        return new UsernamePasswordAuthenticationToken(
            "anonymousUser", null, List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
    }

    private static Authentication authOf(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
            userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    private static PublicLessonFreeView freeView(String lessonId, CourseId courseId, ModuleId moduleId) {
        PublicLessonDetailView detail = PublicLessonDetailView.withoutVideoId(
            lessonId, "Caminada", "Aprendé a caminar la pista",
            "10:00", true, 2,
            PublicModuleRef.of(moduleId, "Postura"),
            PublicCourseRef.of(courseId, "Tango Básico"),
            "https://cdn/tango.jpg"
        );
        return new PublicLessonFreeView(
            detail,
            new PublicLessonNavigation(
                new PublicLessonNavigationRef(UUID.randomUUID().toString(), "Intro", true),
                new PublicLessonNavigationRef(UUID.randomUUID().toString(), "Salida", false)
            ),
            new PublicLessonSubscriptionPrompt("¡Suscríbete para acceder a todas las lecciones!", "/api/v1/billing/plans")
        );
    }

    @Test
    void free_lesson_for_anonymous_caller_returns_200_free_response() {
        String lessonId = UUID.randomUUID().toString();
        CourseId courseId = CourseId.generate();
        ModuleId moduleId = ModuleId.generate();

        when(useCase.get(eq(lessonId), any())).thenReturn(Optional.of(freeView(lessonId, courseId, moduleId)));

        ResponseEntity<PublicLessonResponse> response = controller.get(lessonId, anonymous());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(PublicLessonFreeResponse.class);
        PublicLessonFreeResponse body = (PublicLessonFreeResponse) response.getBody();
        assertThat(body.lesson().lessonId()).isEqualTo(lessonId);
        assertThat(body.lesson().isFree()).isTrue();
        assertThat(body.lesson().videoId()).isNull();
        assertThat(body.access().preview()).isTrue();
        assertThat(body.access().requiresSubscription()).isFalse();
        assertThat(body.subscription().plansUrl()).isEqualTo("/api/v1/billing/plans");
    }

    @Test
    void free_lesson_for_authenticated_caller_returns_200_free_response() {
        String lessonId = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();
        CourseId courseId = CourseId.generate();
        ModuleId moduleId = ModuleId.generate();

        when(useCase.get(eq(lessonId), eq(userId))).thenReturn(Optional.of(freeView(lessonId, courseId, moduleId)));

        ResponseEntity<PublicLessonResponse> response = controller.get(lessonId, authOf(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(PublicLessonFreeResponse.class);
    }

    @Test
    void premium_lesson_for_anonymous_caller_lets_the_use_case_throw_403() {
        // The use case throws ForbiddenLessonAccessException for the anonymous
        // premium caller — controller must not catch and translate. Letting the
        // exception bubble out lets VirtualPublicLessonExceptionHandler map it
        // to a 403 ProblemDetail.
        String lessonId = UUID.randomUUID().toString();
        when(useCase.get(eq(lessonId), any())).thenThrow(new ForbiddenLessonAccessException());

        assertThatThrownBy(() -> controller.get(lessonId, anonymous()))
            .isInstanceOf(ForbiddenLessonAccessException.class);
    }

    @Test
    void premium_lesson_for_authenticated_caller_without_entitlement_lets_the_use_case_throw_403() {
        String lessonId = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();

        when(useCase.get(eq(lessonId), eq(userId))).thenThrow(new ForbiddenLessonAccessException());

        assertThatThrownBy(() -> controller.get(lessonId, authOf(userId)))
            .isInstanceOf(ForbiddenLessonAccessException.class);
    }

    @Test
    void premium_lesson_with_entitlement_returns_200_premium_response_with_videoId() {
        String lessonId = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();
        CourseId courseId = CourseId.generate();
        ModuleId moduleId = ModuleId.generate();

        PublicLessonDetailView detail = PublicLessonDetailView.withVideoId(
            lessonId, "Avanzado", "Giros avanzados", "15:00", false, 2,
            PublicModuleRef.of(moduleId, "Postura"),
            PublicCourseRef.of(courseId, "Tango Básico"),
            "SECRET-VIDEO-ID-42",
            "https://cdn/tango.jpg"
        );
        PublicLessonPremiumAccessibleView premium = new PublicLessonPremiumAccessibleView(
            detail, new PublicLessonNavigation(null, null)
        );

        when(useCase.get(eq(lessonId), eq(userId))).thenReturn(Optional.of(premium));

        ResponseEntity<PublicLessonResponse> response = controller.get(lessonId, authOf(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(PublicLessonPremiumAccessibleResponse.class);
        PublicLessonPremiumAccessibleResponse body = (PublicLessonPremiumAccessibleResponse) response.getBody();
        assertThat(body.lesson().videoId()).isEqualTo("SECRET-VIDEO-ID-42");
        assertThat(body.lesson().isFree()).isFalse();
        assertThat(body.access().preview()).isFalse();
        assertThat(body.access().requiresSubscription()).isFalse();
    }

    @Test
    void missing_lesson_id_throws_LessonNotFoundException() {
        String lessonId = UUID.randomUUID().toString();
        when(useCase.get(eq(lessonId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get(lessonId, anonymous()))
            .isInstanceOf(LessonNotFoundException.class);
    }

    @Test
    void malformed_lesson_id_throws_LessonNotFoundException() {
        // Anti-enumeration path: the use case already collapses a malformed UUID
        // into Optional.empty() before the controller sees it; the controller
        // rethrows LessonNotFoundException → 404 ProblemDetail.
        when(useCase.get(eq("not-a-uuid"), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.get("not-a-uuid", anonymous()))
            .isInstanceOf(LessonNotFoundException.class);
    }

    @Test
    void PublicLessonResponse_sealed_parents_enforce_three_branches() {
        // Static structural guard: the wire-level sealed parent only permits the
        // three expected response shapes. If a future refactor adds a fourth
        // subtype here, the controller's translate method must explicitly cover
        // it — and it should not.
        List<Class<?>> permitted = List.of(PublicLessonResponse.class.getPermittedSubclasses());
        assertThat(permitted).hasSize(3);
        assertThat(permitted).contains(
            PublicLessonFreeResponse.class,
            PublicLessonPremiumAccessibleResponse.class,
            PublicLessonRequiresSubscriptionResponse.class
        );
    }
}
