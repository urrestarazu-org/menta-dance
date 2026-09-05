package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.shared.billing.CourseAccessSnapshot;
import com.menta.shared.billing.VirtualCourseEntitlementPort;
import com.menta.virtual.application.dto.LessonProgressView;
import com.menta.virtual.application.port.out.Clock;
import com.menta.virtual.application.port.out.LessonProgressRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.ForbiddenLessonAccessException;
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

class CompleteLessonUseCaseImplTest {

    private final VirtualLessonRepository lessonRepository = mock(VirtualLessonRepository.class);
    private final VirtualModuleRepository moduleRepository = mock(VirtualModuleRepository.class);
    private final LessonProgressRepository progressRepository = mock(LessonProgressRepository.class);
    private final VirtualCourseEntitlementPort entitlementPort = mock(VirtualCourseEntitlementPort.class);
    private final Clock clock = () -> Instant.parse("2026-01-01T00:00:00Z");
    private final CompleteLessonUseCaseImpl useCase = new CompleteLessonUseCaseImpl(
        lessonRepository, moduleRepository, progressRepository, new LessonAccessPolicy(entitlementPort), clock
    );

    private static VirtualLesson freeLesson(LessonId id, ModuleId moduleId, CourseId courseId) {
        return new VirtualLesson(id, moduleId, courseId, "L1", "d", "v", 10, true, 0);
    }

    private static VirtualLesson protectedLesson(LessonId id, ModuleId moduleId, CourseId courseId) {
        return new VirtualLesson(id, moduleId, courseId, "L1", "d", "v", 10, false, 0);
    }

    private static VirtualModule module(ModuleId id, CourseId courseId) {
        return new VirtualModule(id, courseId, "M1", false, 0);
    }

    @Test
    void completing_does_not_move_the_saved_position() {
        LessonId lessonId = LessonId.generate();
        ModuleId moduleId = ModuleId.generate();
        CourseId courseId = CourseId.generate();
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(freeLesson(lessonId, moduleId, courseId)));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module(moduleId, courseId)));
        UUID userId = UUID.randomUUID();
        LessonProgress existing = LessonProgress.start(userId, lessonId, courseId)
            .withPosition(120, 600, Instant.parse("2025-01-01T00:00:00Z"));
        when(progressRepository.findByUserIdAndLessonId(userId, lessonId)).thenReturn(Optional.of(existing));
        when(progressRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LessonProgressView view = useCase.complete(lessonId.toString(), userId);

        assertThat(view.completed()).isTrue();
        assertThat(view.positionSeconds()).isEqualTo(120);
    }

    @Test
    void repeating_completion_is_a_no_op() {
        LessonId lessonId = LessonId.generate();
        ModuleId moduleId = ModuleId.generate();
        CourseId courseId = CourseId.generate();
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.of(freeLesson(lessonId, moduleId, courseId)));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module(moduleId, courseId)));
        UUID userId = UUID.randomUUID();
        LessonProgress alreadyCompleted = LessonProgress.start(userId, lessonId, courseId)
            .markCompleted(Instant.parse("2025-06-01T00:00:00Z"));
        when(progressRepository.findByUserIdAndLessonId(userId, lessonId)).thenReturn(Optional.of(alreadyCompleted));
        when(progressRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LessonProgressView view = useCase.complete(lessonId.toString(), userId);

        assertThat(view.completed()).isTrue();
        assertThat(view.completedAt()).isEqualTo(Instant.parse("2025-06-01T00:00:00Z"));
    }

    @Test
    void denies_a_protected_lesson_without_entitlement() {
        LessonId lessonId = LessonId.generate();
        ModuleId moduleId = ModuleId.generate();
        CourseId courseId = CourseId.generate();
        when(lessonRepository.findById(lessonId))
            .thenReturn(Optional.of(protectedLesson(lessonId, moduleId, courseId)));
        when(moduleRepository.findById(moduleId)).thenReturn(Optional.of(module(moduleId, courseId)));
        UUID userId = UUID.randomUUID();
        when(entitlementPort.resolveCourseAccess(userId, courseId.getValue().toString()))
            .thenReturn(new CourseAccessSnapshot(true, false));

        assertThatThrownBy(() -> useCase.complete(lessonId.toString(), userId))
            .isInstanceOf(ForbiddenLessonAccessException.class);
    }

    @Test
    void unknown_lesson_id_throws_not_found() {
        LessonId lessonId = LessonId.generate();
        when(lessonRepository.findById(lessonId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.complete(lessonId.toString(), UUID.randomUUID()))
            .isInstanceOf(LessonNotFoundException.class);
    }
}
