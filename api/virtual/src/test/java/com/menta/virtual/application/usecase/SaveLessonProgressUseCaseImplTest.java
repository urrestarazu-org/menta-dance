package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.shared.billing.CourseAccessSnapshot;
import com.menta.shared.billing.VirtualCourseEntitlementPort;
import com.menta.virtual.application.dto.LessonProgressView;
import com.menta.virtual.application.port.out.Clock;
import com.menta.virtual.application.port.out.LessonProgressRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.ForbiddenLessonAccessException;
import com.menta.virtual.domain.exception.InvalidLessonPositionException;
import com.menta.virtual.domain.exception.LessonNotFoundException;
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

class SaveLessonProgressUseCaseImplTest {

    private final VirtualLessonRepository lessonRepository = mock(VirtualLessonRepository.class);
    private final VirtualModuleRepository moduleRepository = mock(VirtualModuleRepository.class);
    private final LessonProgressRepository progressRepository = mock(LessonProgressRepository.class);
    private final VirtualCourseEntitlementPort entitlementPort = mock(VirtualCourseEntitlementPort.class);
    private final Clock clock = () -> Instant.parse("2026-01-01T00:00:00Z");
    private final SaveLessonProgressUseCaseImpl useCase = new SaveLessonProgressUseCaseImpl(
        lessonRepository, moduleRepository, progressRepository, new LessonAccessPolicy(entitlementPort), clock
    );

    private static VirtualLesson freeLesson(LessonId id, ModuleId moduleId, CourseId courseId) {
        return new VirtualLesson(id, moduleId, courseId, "L1", "d", "v", 10, true, 0);
    }

    private static VirtualLesson protectedLesson(LessonId id, ModuleId moduleId, CourseId courseId) {
        return new VirtualLesson(id, moduleId, courseId, "L1", "d", "v", 10, false, 0);
    }

    private static VirtualModule module(ModuleId id, CourseId courseId, boolean preview) {
        return new VirtualModule(id, courseId, "M1", preview, 0);
    }

    @Test
    void saves_a_free_lesson_without_entitlement() {
        LessonId lessonId = LessonId.generate();
        ModuleId moduleId = ModuleId.generate();
        CourseId courseId = CourseId.generate();
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(freeLesson(lessonId, moduleId, courseId)));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module(moduleId, courseId, false)));
        UUID userId = UUID.randomUUID();
        when(progressRepository.findByUserIdAndLessonId(userId, lessonId)).thenReturn(Optional.empty());
        when(progressRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LessonProgressView view = useCase.save(lessonId.toString(), userId, 300);

        assertThat(view.positionSeconds()).isEqualTo(300);
        assertThat(view.completed()).isFalse();
    }

    @Test
    void denies_a_protected_lesson_without_entitlement() {
        LessonId lessonId = LessonId.generate();
        ModuleId moduleId = ModuleId.generate();
        CourseId courseId = CourseId.generate();
        when(lessonRepository.findById(lessonId))
            .thenReturn(Optional.of(protectedLesson(lessonId, moduleId, courseId)));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module(moduleId, courseId, false)));
        UUID userId = UUID.randomUUID();
        when(entitlementPort.resolveCourseAccess(userId, courseId.getValue().toString()))
            .thenReturn(new CourseAccessSnapshot(true, false));

        assertThatThrownBy(() -> useCase.save(lessonId.toString(), userId, 300))
            .isInstanceOf(ForbiddenLessonAccessException.class);
        verify(progressRepository, never()).save(any());
    }

    @Test
    void rejects_an_out_of_bounds_position_before_any_write() {
        LessonId lessonId = LessonId.generate();
        ModuleId moduleId = ModuleId.generate();
        CourseId courseId = CourseId.generate();
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(freeLesson(lessonId, moduleId, courseId)));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module(moduleId, courseId, false)));
        UUID userId = UUID.randomUUID();
        when(progressRepository.findByUserIdAndLessonId(userId, lessonId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.save(lessonId.toString(), userId, 601))
            .isInstanceOf(InvalidLessonPositionException.class);
        verify(progressRepository, never()).save(any());
    }

    @Test
    void repeating_the_identical_position_is_idempotent() {
        LessonId lessonId = LessonId.generate();
        ModuleId moduleId = ModuleId.generate();
        CourseId courseId = CourseId.generate();
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(freeLesson(lessonId, moduleId, courseId)));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module(moduleId, courseId, false)));
        UUID userId = UUID.randomUUID();
        LessonProgress existing = LessonProgress.start(userId, lessonId, courseId)
            .withPosition(300, 600, Instant.parse("2025-01-01T00:00:00Z"));
        when(progressRepository.findByUserIdAndLessonId(userId, lessonId)).thenReturn(Optional.of(existing));
        when(progressRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LessonProgressView view = useCase.save(lessonId.toString(), userId, 300);

        assertThat(view.positionSeconds()).isEqualTo(300);
        assertThat(view.completed()).isFalse();
    }

    @Test
    void unknown_lesson_id_throws_not_found() {
        LessonId lessonId = LessonId.generate();
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.save(lessonId.toString(), UUID.randomUUID(), 0))
            .isInstanceOf(LessonNotFoundException.class);
    }

    @Test
    void malformed_lesson_id_throws_not_found() {
        assertThatThrownBy(() -> useCase.save("not-a-uuid", UUID.randomUUID(), 0))
            .isInstanceOf(LessonNotFoundException.class);
    }
}
