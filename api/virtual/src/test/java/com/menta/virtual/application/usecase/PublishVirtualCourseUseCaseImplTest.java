package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.CourseNotPublishableException;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublishVirtualCourseUseCaseImplTest {

    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualModuleRepository moduleRepository = mock(VirtualModuleRepository.class);
    private final VirtualLessonRepository lessonRepository = mock(VirtualLessonRepository.class);
    private final VirtualCourseAuditRepository auditRepository = mock(VirtualCourseAuditRepository.class);
    private final PublishVirtualCourseUseCaseImpl useCase = new PublishVirtualCourseUseCaseImpl(
        courseRepository, moduleRepository, lessonRepository, auditRepository
    );

    private static VirtualCourse draft(CourseId id, UUID professorId) {
        return new VirtualCourse(
            id, "t", "s", "d", professorId, "i", CourseCategory.of("tango"), CourseLevel.BEGINNER, false,
            CourseStatus.DRAFT, 0, 0, 0
        );
    }

    @Test
    void rejects_publishing_a_course_with_no_modules() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(draft(id, ownerId)));
        when(moduleRepository.findByCourseId(id)).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.publish(id.toString(), ownerId, false))
            .isInstanceOf(CourseNotPublishableException.class);
    }

    @Test
    void rejects_publishing_a_course_whose_modules_have_no_complete_lesson() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        VirtualModule module = VirtualModule.create(id, "Módulo 1", 0);
        when(courseRepository.findById(id)).thenReturn(Optional.of(draft(id, ownerId)));
        when(moduleRepository.findByCourseId(id)).thenReturn(List.of(module));
        when(lessonRepository.findByCourseId(id)).thenReturn(List.of(
            VirtualLesson.create(module.getId(), id, "L1", "d", null, 10, false, 0)
        ));

        assertThatThrownBy(() -> useCase.publish(id.toString(), ownerId, false))
            .isInstanceOf(CourseNotPublishableException.class);
    }

    @Test
    void publishes_a_course_with_at_least_one_module_and_one_complete_lesson() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        VirtualModule module = VirtualModule.create(id, "Módulo 1", 0);
        when(courseRepository.findById(id)).thenReturn(Optional.of(draft(id, ownerId)));
        when(moduleRepository.findByCourseId(id)).thenReturn(List.of(module));
        when(lessonRepository.findByCourseId(id)).thenReturn(List.of(
            VirtualLesson.create(module.getId(), id, "L1", "d", "bunny-123", 10, false, 0)
        ));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VirtualCourseManagementResult result = useCase.publish(id.toString(), ownerId, false);

        assertThat(result.status()).isEqualTo("PUBLISHED");
    }

    @Test
    void ignores_a_lesson_belonging_to_a_module_not_of_this_course() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        VirtualModule module = VirtualModule.create(id, "Módulo 1", 0);
        ModuleId foreignModuleId = ModuleId.generate();
        when(courseRepository.findById(id)).thenReturn(Optional.of(draft(id, ownerId)));
        when(moduleRepository.findByCourseId(id)).thenReturn(List.of(module));
        when(lessonRepository.findByCourseId(id)).thenReturn(List.of(
            VirtualLesson.create(foreignModuleId, id, "L1", "d", "bunny-123", 10, false, 0)
        ));

        assertThatThrownBy(() -> useCase.publish(id.toString(), ownerId, false))
            .isInstanceOf(CourseNotPublishableException.class);
    }
}
