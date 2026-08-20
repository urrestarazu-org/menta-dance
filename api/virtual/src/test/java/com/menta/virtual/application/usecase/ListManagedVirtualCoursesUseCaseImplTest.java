package com.menta.virtual.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.domain.model.CourseCategory;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.CourseLevel;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.VirtualCourse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListManagedVirtualCoursesUseCaseImplTest {

    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final ListManagedVirtualCoursesUseCaseImpl useCase =
        new ListManagedVirtualCoursesUseCaseImpl(courseRepository);

    private static VirtualCourse course(UUID professorId) {
        return new VirtualCourse(
            CourseId.generate(), "t", "s", "d", professorId, "i", CourseCategory.of("tango"),
            CourseLevel.BEGINNER, false, CourseStatus.DRAFT, 0, 0, 0
        );
    }

    @Test
    void admin_lists_every_course() {
        when(courseRepository.findAll()).thenReturn(List.of(course(UUID.randomUUID()), course(UUID.randomUUID())));

        List<VirtualCourseManagementResult> results = useCase.list(UUID.randomUUID(), true);

        assertThat(results).hasSize(2);
    }

    @Test
    void instructor_lists_only_their_own_courses() {
        UUID instructorId = UUID.randomUUID();
        when(courseRepository.findByProfessorId(instructorId)).thenReturn(List.of(course(instructorId)));

        useCase.list(instructorId, false);

        verify(courseRepository).findByProfessorId(instructorId);
    }
}
