package com.menta.virtual.infrastructure.config;

import com.menta.virtual.application.port.in.CreateVirtualCourseUseCase;
import com.menta.virtual.application.port.in.CreateVirtualLessonUseCase;
import com.menta.virtual.application.port.in.CreateVirtualModuleUseCase;
import com.menta.virtual.application.port.in.DeleteVirtualCourseUseCase;
import com.menta.virtual.application.port.in.ListManagedVirtualCoursesUseCase;
import com.menta.virtual.application.port.in.PublishVirtualCourseUseCase;
import com.menta.virtual.application.port.in.ReorderVirtualModulesUseCase;
import com.menta.virtual.application.port.in.UnpublishVirtualCourseUseCase;
import com.menta.virtual.application.port.in.UpdateVirtualCourseUseCase;
import com.menta.virtual.application.port.in.UpdateVirtualLessonUseCase;
import com.menta.virtual.application.port.in.UpdateVirtualModuleUseCase;
import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.application.usecase.CreateVirtualCourseUseCaseImpl;
import com.menta.virtual.application.usecase.CreateVirtualLessonUseCaseImpl;
import com.menta.virtual.application.usecase.CreateVirtualModuleUseCaseImpl;
import com.menta.virtual.application.usecase.DeleteVirtualCourseUseCaseImpl;
import com.menta.virtual.application.usecase.ListManagedVirtualCoursesUseCaseImpl;
import com.menta.virtual.application.usecase.PublishVirtualCourseUseCaseImpl;
import com.menta.virtual.application.usecase.ReorderVirtualModulesUseCaseImpl;
import com.menta.virtual.application.usecase.UnpublishVirtualCourseUseCaseImpl;
import com.menta.virtual.application.usecase.UpdateVirtualCourseUseCaseImpl;
import com.menta.virtual.application.usecase.UpdateVirtualLessonUseCaseImpl;
import com.menta.virtual.application.usecase.UpdateVirtualModuleUseCaseImpl;
import com.menta.virtual.application.usecase.VirtualCourseCatalogPortImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Virtual's use cases. Adapters are {@code @Component}-scanned; the
 * use cases are plain Java classes composed here, mirroring {@code
 * AuthConfiguration}/{@code BillingConfiguration}'s rationale: no implicit
 * {@code @Autowired} on use-case classes.
 */
@Configuration
public class VirtualConfiguration {

    @Bean
    public VirtualCourseCatalogPort virtualCourseCatalogPort(VirtualCourseRepository virtualCourseRepository) {
        return new VirtualCourseCatalogPortImpl(virtualCourseRepository);
    }

    @Bean
    public CreateVirtualCourseUseCase createVirtualCourseUseCase(
        VirtualCourseRepository courseRepository, VirtualCourseAuditRepository auditRepository
    ) {
        return new CreateVirtualCourseUseCaseImpl(courseRepository, auditRepository);
    }

    @Bean
    public ListManagedVirtualCoursesUseCase listManagedVirtualCoursesUseCase(VirtualCourseRepository courseRepository) {
        return new ListManagedVirtualCoursesUseCaseImpl(courseRepository);
    }

    @Bean
    public UpdateVirtualCourseUseCase updateVirtualCourseUseCase(
        VirtualCourseRepository courseRepository, VirtualCourseAuditRepository auditRepository
    ) {
        return new UpdateVirtualCourseUseCaseImpl(courseRepository, auditRepository);
    }

    @Bean
    public DeleteVirtualCourseUseCase deleteVirtualCourseUseCase(
        VirtualCourseRepository courseRepository, VirtualCourseAuditRepository auditRepository
    ) {
        return new DeleteVirtualCourseUseCaseImpl(courseRepository, auditRepository);
    }

    @Bean
    public PublishVirtualCourseUseCase publishVirtualCourseUseCase(
        VirtualCourseRepository courseRepository, VirtualModuleRepository moduleRepository,
        VirtualLessonRepository lessonRepository, VirtualCourseAuditRepository auditRepository
    ) {
        return new PublishVirtualCourseUseCaseImpl(courseRepository, moduleRepository, lessonRepository, auditRepository);
    }

    @Bean
    public UnpublishVirtualCourseUseCase unpublishVirtualCourseUseCase(
        VirtualCourseRepository courseRepository, VirtualCourseAuditRepository auditRepository
    ) {
        return new UnpublishVirtualCourseUseCaseImpl(courseRepository, auditRepository);
    }

    @Bean
    public CreateVirtualModuleUseCase createVirtualModuleUseCase(
        VirtualCourseRepository courseRepository, VirtualModuleRepository moduleRepository,
        VirtualCourseAuditRepository auditRepository
    ) {
        return new CreateVirtualModuleUseCaseImpl(courseRepository, moduleRepository, auditRepository);
    }

    @Bean
    public UpdateVirtualModuleUseCase updateVirtualModuleUseCase(
        VirtualModuleRepository moduleRepository, VirtualCourseRepository courseRepository,
        VirtualCourseAuditRepository auditRepository
    ) {
        return new UpdateVirtualModuleUseCaseImpl(moduleRepository, courseRepository, auditRepository);
    }

    @Bean
    public CreateVirtualLessonUseCase createVirtualLessonUseCase(
        VirtualModuleRepository moduleRepository, VirtualCourseRepository courseRepository,
        VirtualLessonRepository lessonRepository, VirtualCourseAuditRepository auditRepository
    ) {
        return new CreateVirtualLessonUseCaseImpl(moduleRepository, courseRepository, lessonRepository, auditRepository);
    }

    @Bean
    public UpdateVirtualLessonUseCase updateVirtualLessonUseCase(
        VirtualLessonRepository lessonRepository, VirtualCourseRepository courseRepository,
        VirtualCourseAuditRepository auditRepository
    ) {
        return new UpdateVirtualLessonUseCaseImpl(lessonRepository, courseRepository, auditRepository);
    }

    @Bean
    public ReorderVirtualModulesUseCase reorderVirtualModulesUseCase(
        VirtualCourseRepository courseRepository, VirtualModuleRepository moduleRepository,
        VirtualCourseAuditRepository auditRepository
    ) {
        return new ReorderVirtualModulesUseCaseImpl(courseRepository, moduleRepository, auditRepository);
    }
}
