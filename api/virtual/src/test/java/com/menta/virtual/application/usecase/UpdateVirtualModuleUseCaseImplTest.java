package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.UpdateVirtualModuleCommand;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.CourseNotOwnedException;
import com.menta.virtual.domain.exception.ModuleNotFoundException;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdateVirtualModuleUseCaseImplTest {

    private final VirtualModuleRepository moduleRepository = mock(VirtualModuleRepository.class);
    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualCourseAuditRepository auditRepository = mock(VirtualCourseAuditRepository.class);
    private final UpdateVirtualModuleUseCaseImpl useCase =
        new UpdateVirtualModuleUseCaseImpl(moduleRepository, courseRepository, auditRepository);

    private static VirtualCourse course(CourseId id, UUID professorId) {
        return new VirtualCourse(
            id, "t", "s", "d", professorId, "i", CourseCategory.of("tango"), CourseLevel.BEGINNER, false,
            CourseStatus.DRAFT, 0, 0, 0
        );
    }

    @Test
    void throws_when_the_module_does_not_exist() {
        ModuleId id = ModuleId.generate();
        when(moduleRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.update(
            id.toString(), new UpdateVirtualModuleCommand(Optional.empty(), Optional.empty()),
            UUID.randomUUID(), true
        )).isInstanceOf(ModuleNotFoundException.class);
    }

    @Test
    void instructor_editing_a_module_of_a_course_they_do_not_own_is_rejected() {
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        VirtualModule module = VirtualModule.create(courseId, "Módulo 1", 0);
        when(moduleRepository.findById(module.getId())).thenReturn(Optional.of(module));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));

        assertThatThrownBy(() -> useCase.update(
            module.getId().toString(), new UpdateVirtualModuleCommand(Optional.empty(), Optional.empty()),
            UUID.randomUUID(), false
        )).isInstanceOf(CourseNotOwnedException.class);
    }

    @Test
    void applies_only_the_fields_present_in_the_command() {
        CourseId courseId = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        VirtualModule module = VirtualModule.create(courseId, "Módulo 1", 0);
        when(moduleRepository.findById(module.getId())).thenReturn(Optional.of(module));
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, ownerId)));
        when(moduleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VirtualModuleManagementResult result = useCase.update(
            module.getId().toString(), new UpdateVirtualModuleCommand(Optional.of("Nuevo"), Optional.of(4)),
            ownerId, false
        );

        assertThat(result.title()).isEqualTo("Nuevo");
        assertThat(result.order()).isEqualTo(4);
    }
}
