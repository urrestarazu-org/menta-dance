package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.VirtualCourseDetailView;
import com.menta.virtual.application.dto.VirtualLessonSummary;
import com.menta.virtual.application.dto.VirtualModuleDetail;
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
 * Coverage for {@link VirtualCourseCatalogPortImpl#findPublishedDetailById}
 * — distinctly from {@code VirtualCourseCatalogPortImplTest} (which covers
 * the summary list + lookup path) because the detail method has its own
 * collaborators (modules, lessons) and its own no-leak invariant
 * (@code videoId} never reaches the cross-module projection).
 */
class VirtualCourseCatalogPortImplDetailTest {

    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualModuleRepository moduleRepository = mock(VirtualModuleRepository.class);
    private final VirtualLessonRepository lessonRepository = mock(VirtualLessonRepository.class);
    private final VirtualCourseCatalogPortImpl port =
        new VirtualCourseCatalogPortImpl(courseRepository, moduleRepository, lessonRepository);

    private static VirtualCourse course(CourseId id) {
        // Aggregate counts deliberately match what the detail walk will
        // produce: 2 modules × 2 lessons, 30 + 40 minutes each — so the
        // happy-path test can also assert stats.lessonCount is the AGGREGATE
        // count (4), not a recomputed walk count.
        return new VirtualCourse(
            id, "Tango Básico", "Aprendé los pasos fundamentales", "Descripción larga", UUID.randomUUID(),
            "https://cdn/tango.jpg", CourseCategory.of("tango"), CourseLevel.BEGINNER, true,
            CourseStatus.PUBLISHED, 2, 4, 70
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

    @Test
    void find_published_detail_by_id_happy_path_returns_populated_detail_with_modules_and_lessons_summing_correctly() {
        CourseId courseId = CourseId.generate();
        ModuleId moduleOne = ModuleId.generate();
        ModuleId moduleTwo = ModuleId.generate();

        when(courseRepository.findPublishedById(courseId)).thenReturn(Optional.of(course(courseId)));
        when(moduleRepository.findByCourseId(courseId)).thenReturn(List.of(
            module(courseId, moduleOne, "Introducción", 1),
            module(courseId, moduleTwo, "Postura", 2)
        ));
        when(lessonRepository.findByModuleId(moduleOne)).thenReturn(List.of(
            lesson(LessonId.generate(), moduleOne, courseId, "Historia", "vid-x", 30, true, 1),
            lesson(LessonId.generate(), moduleOne, courseId, "Caminada", "vid-y", 10, false, 2)
        ));
        // Set up a stub for moduleTwo even if the test does not drill in:
        when(lessonRepository.findByModuleId(moduleTwo)).thenReturn(List.of(
            lesson(LessonId.generate(), moduleTwo, courseId, "Salida", "vid-z", 15, false, 1),
            lesson(LessonId.generate(), moduleTwo, courseId, "Giro", "vid-w", 15, true, 2)
        ));

        Optional<VirtualCourseDetailView> result = port.findPublishedDetailById(courseId.toString());

        assertThat(result).isPresent();
        VirtualCourseDetailView view = result.get();
        assertThat(view.courseId()).isEqualTo(courseId.toString());
        assertThat(view.title()).isEqualTo("Tango Básico");
        assertThat(view.description()).isEqualTo("Descripción larga");
        assertThat(view.imageUrl()).isEqualTo("https://cdn/tango.jpg");
        assertThat(view.category()).isEqualTo("tango");
        assertThat(view.level()).isEqualTo("BEGINNER");
        assertThat(view.isPremium()).isTrue();

        List<VirtualModuleDetail> modules = view.modules();
        assertThat(modules).extracting(VirtualModuleDetail::title).containsExactly("Introducción", "Postura");
        assertThat(modules).extracting(VirtualModuleDetail::order).containsExactly(1, 2);

        List<VirtualLessonSummary> firstModuleLessons = modules.get(0).lessons();
        assertThat(firstModuleLessons).extracting(VirtualLessonSummary::title)
            .containsExactly("Historia", "Caminada");
        assertThat(firstModuleLessons).extracting(VirtualLessonSummary::durationMinutes)
            .containsExactly(30, 10);
        assertThat(firstModuleLessons).extracting(VirtualLessonSummary::isFree)
            .containsExactly(true, false);

        // Aggregate counts — never recomputed from the walk.
        assertThat(view.stats().moduleCount()).isEqualTo(2);
        assertThat(view.stats().lessonCount()).isEqualTo(4);
        assertThat(view.stats().totalDurationMinutes()).isEqualTo(70);
    }

    @Test
    void find_published_detail_by_id_missing_course_returns_empty_without_throwing() {
        CourseId courseId = CourseId.generate();
        when(courseRepository.findPublishedById(courseId)).thenReturn(Optional.empty());

        assertThat(port.findPublishedDetailById(courseId.toString())).isEmpty();
        verify(moduleRepository, never()).findByCourseId(any());
        verify(lessonRepository, never()).findByModuleId(any());
    }

    @Test
    void find_published_detail_by_id_never_exposes_video_id_in_lesson_summaries() {
        // Static structural check: the cross-module projection type cannot
        // carry a videoId at all, regardless of what the domain
        // VirtualLesson has. If a future refactor adds a videoId back this
        // test fails before reviewers ever see a leak.
        RecordComponent[] components = VirtualLessonSummary.class.getRecordComponents();
        assertThat(components).extracting(RecordComponent::getName)
            .containsExactlyInAnyOrder("lessonId", "title", "durationMinutes", "isFree", "order");

        // Behavior check: the port's projection must drop the source's
        // videoId even when the source has one.
        CourseId courseId = CourseId.generate();
        ModuleId moduleId = ModuleId.generate();

        when(courseRepository.findPublishedById(courseId)).thenReturn(Optional.of(course(courseId)));
        when(moduleRepository.findByCourseId(courseId)).thenReturn(List.of(
            module(courseId, moduleId, "Solo módulo", 1)
        ));
        when(lessonRepository.findByModuleId(moduleId)).thenReturn(List.of(
            lesson(
                LessonId.generate(), moduleId, courseId,
                "Lección premium con video", "SECRET-VIDEO-ID-42",
                15, false, 1
            )
        ));

        List<VirtualLessonSummary> summaries = port.findPublishedDetailById(courseId.toString())
            .orElseThrow().modules().get(0).lessons();

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).title()).isEqualTo("Lección premium con video");
        // No getter to reach the dropped value — the record component list
        // assertion above proves it cannot be there in the first place.
    }

    @Test
    void find_published_detail_by_id_empty_modules_list_returns_empty_modules_array_not_null() {
        CourseId courseId = CourseId.generate();
        when(courseRepository.findPublishedById(courseId)).thenReturn(Optional.of(course(courseId)));
        when(moduleRepository.findByCourseId(courseId)).thenReturn(List.of());

        Optional<VirtualCourseDetailView> result = port.findPublishedDetailById(courseId.toString());

        assertThat(result).isPresent();
        assertThat(result.get().modules()).isNotNull().isEmpty();
        verify(lessonRepository, never()).findByModuleId(any());
    }
}
