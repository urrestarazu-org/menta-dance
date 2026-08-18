package com.menta.physical.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.menta.physical.application.port.in.CreatePhysicalCourseUseCase;
import com.menta.physical.application.port.in.ListManagedPhysicalCoursesUseCase;
import com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort;
import com.menta.physical.application.port.in.UpdatePhysicalCourseUseCase;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.application.usecase.CreatePhysicalCourseUseCaseImpl;
import com.menta.physical.application.usecase.ListManagedPhysicalCoursesUseCaseImpl;
import com.menta.physical.application.usecase.PhysicalCourseAvailabilityPortImpl;
import com.menta.physical.application.usecase.UpdatePhysicalCourseUseCaseImpl;
import org.junit.jupiter.api.Test;

class PhysicalConfigurationTest {

    private final PhysicalConfiguration configuration = new PhysicalConfiguration();

    @Test
    void wires_the_availability_port_bean_with_the_given_repositories() {
        PhysicalCourseRepository courseRepository = mock(PhysicalCourseRepository.class);
        PhysicalSessionRepository sessionRepository = mock(PhysicalSessionRepository.class);

        PhysicalCourseAvailabilityPort port =
            configuration.physicalCourseAvailabilityPort(courseRepository, sessionRepository);

        assertThat(port).isInstanceOf(PhysicalCourseAvailabilityPortImpl.class);
    }

    @Test
    void wires_the_create_course_use_case_bean() {
        CreatePhysicalCourseUseCase useCase =
            configuration.createPhysicalCourseUseCase(mock(PhysicalCourseRepository.class));

        assertThat(useCase).isInstanceOf(CreatePhysicalCourseUseCaseImpl.class);
    }

    @Test
    void wires_the_list_managed_courses_use_case_bean() {
        ListManagedPhysicalCoursesUseCase useCase =
            configuration.listManagedPhysicalCoursesUseCase(mock(PhysicalCourseRepository.class));

        assertThat(useCase).isInstanceOf(ListManagedPhysicalCoursesUseCaseImpl.class);
    }

    @Test
    void wires_the_update_course_use_case_bean() {
        UpdatePhysicalCourseUseCase useCase = configuration.updatePhysicalCourseUseCase(
            mock(PhysicalCourseRepository.class), mock(PhysicalSessionRepository.class)
        );

        assertThat(useCase).isInstanceOf(UpdatePhysicalCourseUseCaseImpl.class);
    }
}
