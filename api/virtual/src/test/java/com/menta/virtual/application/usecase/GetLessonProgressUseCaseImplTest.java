package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.shared.billing.CourseAccessSnapshot;
import com.menta.shared.billing.VirtualCourseEntitlementPort;
import com.menta.virtual.application.dto.LessonProgressView;
import com.menta.virtual.application.port.out.LessonProgressRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.ForbiddenLessonAccessException;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.LessonProgress;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.domain.model.VirtualModule;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetLessonProgressUseCaseImplTest {

    private final VirtualLessonRepository lessonRepository = mock(VirtualLessonRepository.class);
    private final VirtualModuleRepository moduleRepository = mock(VirtualModuleRepository.class);
    private final LessonProgressRepository progressRepository = mock(LessonProgressRepository.class);
    private final VirtualCourseEntitlementPort entitlementPort = mock(VirtualCourseEntitlementPort.class);
    private final GetLessonProgressUseCaseImpl useCase = new GetLessonProgressUseCaseImpl(
        lessonRepository, moduleRepository, progressRepository, new LessonAccessPolicy(entitlementPort)
    );

    private static VirtualLesson freeLesson(LessonId id, ModuleId moduleId, CourseId courseId, int duration) {
        return new VirtualLesson(id, moduleId, courseId, "L1", "d", "v", duration, true, 0);
    }

    private static VirtualLesson protectedLesson(LessonId id, ModuleId moduleId, CourseId courseId) {
        return new VirtualLesson(id, moduleId, courseId, "L1", "d", "v", 10, false, 0);
    }

    private static VirtualModule module(ModuleId id, CourseId courseId) {
        return new VirtualModule(id, courseId, "M1", false, 0);
    }

    @Test
    void returns_a_default_zeroed_view_when_no_row_exists_yet() {
        LessonId lessonId = LessonId.generate();
        ModuleId moduleId = ModuleId.generate();
        CourseId courseId = CourseId.generate();
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(freeLesson(lessonId, moduleId, courseId, 10)));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module(moduleId, courseId)));
        UUID userId = UUID.randomUUID();
        when(progressRepository.findByUserIdAndLessonId(userId, lessonId)).thenReturn(Optional.empty());

        Optional<LessonProgressView> view = useCase.get(lessonId.toString(), userId);

        assertThat(view).isPresent();
        assertThat(view.get().positionSeconds()).isZero();
        assertThat(view.get().completed()).isFalse();
    }

    @Test
    void a_lapsed_subscriber_loses_read_access() {
        LessonId lessonId = LessonId.generate();
        ModuleId moduleId = ModuleId.generate();
        CourseId courseId = CourseId.generate();
        when(lessonRepository.findById(lessonId))
            .thenReturn(Optional.of(protectedLesson(lessonId, moduleId, courseId)));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module(moduleId, courseId)));
        UUID userId = UUID.randomUUID();
        when(entitlementPort.resolveCourseAccess(userId, courseId.getValue().toString()))
            .thenReturn(new CourseAccessSnapshot(true, false));

        assertThatThrownBy(() -> useCase.get(lessonId.toString(), userId))
            .isInstanceOf(ForbiddenLessonAccessException.class);
    }

    @Test
    void unknown_lesson_id_yields_empty() {
        LessonId lessonId = LessonId.generate();
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.empty());

        assertThat(useCase.get(lessonId.toString(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void malformed_lesson_id_yields_empty() {
        assertThat(useCase.get("not-a-uuid", UUID.randomUUID())).isEmpty();
    }

    @Test
    void position_is_clamped_to_the_current_duration_on_read() {
        LessonId lessonId = LessonId.generate();
        ModuleId moduleId = ModuleId.generate();
        CourseId courseId = CourseId.generate();
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(freeLesson(lessonId, moduleId, courseId, 1)));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module(moduleId, courseId)));
        UUID userId = UUID.randomUUID();
        LessonProgress stale = LessonProgress.start(userId, lessonId, courseId)
            .withPosition(500, 3_000, Instant.now());
        when(progressRepository.findByUserIdAndLessonId(userId, lessonId)).thenReturn(Optional.of(stale));

        Optional<LessonProgressView> view = useCase.get(lessonId.toString(), userId);

        assertThat(view).isPresent();
        assertThat(view.get().positionSeconds()).isEqualTo(60);
    }
}
