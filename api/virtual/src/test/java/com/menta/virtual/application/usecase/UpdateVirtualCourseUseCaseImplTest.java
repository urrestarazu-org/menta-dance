package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.UpdateVirtualCourseCommand;
import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.domain.exception.CourseNotFoundException;
import com.menta.virtual.domain.exception.CourseNotOwnedException;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UpdateVirtualCourseUseCaseImplTest {

    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualCourseAuditRepository auditRepository = mock(VirtualCourseAuditRepository.class);
    private final UpdateVirtualCourseUseCaseImpl useCase =
        new UpdateVirtualCourseUseCaseImpl(courseRepository, auditRepository);

    private static VirtualCourse course(CourseId id, UUID professorId) {
        return new VirtualCourse(
            id, "t", "s", "d", professorId, "i", CourseCategory.of("tango"), CourseLevel.BEGINNER, false,
            CourseStatus.DRAFT, 0, 0, 0
        );
    }

    private static UpdateVirtualCourseCommand emptyCommand() {
        return new UpdateVirtualCourseCommand(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty()
        );
    }

    @Test
    void throws_when_the_course_does_not_exist() {
        CourseId id = CourseId.generate();
        when(courseRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.update(id.toString(), emptyCommand(), UUID.randomUUID(), true))
            .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void instructor_editing_a_course_they_do_not_own_is_rejected() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId)));

        assertThatThrownBy(() -> useCase.update(id.toString(), emptyCommand(), UUID.randomUUID(), false))
            .isInstanceOf(CourseNotOwnedException.class);
    }

    @Test
    void applies_only_the_fields_present_in_the_command() {
        CourseId id = CourseId.generate();
        UUID ownerId = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.of(course(id, ownerId)));
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateVirtualCourseCommand command = new UpdateVirtualCourseCommand(
            Optional.of("Nuevo título"), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.of(true)
        );
        VirtualCourseManagementResult result = useCase.update(id.toString(), command, ownerId, false);

        assertThat(result.title()).isEqualTo("Nuevo título");
        assertThat(result.premium()).isTrue();
        assertThat(result.shortDescription()).isEqualTo("s");
    }
}
