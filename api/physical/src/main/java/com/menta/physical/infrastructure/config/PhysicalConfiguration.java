package com.menta.physical.infrastructure.config;

import com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.application.usecase.PhysicalCourseAvailabilityPortImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link PhysicalCourseAvailabilityPort}. Adapters are
 * {@code @Component}-scanned; the use case is a plain Java class composed
 * here, mirroring {@code VirtualConfiguration}'s rationale: no implicit
 * {@code @Autowired} on use-case classes.
 */
@Configuration
public class PhysicalConfiguration {

    @Bean
    public PhysicalCourseAvailabilityPort physicalCourseAvailabilityPort(
        PhysicalCourseRepository courseRepository, PhysicalSessionRepository sessionRepository
    ) {
        return new PhysicalCourseAvailabilityPortImpl(courseRepository, sessionRepository);
    }
}
