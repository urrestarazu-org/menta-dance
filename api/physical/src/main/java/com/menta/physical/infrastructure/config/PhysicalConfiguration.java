package com.menta.physical.infrastructure.config;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Physical's use cases. Adapters are {@code @Component}-scanned; the
 * use cases are plain Java classes composed here, mirroring {@code
 * VirtualConfiguration}'s rationale: no implicit {@code @Autowired} on
 * use-case classes.
 */
@Configuration
public class PhysicalConfiguration {

    @Bean
    public PhysicalCourseAvailabilityPort physicalCourseAvailabilityPort(
        PhysicalCourseRepository courseRepository, PhysicalSessionRepository sessionRepository
    ) {
        return new PhysicalCourseAvailabilityPortImpl(courseRepository, sessionRepository);
    }

    @Bean
    public CreatePhysicalCourseUseCase createPhysicalCourseUseCase(PhysicalCourseRepository courseRepository) {
        return new CreatePhysicalCourseUseCaseImpl(courseRepository);
    }

    @Bean
    public ListManagedPhysicalCoursesUseCase listManagedPhysicalCoursesUseCase(
        PhysicalCourseRepository courseRepository
    ) {
        return new ListManagedPhysicalCoursesUseCaseImpl(courseRepository);
    }

    @Bean
    public UpdatePhysicalCourseUseCase updatePhysicalCourseUseCase(
        PhysicalCourseRepository courseRepository, PhysicalSessionRepository sessionRepository
    ) {
        return new UpdatePhysicalCourseUseCaseImpl(courseRepository, sessionRepository);
    }
}
