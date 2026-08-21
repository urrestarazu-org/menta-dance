package com.menta.physical.infrastructure.config;

import com.menta.physical.application.port.in.BatchCreatePhysicalSessionsUseCase;
import com.menta.physical.application.port.in.CreatePhysicalCourseUseCase;
import com.menta.physical.application.port.in.CreatePhysicalSessionUseCase;
import com.menta.physical.application.port.in.ListManagedPhysicalCoursesUseCase;
import com.menta.physical.application.port.in.ListManagedPhysicalSessionsUseCase;
import com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort;
import com.menta.physical.application.port.in.PhysicalCourseOwnershipPort;
import com.menta.physical.application.port.in.UpdatePhysicalCourseUseCase;
import com.menta.physical.application.port.in.UpdatePhysicalSessionUseCase;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.application.usecase.BatchCreatePhysicalSessionsUseCaseImpl;
import com.menta.physical.application.usecase.CreatePhysicalCourseUseCaseImpl;
import com.menta.physical.application.usecase.CreatePhysicalSessionUseCaseImpl;
import com.menta.physical.application.usecase.ListManagedPhysicalCoursesUseCaseImpl;
import com.menta.physical.application.usecase.ListManagedPhysicalSessionsUseCaseImpl;
import com.menta.physical.application.usecase.PhysicalCourseAvailabilityPortImpl;
import com.menta.physical.application.usecase.PhysicalCourseOwnershipPortImpl;
import com.menta.physical.application.usecase.UpdatePhysicalCourseUseCaseImpl;
import com.menta.physical.application.usecase.UpdatePhysicalSessionUseCaseImpl;
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
    public PhysicalCourseOwnershipPort physicalCourseOwnershipPort(PhysicalCourseRepository courseRepository) {
        return new PhysicalCourseOwnershipPortImpl(courseRepository);
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

    @Bean
    public CreatePhysicalSessionUseCase createPhysicalSessionUseCase(
        PhysicalCourseRepository courseRepository, PhysicalSessionRepository sessionRepository
    ) {
        return new CreatePhysicalSessionUseCaseImpl(courseRepository, sessionRepository);
    }

    @Bean
    public BatchCreatePhysicalSessionsUseCase batchCreatePhysicalSessionsUseCase(
        PhysicalCourseRepository courseRepository, PhysicalSessionRepository sessionRepository
    ) {
        return new BatchCreatePhysicalSessionsUseCaseImpl(courseRepository, sessionRepository);
    }

    @Bean
    public ListManagedPhysicalSessionsUseCase listManagedPhysicalSessionsUseCase(
        PhysicalCourseRepository courseRepository, PhysicalSessionRepository sessionRepository
    ) {
        return new ListManagedPhysicalSessionsUseCaseImpl(courseRepository, sessionRepository);
    }

    @Bean
    public UpdatePhysicalSessionUseCase updatePhysicalSessionUseCase(
        PhysicalCourseRepository courseRepository, PhysicalSessionRepository sessionRepository
    ) {
        return new UpdatePhysicalSessionUseCaseImpl(courseRepository, sessionRepository);
    }
}
