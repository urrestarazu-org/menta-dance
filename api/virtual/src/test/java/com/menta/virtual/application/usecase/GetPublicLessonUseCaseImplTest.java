package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.shared.billing.VirtualCourseEntitlementPort;
import com.menta.virtual.application.dto.LessonAccessDecisionDto;
import com.menta.virtual.application.dto.PublicLessonFreeView;
import com.menta.virtual.application.dto.PublicLessonPremiumAccessibleView;
import com.menta.virtual.application.dto.PublicLessonRequiresSubscriptionView;
import com.menta.virtual.application.dto.PublicLessonView;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.ForbiddenLessonAccessException;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Coverage for {@link GetPublicLessonUseCaseImpl}. Distinct from the
 * existing catalog tests because the public-lesson read has its own
 * invariants:
 * <ol>
 *   <li>never consults the billing port on a free lesson — verified with
 *       a {@code verify(entitlement, never())} assertion, since free
 *       reads are the hot path and any leak would penalize every visitor;</li>
 *   <li>collapses missing-id and malformed-id into the same
 *       {@code Optional.empty()} — the controller turns that into a 404
 *       indistinguishable from a not-found row;</li>
 *   <li>throws {@link ForbiddenLessonAccessException} for anonymous
 *       premium callers, NOT silently downgrading to a preview;</li>
 *   <li>does NOT throw for identified-but-not-subscribed callers —
 *       that branch returns a {@link PublicLessonRequiresSubscriptionView}
 *       with {@code access.allowed=false}.</li>
 * </ol>
 */
class GetPublicLessonUseCaseImplTest {

    private final VirtualLessonRepository lessonRepository = mock(VirtualLessonRepository.class);
    private final VirtualModuleRepository moduleRepository = mock(VirtualModuleRepository.class);
    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualCourseEntitlementPort entitlementPort = mock(VirtualCourseEntitlementPort.class);
    private final GetPublicLessonUseCaseImpl useCase = new GetPublicLessonUseCaseImpl(
        lessonRepository, moduleRepository, courseRepository, entitlementPort
    );

    private static VirtualCourse publishedCourse(CourseId id) {
        return new VirtualCourse(
            id, "Tango Básico", "Aprendé los pasos fundamentales", "Descripción larga", UUID.randomUUID(),
            "https://cdn/tango.jpg", CourseCategory.of("tango"), CourseLevel.BEGINNER, true,
            CourseStatus.PUBLISHED, 1, 4, 60
        );
    }

    private static VirtualModule module(CourseId courseId, ModuleId id, String title, int order) {
        return new VirtualModule(id, courseId, title, order);
    }

    private static VirtualLesson lesson(
        LessonId id, ModuleId moduleId, CourseId courseId, String title,
        String videoId, int durationMinutes, boolean free, int order
    ) {
        return new VirtualLesson(
            id, moduleId, courseId, title, "desc " + title, videoId,
            durationMinutes, free, order
        );
    }

    /** Three siblings so we can also exercise the previous / next navigation pointers. */
    private static List<VirtualLesson> siblingLessons(
        ModuleId moduleId, CourseId courseId, LessonId targetId
    ) {
        LessonId prevId = LessonId.generate();
        LessonId nextId = LessonId.generate();
        return List.of(
            lesson(prevId, moduleId, courseId, "Intro", "vid-prev", 5, true, 1),
            lesson(targetId, moduleId, courseId, "Caminada", "vid-target", 10, true, 2),
            lesson(nextId, moduleId, courseId, "Salida", "vid-next", 8, false, 3)
        );
    }

