package com.menta.physical.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.application.usecase.PhysicalCourseAvailabilityPortImpl;
import org.junit.jupiter.api.Test;

class PhysicalConfigurationTest {

    @Test
    void wires_the_availability_port_bean_with_the_given_repositories() {
        PhysicalCourseRepository courseRepository = mock(PhysicalCourseRepository.class);
        PhysicalSessionRepository sessionRepository = mock(PhysicalSessionRepository.class);

        PhysicalCourseAvailabilityPort port =
            new PhysicalConfiguration().physicalCourseAvailabilityPort(courseRepository, sessionRepository);

        assertThat(port).isInstanceOf(PhysicalCourseAvailabilityPortImpl.class);
    }
}
