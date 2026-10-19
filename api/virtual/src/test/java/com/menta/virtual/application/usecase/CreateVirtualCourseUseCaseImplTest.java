package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.CreateVirtualCourseCommand;
import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.domain.exception.ProfessorMismatchException;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateVirtualCourseUseCaseImplTest {

    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualCourseAuditRepository auditRepository = mock(VirtualCourseAuditRepository.class);
    private final CreateVirtualCourseUseCaseImpl useCase =
        new CreateVirtualCourseUseCaseImpl(courseRepository, auditRepository);

    private static CreateVirtualCourseCommand command(UUID professorId) {
        return new CreateVirtualCourseCommand(
            "Tango Básico", "corta", "larga", professorId, "https://cdn/img.jpg", "tango", "BEGINNER"
        );
    }

    @Test
    void instructor_creating_without_a_professor_id_defaults_to_themself() {
        UUID instructorId = UUID.randomUUID();
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.create(command(null), instructorId, false);

        ArgumentCaptor<VirtualCourse> captor = ArgumentCaptor.forClass(VirtualCourse.class);
        verify(courseRepository).save(captor.capture());
        assertThat(captor.getValue().getProfessorId()).isEqualTo(instructorId);
        assertThat(captor.getValue().getStatus().name()).isEqualTo("DRAFT");
    }

    @Test
    void instructor_supplying_a_different_professor_id_is_rejected() {
        UUID instructorId = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.create(command(someoneElse), instructorId, false))
            .isInstanceOf(ProfessorMismatchException.class);
    }

    @Test
    void admin_can_assign_an_arbitrary_professor_id() {
        UUID adminId = UUID.randomUUID();
        UUID targetProfessor = UUID.randomUUID();
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        VirtualCourseManagementResult result = useCase.create(command(targetProfessor), adminId, true);

        assertThat(result.professorId()).isEqualTo(targetProfessor.toString());
    }

    @Test
    void appends_a_create_audit_entry() {
        UUID instructorId = UUID.randomUUID();
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.create(command(null), instructorId, false);

        verify(auditRepository).append(any(), org.mockito.ArgumentMatchers.eq(instructorId),
            org.mockito.ArgumentMatchers.eq("CREATE_COURSE"), org.mockito.ArgumentMatchers.isNull(), any());
    }
}
