package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.CreatePhysicalCourseCommand;
import com.menta.physical.application.dto.PhysicalCourseManagementResult;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.domain.exception.ProfessorMismatchException;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreatePhysicalCourseUseCaseImplTest {

    private final PhysicalCourseRepository courseRepository = mock(PhysicalCourseRepository.class);
    private final CreatePhysicalCourseUseCaseImpl useCase = new CreatePhysicalCourseUseCaseImpl(courseRepository);

    private static CreatePhysicalCourseCommand command(UUID professorId) {
        return new CreatePhysicalCourseCommand(
            "Salsa inicial", "desc", professorId, "María García", DayOfWeek.WEDNESDAY,
            LocalTime.of(20, 0), 60, PhysicalCourseLevel.INTERMEDIATE, 20
        );
    }

    @Test
    void instructor_creating_without_a_professor_id_defaults_to_themself() {
        UUID instructorId = UUID.randomUUID();
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.create(command(null), instructorId, false);

        ArgumentCaptor<PhysicalCourse> captor = ArgumentCaptor.forClass(PhysicalCourse.class);
        verify(courseRepository).save(captor.capture());
        assertThat(captor.getValue().getProfessorId()).isEqualTo(instructorId);
    }

    @Test
    void instructor_supplying_their_own_id_is_accepted() {
        UUID instructorId = UUID.randomUUID();
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.create(command(instructorId), instructorId, false);

        ArgumentCaptor<PhysicalCourse> captor = ArgumentCaptor.forClass(PhysicalCourse.class);
        verify(courseRepository).save(captor.capture());
        assertThat(captor.getValue().getProfessorId()).isEqualTo(instructorId);
    }

    @Test
    void instructor_supplying_a_different_professor_id_is_rejected() {
        UUID instructorId = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();

        assertThatThrownBy(() -> useCase.create(command(someoneElse), instructorId, false))
            .isInstanceOf(ProfessorMismatchException.class);
    }

    @Test
    void admin_defaults_to_themself_when_no_professor_id_supplied() {
        UUID adminId = UUID.randomUUID();
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.create(command(null), adminId, true);

        ArgumentCaptor<PhysicalCourse> captor = ArgumentCaptor.forClass(PhysicalCourse.class);
        verify(courseRepository).save(captor.capture());
        assertThat(captor.getValue().getProfessorId()).isEqualTo(adminId);
    }

    @Test
    void admin_can_assign_an_arbitrary_professor_id() {
        UUID adminId = UUID.randomUUID();
        UUID targetProfessor = UUID.randomUUID();
        when(courseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PhysicalCourseManagementResult result = useCase.create(command(targetProfessor), adminId, true);

        assertThat(result.professorId()).isEqualTo(targetProfessor.toString());
    }
}
