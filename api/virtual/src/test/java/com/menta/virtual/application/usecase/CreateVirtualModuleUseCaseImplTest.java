package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.CreateVirtualModuleCommand;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.CourseNotOwnedException;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateVirtualModuleUseCaseImplTest {

    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualModuleRepository moduleRepository = mock(VirtualModuleRepository.class);
    private final VirtualCourseAuditRepository auditRepository = mock(VirtualCourseAuditRepository.class);
    private final CreateVirtualModuleUseCaseImpl useCase =
        new CreateVirtualModuleUseCaseImpl(courseRepository, moduleRepository, auditRepository);

    private static VirtualCourse course(CourseId id, UUID professorId) {
        return new VirtualCourse(
            id, "t", "s", "d", professorId, "i", CourseCategory.of("tango"), CourseLevel.BEGINNER, false,
            CourseStatus.DRAFT, 0, 0, 0
        );
    }

    @Test
    void instructor_creating_a_module_for_a_course_they_do_not_own_is_rejected() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId)));

        assertThatThrownBy(() -> useCase.create(
            id.toString(), new CreateVirtualModuleCommand("Módulo 1", Optional.empty()), UUID.randomUUID(), false
        )).isInstanceOf(CourseNotOwnedException.class);
    }

    @Test
    void uses_the_requested_order_when_present() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId)));
        when(moduleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VirtualModuleManagementResult result = useCase.create(
            id.toString(), new CreateVirtualModuleCommand("Módulo 1", Optional.of(5)), ownerId, false
        );

        assertThat(result.order()).isEqualTo(5);
    }

    @Test
    void appends_at_the_end_when_order_is_absent() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId)));
        when(moduleRepository.countByCourseId(id)).thenReturn(2);
        when(moduleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VirtualModuleManagementResult result = useCase.create(
            id.toString(), new CreateVirtualModuleCommand("Módulo 3", Optional.empty()), ownerId, false
        );

        assertThat(result.order()).isEqualTo(2);
    }
}