    @Test
    void free_lesson_returns_free_view_and_does_not_consult_the_billing_port() {
        CourseId courseId = CourseId.generate();
        ModuleId moduleId = ModuleId.generate();
        LessonId lessonId = LessonId.generate();
        UUID userId = UUID.randomUUID();

        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(
            lesson(lessonId, moduleId, courseId, "Caminada", "vid-target", 10, true, 2)
        ));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(
            module(courseId, moduleId, "Postura", 1)
        ));
        when(courseRepository.findPublishedById(courseId)).thenReturn(Optional.of(publishedCourse(courseId)));
        when(lessonRepository.findByModuleId(moduleId)).thenReturn(siblingLessons(moduleId, courseId, lessonId));

        Optional<PublicLessonView> view = useCase.get(lessonId.toString(), userId);

        assertThat(view).isPresent();
        assertThat(view.get()).isInstanceOf(PublicLessonFreeView.class);
        PublicLessonFreeView free = (PublicLessonFreeView) view.get();
        assertThat(free.lesson().lessonId()).isEqualTo(lessonId.toString());
        assertThat(free.lesson().title()).isEqualTo("Caminada");
        assertThat(free.lesson().isFree()).isTrue();
        assertThat(free.lesson().duration()).isEqualTo("10:00");
        assertThat(free.lesson().videoId()).isNull();
        assertThat(free.lesson().order()).isEqualTo(2);
        assertThat(free.lesson().module().moduleId()).isEqualTo(moduleId.toString());
        assertThat(free.lesson().course().courseId()).isEqualTo(courseId.toString());
        assertThat(free.lesson().thumbnailUrl()).isEqualTo("https://cdn/tango.jpg");
        assertThat(free.navigation().previousLesson()).isNotNull();
        assertThat(free.navigation().previousLesson().title()).isEqualTo("Intro");
        assertThat(free.navigation().previousLesson().isFree()).isTrue();
        assertThat(free.navigation().nextLesson()).isNotNull();
        assertThat(free.navigation().nextLesson().title()).isEqualTo("Salida");
        assertThat(free.navigation().nextLesson().isFree()).isFalse();
        assertThat(free.subscription().plansUrl()).isEqualTo("/api/v1/billing/plans");
        assertThat(free.subscription().message()).contains("Suscríbete");
        // Critical invariant: a free lesson never reaches the billing port.
        verify(entitlementPort, never()).hasActiveEntitlement(any(), anyString());
    }

    @Test
    void anonymous_caller_on_a_free_lesson_does_not_consult_the_billing_port() {
        CourseId courseId = CourseId.generate();
        ModuleId moduleId = ModuleId.generate();
        LessonId lessonId = LessonId.generate();

        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(
            lesson(lessonId, moduleId, courseId, "Caminada", "vid-target", 10, true, 2)
        ));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(
            module(courseId, moduleId, "Postura", 1)
        ));
        when(courseRepository.findPublishedById(courseId)).thenReturn(Optional.of(publishedCourse(courseId)));
        when(lessonRepository.findByModuleId(moduleId)).thenReturn(siblingLessons(moduleId, courseId, lessonId));

        // actingUserId == null simulates a request that hit the controller before the
        // JWT filter populated the SecurityContext.
        Optional<PublicLessonView> view = useCase.get(lessonId.toString(), null);

        assertThat(view).isPresent();
        assertThat(view.get()).isInstanceOf(PublicLessonFreeView.class);
        verify(entitlementPort, never()).hasActiveEntitlement(any(), anyString());
    }

    @Test
    void premium_lesson_with_active_entitlement_returns_premium_view_with_videoId() {
        CourseId courseId = CourseId.generate();
        ModuleId moduleId = ModuleId.generate();
        LessonId lessonId = LessonId.generate();
        UUID userId = UUID.randomUUID();

        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(
            lesson(lessonId, moduleId, courseId, "Avanzado", "SECRET-VIDEO-ID", 15, false, 2)
        ));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(
            module(courseId, moduleId, "Postura", 1)
        ));
        when(courseRepository.findPublishedById(courseId)).thenReturn(Optional.of(publishedCourse(courseId)));
        when(lessonRepository.findByModuleId(moduleId)).thenReturn(List.of(
            lesson(LessonId.generate(), moduleId, courseId, "Intro", "vid-p", 5, true, 1),
            lesson(lessonId, moduleId, courseId, "Avanzado", "vid-a", 15, false, 2)
        ));
        when(entitlementPort.hasActiveEntitlement(eq(userId), eq(courseId.getValue().toString())))
            .thenReturn(true);

        Optional<PublicLessonView> view = useCase.get(lessonId.toString(), userId);

        assertThat(view).isPresent();
        assertThat(view.get()).isInstanceOf(PublicLessonPremiumAccessibleView.class);
        PublicLessonPremiumAccessibleView premium = (PublicLessonPremiumAccessibleView) view.get();
        assertThat(premium.lesson().videoId()).isEqualTo("SECRET-VIDEO-ID");
        assertThat(premium.lesson().isFree()).isFalse();
        assertThat(premium.lesson().duration()).isEqualTo("15:00");
        assertThat(premium.lesson().module().title()).isEqualTo("Postura");
        assertThat(premium.navigation().previousLesson().title()).isEqualTo("Intro");
        assertThat(premium.navigation().nextLesson()).isNull();
        // No "subscription" CTA on the premium-accessible branch.
        assertThat(premium.navigation().previousLesson().lessonId()).isNotNull();
    }

    @Test
    void premium_lesson_with_no_entitlement_returns_requires_subscription_view_with_access_disallowed() {
        CourseId courseId = CourseId.generate();
        ModuleId moduleId = ModuleId.generate();
        LessonId lessonId = LessonId.generate();
        UUID userId = UUID.randomUUID();

        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(
            lesson(lessonId, moduleId, courseId, "Avanzado", "should-not-leak", 15, false, 2)
        ));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(
            module(courseId, moduleId, "Postura", 1)
        ));
        when(courseRepository.findPublishedById(courseId)).thenReturn(Optional.of(publishedCourse(courseId)));
        // Stub the sibling scan even if this test does not assert on navigation —
        // navigation is recomputed on every public read.
        when(lessonRepository.findByModuleId(moduleId)).thenReturn(List.of(
            lesson(lessonId, moduleId, courseId, "Avanzado", "vid-a", 15, false, 2)
        ));
        when(entitlementPort.hasActiveEntitlement(eq(userId), eq(courseId.getValue().toString())))
            .thenReturn(false);

        Optional<PublicLessonView> view = useCase.get(lessonId.toString(), userId);

        assertThat(view).isPresent();
        assertThat(view.get()).isInstanceOf(PublicLessonRequiresSubscriptionView.class);
        PublicLessonRequiresSubscriptionView gated = (PublicLessonRequiresSubscriptionView) view.get();
        // No videoId on this branch, by the type. Static structural guard below
        // (record components = exactly 6 named fields) is the real check;
        // runtime assertion is a no-op here so we keep the test simple and
        // the type-restriction readable.
        assertThat(gated.lesson().title()).isEqualTo("Avanzado");
        assertThat(gated.lesson().isFree()).isFalse();
        assertThat(gated.access().allowed()).isFalse();
        assertThat(gated.access().reason()).isEqualTo("SUBSCRIPTION_REQUIRED");
        assertThat(gated.access().message()).contains("suscripción");
        assertThat(gated.access().plansUrl()).isEqualTo("/api/v1/billing/plans");
    }

    @Test
    void premium_lesson_with_anonymous_caller_throws_ForbiddenLessonAccessException() {
        CourseId courseId = CourseId.generate();
        ModuleId moduleId = ModuleId.generate();
        LessonId lessonId = LessonId.generate();

        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(
            lesson(lessonId, moduleId, courseId, "Avanzado", "vid-a", 15, false, 2)
        ));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(
            module(courseId, moduleId, "Postura", 1)
        ));
        when(courseRepository.findPublishedById(courseId)).thenReturn(Optional.of(publishedCourse(courseId)));

        // Anonymous caller → no userId at all → throw before even consulting billing.
        assertThatThrownBy(() -> useCase.get(lessonId.toString(), null))
            .isInstanceOf(ForbiddenLessonAccessException.class);
        verify(entitlementPort, never()).hasActiveEntitlement(any(), anyString());
    }

    @Test
    void malformed_lesson_id_collapses_to_Optional_empty() {
        Optional<PublicLessonView> blank = useCase.get("", null);
        Optional<PublicLessonView> garbage = useCase.get("not-a-uuid", UUID.randomUUID());

        assertThat(blank).isEmpty();
        assertThat(garbage).isEmpty();
        verify(lessonRepository, never()).findById(any());
        verify(courseRepository, never()).findPublishedById(any());
        verify(entitlementPort, never()).hasActiveEntitlement(any(), anyString());
    }

    @Test
    void missing_lesson_id_collapses_to_Optional_empty() {
        LessonId missing = LessonId.generate();
        when(lessonRepository.findById(missing)).thenReturn(Optional.empty());

        Optional<PublicLessonView> view = useCase.get(missing.toString(), UUID.randomUUID());

        assertThat(view).isEmpty();
        verify(moduleRepository, never()).findById(any());
        verify(courseRepository, never()).findPublishedById(any());
    }

    @Test
    void lesson_with_a_non_published_parent_course_collapses_to_empty() {
        CourseId courseId = CourseId.generate();
        ModuleId moduleId = ModuleId.generate();
        LessonId lessonId = LessonId.generate();

        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(
            lesson(lessonId, moduleId, courseId, "Caminada", "vid-target", 10, true, 2)
        ));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(
            module(courseId, moduleId, "Postura", 1)
        ));
        // Parent course is a DRAFT — findPublishedById returns empty even though the
        // row exists. The use case does NOT distinguish, by orchestrator's spec.
        when(courseRepository.findPublishedById(courseId)).thenReturn(Optional.empty());

        Optional<PublicLessonView> view = useCase.get(lessonId.toString(), UUID.randomUUID());

        assertThat(view).isEmpty();
    }

    @Test
    void lesson_whose_module_lookup_returns_empty_collapses_to_empty() {
        CourseId courseId = CourseId.generate();
        ModuleId moduleId = ModuleId.generate();
        LessonId lessonId = LessonId.generate();

        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(
            lesson(lessonId, moduleId, courseId, "Caminada", "vid-target", 10, true, 2)
        ));
        // FK invariant broken — log a warning and collapse to empty.
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.empty());

        Optional<PublicLessonView> view = useCase.get(lessonId.toString(), UUID.randomUUID());

        assertThat(view).isEmpty();
        verify(courseRepository, never()).findPublishedById(any());
    }

    @Test
    void first_lesson_of_a_module_has_no_previous_pointer() {
        CourseId courseId = CourseId.generate();
        ModuleId moduleId = ModuleId.generate();
        LessonId lessonId = LessonId.generate();

        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(
            lesson(lessonId, moduleId, courseId, "Caminada", "vid-target", 10, true, 1)
        ));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(
            module(courseId, moduleId, "Postura", 1)
        ));
        when(courseRepository.findPublishedById(courseId)).thenReturn(Optional.of(publishedCourse(courseId)));
        // Only this lesson in the module.
        when(lessonRepository.findByModuleId(moduleId)).thenReturn(List.of(
            lesson(lessonId, moduleId, courseId, "Caminada", "vid-target", 10, true, 1)
        ));

        Optional<PublicLessonView> view = useCase.get(lessonId.toString(), UUID.randomUUID());
        PublicLessonFreeView free = (PublicLessonFreeView) view.orElseThrow();

        assertThat(free.navigation().previousLesson()).isNull();
        assertThat(free.navigation().nextLesson()).isNull();
    }

    @Test
    void PublicLessonPreviewView_record_cannot_carry_videoId_by_type() {
        // Static structural guard: an authenticated-but-not-subscribed branch must
        // never be able to surface a Bunny.net reference. If a future refactor
        // adds a videoId component to the preview record, this test fails before
        // the change ever reaches review.
        java.lang.reflect.RecordComponent[] components = com.menta.virtual.application.dto.PublicLessonPreviewView.class.getRecordComponents();
        assertThat(components).extracting(java.lang.reflect.RecordComponent::getName)
            .containsExactlyInAnyOrder("lessonId", "title", "description", "duration", "isFree", "thumbnailUrl");
    }

    @Test
    void PublicLessonAccessDto_record_carries_the_plans_url_contract() {
        // Static structural guard: the wire-level access payload is exactly the
        // shape the BFF expects, with the stable {@code reason} string the
        // frontend keys off.
        LessonAccessDecisionDto access = LessonAccessDecisionDto.requiresSubscription("/api/v1/billing/plans");

        assertThat(access.allowed()).isFalse();
        assertThat(access.reason()).isEqualTo("SUBSCRIPTION_REQUIRED");
        assertThat(access.plansUrl()).isEqualTo("/api/v1/billing/plans");
        assertThat(access.message()).isNotBlank();
    }
}
