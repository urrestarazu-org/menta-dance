package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.UpdateVirtualLessonCommand;
import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.domain.exception.CourseNotOwnedException;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.domain.model.VirtualLesson;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdateVirtualLessonUseCaseImplTest {

    private final VirtualLessonRepository lessonRepository = mock(VirtualLessonRepository.class);
    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualCourseAuditRepository auditRepository = mock(VirtualCourseAuditRepository.class);
    private final UpdateVirtualLessonUseCaseImpl useCase =
        new UpdateVirtualLessonUseCaseImpl(lessonRepository, courseRepository, auditRepository);

    private static VirtualCourse course(CourseId id, UUID professorId) {
        return new VirtualCourse(
            id, "t", "s", "d", professorId, "i", CourseCategory.of("tango"), CourseLevel.BEGINNER, false,
            CourseStatus.DRAFT, 0, 0, 0
        );
    }

    private static UpdateVirtualLessonCommand emptyCommand() {
        return new UpdateVirtualLessonCommand(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty()
        );
    }

    @Test
    void throws_when_the_lesson_does_not_exist() {
        LessonId id = LessonId.generate();
        when(lessonRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.update(id.toString(), emptyCommand(), UUID.randomUUID(), true))
            .isInstanceOf(LessonNotFoundException.class);
    }

    @Test
    void instructor_editing_a_lesson_of_a_course_they_do_not_own_is_rejected() {
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        VirtualLesson lesson = VirtualLesson.create(ModuleId.generate(), courseId, "L1", "d", "v", 10, false, 0);
        when(lessonRepository.findById(lesson.getId())).thenReturn(Optional.of(lesson));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));

        assertThatThrownBy(() -> useCase.update(lesson.getId().toString(), emptyCommand(), UUID.randomUUID(), false))
            .isInstanceOf(CourseNotOwnedException.class);
    }

    @Test
    void applies_only_the_fields_present_in_the_command() {
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        VirtualLesson lesson = VirtualLesson.create(ModuleId.generate(), courseId, "L1", "d", "v", 10, false, 0);
        when(lessonRepository.findById(lesson.getId())).thenReturn(Optional.of(lesson));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));
        when(lessonRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateVirtualLessonCommand command = new UpdateVirtualLessonCommand(
            Optional.of("Nuevo título"), Optional.empty(), Optional.of("otro-video"), Optional.of(20),
            Optional.of(true), Optional.of(3)
        );
        VirtualLessonManagementResult result = useCase.update(lesson.getId().toString(), command, ownerId, false);

        assertThat(result.title()).isEqualTo("Nuevo título");
        assertThat(result.videoId()).isEqualTo("otro-video");
        assertThat(result.durationMinutes()).isEqualTo(20);
        assertThat(result.free()).isTrue();
        assertThat(result.order()).isEqualTo(3);
        assertThat(result.description()).isEqualTo("d");
    }
}
