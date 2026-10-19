package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.CreateVirtualLessonCommand;
import com.menta.virtual.application.dto.VirtualLessonManagementResult;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.CourseNotOwnedException;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateVirtualLessonUseCaseImplTest {

    private final VirtualModuleRepository moduleRepository = mock(VirtualModuleRepository.class);
    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualLessonRepository lessonRepository = mock(VirtualLessonRepository.class);
    private final VirtualCourseAuditRepository auditRepository = mock(VirtualCourseAuditRepository.class);
    private final CreateVirtualLessonUseCaseImpl useCase = new CreateVirtualLessonUseCaseImpl(
        moduleRepository, courseRepository, lessonRepository, auditRepository
    );

    private static VirtualCourse course(CourseId id, UUID professorId) {
        return new VirtualCourse(
            id, "t", "s", "d", professorId, "i", CourseCategory.of("tango"), CourseLevel.BEGINNER, false,
            CourseStatus.DRAFT, 0, 0, 0
        );
    }

    private static CreateVirtualLessonCommand command(Optional<Integer> order) {
        return new CreateVirtualLessonCommand("L1", "d", "bunny-123", 10, false, order);
    }

    @Test
    void instructor_creating_a_lesson_for_a_module_of_a_course_they_do_not_own_is_rejected() {
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        VirtualModule module = VirtualModule.create(courseId, "Módulo 1", 0);
        when(moduleRepository.findById(module.getId())).thenReturn(Optional.of(module));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));

        assertThatThrownBy(() -> useCase.create(
            module.getId().toString(), command(Optional.empty()), UUID.randomUUID(), false
        )).isInstanceOf(CourseNotOwnedException.class);
    }

    @Test
    void appends_at_the_end_when_order_is_absent() {
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        VirtualModule module = VirtualModule.create(courseId, "Módulo 1", 0);
        when(moduleRepository.findById(module.getId())).thenReturn(Optional.of(module));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));
        when(lessonRepository.countByModuleId(module.getId())).thenReturn(1);
        when(lessonRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VirtualLessonManagementResult result =
            useCase.create(module.getId().toString(), command(Optional.empty()), ownerId, false);

        assertThat(result.order()).isEqualTo(1);
        assertThat(result.courseId()).isEqualTo(courseId.toString());
    }

    @Test
    void uses_the_requested_order_when_present() {
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        VirtualModule module = VirtualModule.create(courseId, "Módulo 1", 0);
        when(moduleRepository.findById(module.getId())).thenReturn(Optional.of(module));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));
        when(lessonRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VirtualLessonManagementResult result =
            useCase.create(module.getId().toString(), command(Optional.of(7)), ownerId, false);

        assertThat(result.order()).isEqualTo(7);
    }
}
