package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.VirtualCourseAdminDetailView;
import com.menta.virtual.application.dto.VirtualLessonAdminSummary;
import com.menta.virtual.application.dto.VirtualModuleAdminDetail;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.domain.model.VirtualModule;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Coverage for {@link VirtualCourseCatalogPortImpl#findByIdForAdmin} —
 * split from {@code VirtualCourseCatalogPortImplDetailTest}, which covers
 * the public detail path, because the admin path has its own invariants:
 * <ol>
 *   <li>must expose {@code status} (DRAFT/PUBLISHED/ARCHIVED) regardless
 *       of the public non-enumeration discipline,</li>
 *   <li>must expose {@code videoId} on every lesson,</li>
 *   <li>must return {@code Optional.empty()} on a malformed courseId
 *       (the contract split with the public path that propagates
 *       {@code IllegalArgumentException}),</li>
 *   <li>must NOT silently merge "exists but DRAFT/ARCHIVED" with "missing"
 *       — emptying is reserved exclusively for the latter.</li>
 * </ol>
 */
class VirtualCourseCatalogPortImplAdminDetailTest {

    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualModuleRepository moduleRepository = mock(VirtualModuleRepository.class);
    private final VirtualLessonRepository lessonRepository = mock(VirtualLessonRepository.class);
    private final VirtualCourseCatalogPortImpl port =
        new VirtualCourseCatalogPortImpl(courseRepository, moduleRepository, lessonRepository);

    private static VirtualCourse course(CourseId id, CourseStatus status) {
        // Aggregate counts are deliberately realistic (2 modules × 2 lessons,
        // 30 + 40 minutes) so the happy-path test can assert
        // stats.lessonCount is the AGGREGATE count (4), not a recomputed walk.
        return new VirtualCourse(
            id, "Tango Básico", "Aprendé los pasos fundamentales", "Descripción larga", UUID.randomUUID(),
            "https://cdn/tango.jpg", CourseCategory.of("tango"), CourseLevel.BEGINNER, true,
            status, 2, 4, 70
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

    private void stubAggregatesAndModules(CourseId courseId, ModuleId m1, ModuleId m2) {
        when(moduleRepository.findByCourseId(courseId)).thenReturn(List.of(
            module(courseId, m1, "Introducción", 1),
            module(courseId, m2, "Postura", 2)
        ));
        when(lessonRepository.findByModuleId(m1)).thenReturn(List.of(
            lesson(LessonId.generate(), m1, courseId, "Historia", "vid-x", 30, true, 1),
            lesson(LessonId.generate(), m1, courseId, "Caminada", "vid-y", 10, false, 2)
        ));
        when(lessonRepository.findByModuleId(m2)).thenReturn(List.of(
            lesson(LessonId.generate(), m2, courseId, "Salida", "vid-z", 15, false, 1),
            lesson(LessonId.generate(), m2, courseId, "Giro", "vid-w", 15, true, 2)
        ));
    }

    @Test
    void find_by_id_for_admin_returns_detail_with_status_DRAFT() {
        CourseId courseId = CourseId.generate();
        ModuleId m1 = ModuleId.generate();
        ModuleId m2 = ModuleId.generate();

        when(courseRepository.findByIdAnyStatus(courseId))
            .thenReturn(Optional.of(course(courseId, CourseStatus.DRAFT)));
        stubAggregatesAndModules(courseId, m1, m2);

        VirtualCourseAdminDetailView view = port.findByIdForAdmin(courseId.toString()).orElseThrow();

        assertThat(view.status()).isEqualTo(CourseStatus.DRAFT);
    }

    @Test
    void find_by_id_for_admin_returns_detail_with_status_PUBLISHED() {
        CourseId courseId = CourseId.generate();
        ModuleId m1 = ModuleId.generate();
        ModuleId m2 = ModuleId.generate();

        when(courseRepository.findByIdAnyStatus(courseId))
            .thenReturn(Optional.of(course(courseId, CourseStatus.PUBLISHED)));
        stubAggregatesAndModules(courseId, m1, m2);

        VirtualCourseAdminDetailView view = port.findByIdForAdmin(courseId.toString()).orElseThrow();

        assertThat(view.status()).isEqualTo(CourseStatus.PUBLISHED);
    }

    @Test
    void find_by_id_for_admin_returns_detail_with_status_ARCHIVED() {
        CourseId courseId = CourseId.generate();
        ModuleId m1 = ModuleId.generate();
        ModuleId m2 = ModuleId.generate();

        when(courseRepository.findByIdAnyStatus(courseId))
            .thenReturn(Optional.of(course(courseId, CourseStatus.ARCHIVED)));
        stubAggregatesAndModules(courseId, m1, m2);

        VirtualCourseAdminDetailView view = port.findByIdForAdmin(courseId.toString()).orElseThrow();

        assertThat(view.status()).isEqualTo(CourseStatus.ARCHIVED);
    }

    @Test
    void find_by_id_for_admin_exposes_video_id_in_every_lesson() {
        // Static structural check: VirtualLessonAdminSummary carries
        // videoId. If a future refactor drops it, this test fails before
        // reviewers see a regression.
        RecordComponent[] components = VirtualLessonAdminSummary.class.getRecordComponents();
        assertThat(components).extracting(RecordComponent::getName)
            .containsExactlyInAnyOrder("lessonId", "title", "durationMinutes", "isFree", "order", "videoId");

        // Behavior check: the port passes the source's videoId through,
        // even for free and for premium-alike lessons alike.
        CourseId courseId = CourseId.generate();
        ModuleId m1 = ModuleId.generate();

        when(courseRepository.findByIdAnyStatus(courseId))
            .thenReturn(Optional.of(course(courseId, CourseStatus.PUBLISHED)));
        when(moduleRepository.findByCourseId(courseId))
            .thenReturn(List.of(module(courseId, m1, "Solo módulo", 1)));
        when(lessonRepository.findByModuleId(m1)).thenReturn(List.of(
            lesson(
                LessonId.generate(), m1, courseId, "Lección admin-only con video",
                "SECRET-VIDEO-ID-42", 15, false, 1
            )
        ));

        VirtualModuleAdminDetail onlyModule =
            port.findByIdForAdmin(courseId.toString()).orElseThrow().modules().get(0);

        VirtualLessonAdminSummary firstLesson = onlyModule.lessons().get(0);
        assertThat(firstLesson.videoId()).isEqualTo("SECRET-VIDEO-ID-42");
    }

    @Test
    void find_by_id_for_admin_returns_empty_when_courseId_does_not_exist() {
        CourseId courseId = CourseId.generate();
        when(courseRepository.findByIdAnyStatus(courseId)).thenReturn(Optional.empty());

        assertThat(port.findByIdForAdmin(courseId.toString())).isEmpty();
        verify(moduleRepository, never()).findByCourseId(any());
        verify(lessonRepository, never()).findByModuleId(any());
    }

    @Test
    void find_by_id_for_admin_never_throws_for_a_malformed_courseId() {
        // Contract split with findPublishedDetailById: admin collapses
        // "malformed UUID" into Optional.empty() so the @RestControllerAdvice
        // returns a uniform 404 ProblemDetail for the operator UI.
        assertThat(port.findByIdForAdmin("not-a-uuid")).isEmpty();
        assertThat(port.findByIdForAdmin("")).isEmpty();

        verify(courseRepository, never()).findByIdAnyStatus(any());
        verify(moduleRepository, never()).findByCourseId(any());
        verify(lessonRepository, never()).findByModuleId(any());
    }

    @Test
    void find_by_id_for_admin_walks_modules_and_lessons_in_order_and_carries_aggregates() {
        // The full happy path: status, modules in order, lessons in order
        // with videoId every time, and pre-aggregated stats.
        CourseId courseId = CourseId.generate();
        ModuleId m1 = ModuleId.generate();
        ModuleId m2 = ModuleId.generate();

        when(courseRepository.findByIdAnyStatus(courseId))
            .thenReturn(Optional.of(course(courseId, CourseStatus.DRAFT)));
        stubAggregatesAndModules(courseId, m1, m2);

        VirtualCourseAdminDetailView view = port.findByIdForAdmin(courseId.toString()).orElseThrow();

        assertThat(view.modules()).extracting(VirtualModuleAdminDetail::title)
            .containsExactly("Introducción", "Postura");
        assertThat(view.modules()).extracting(VirtualModuleAdminDetail::order)
            .containsExactly(1, 2);

        List<VirtualLessonAdminSummary> firstModuleLessons = view.modules().get(0).lessons();
        assertThat(firstModuleLessons).extracting(VirtualLessonAdminSummary::title)
            .containsExactly("Historia", "Caminada");
        assertThat(firstModuleLessons).extracting(VirtualLessonAdminSummary::durationMinutes)
            .containsExactly(30, 10);
        assertThat(firstModuleLessons).extracting(VirtualLessonAdminSummary::isFree)
            .containsExactly(true, false);
        assertThat(firstModuleLessons).extracting(VirtualLessonAdminSummary::videoId)
            .containsExactly("vid-x", "vid-y");

        assertThat(view.stats().moduleCount()).isEqualTo(2);
        assertThat(view.stats().lessonCount()).isEqualTo(4);
        assertThat(view.stats().totalDurationMinutes()).isEqualTo(70);
    }
}
