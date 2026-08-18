package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.PhysicalCourseManagementResult;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.PhysicalCourse;
import com.menta.physical.domain.model.PhysicalCourseLevel;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListManagedPhysicalCoursesUseCaseImplTest {

    private final PhysicalCourseRepository courseRepository = mock(PhysicalCourseRepository.class);
    private final ListManagedPhysicalCoursesUseCaseImpl useCase =
        new ListManagedPhysicalCoursesUseCaseImpl(courseRepository);

    private static PhysicalCourse course(UUID professorId) {
        return new PhysicalCourse(
            com.menta.physical.domain.model.CourseId.generate(), "Salsa inicial", "desc", professorId,
            "María García", DayOfWeek.WEDNESDAY, LocalTime.of(20, 0), 60, PhysicalCourseLevel.INTERMEDIATE, 20,
            CourseStatus.ACTIVE
        );
    }

    @Test
    void admin_sees_every_course() {
        UUID adminId = UUID.randomUUID();
        when(courseRepository.findAll()).thenReturn(List.of(course(UUID.randomUUID())));

        List<PhysicalCourseManagementResult> result = useCase.list(adminId, true);

        assertThat(result).hasSize(1);
        verify(courseRepository).findAll();
        verifyNoMoreInteractions(courseRepository);
    }

    @Test
    void instructor_sees_only_their_own_courses() {
        UUID instructorId = UUID.randomUUID();
        when(courseRepository.findByProfessorId(instructorId)).thenReturn(List.of(course(instructorId)));

        List<PhysicalCourseManagementResult> result = useCase.list(instructorId, false);

        assertThat(result).hasSize(1);
        verify(courseRepository).findByProfessorId(instructorId);
        verifyNoMoreInteractions(courseRepository);
    }
}
