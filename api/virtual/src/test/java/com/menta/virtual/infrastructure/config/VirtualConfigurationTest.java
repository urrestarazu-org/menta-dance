package com.menta.virtual.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
import org.junit.jupiter.api.Test;

class VirtualConfigurationTest {

    private final VirtualConfiguration configuration = new VirtualConfiguration();
    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualModuleRepository moduleRepository = mock(VirtualModuleRepository.class);
    private final VirtualLessonRepository lessonRepository = mock(VirtualLessonRepository.class);
    private final VirtualCourseAuditRepository auditRepository = mock(VirtualCourseAuditRepository.class);

    @Test
    void wires_the_catalog_port_bean_with_the_given_repository() {
        VirtualCourseCatalogPort port = configuration.virtualCourseCatalogPort(courseRepository);

        assertThat(port).isInstanceOf(VirtualCourseCatalogPortImpl.class);
    }

    @Test
    void wires_the_create_course_use_case_bean() {
        CreateVirtualCourseUseCase useCase =
            configuration.createVirtualCourseUseCase(courseRepository, auditRepository);

        assertThat(useCase).isInstanceOf(CreateVirtualCourseUseCaseImpl.class);
    }

    @Test
    void wires_the_list_managed_courses_use_case_bean() {
        ListManagedVirtualCoursesUseCase useCase = configuration.listManagedVirtualCoursesUseCase(courseRepository);

        assertThat(useCase).isInstanceOf(ListManagedVirtualCoursesUseCaseImpl.class);
    }

    @Test
    void wires_the_update_course_use_case_bean() {
        UpdateVirtualCourseUseCase useCase =
            configuration.updateVirtualCourseUseCase(courseRepository, auditRepository);

        assertThat(useCase).isInstanceOf(UpdateVirtualCourseUseCaseImpl.class);
    }

    @Test
    void wires_the_delete_course_use_case_bean() {
        DeleteVirtualCourseUseCase useCase =
            configuration.deleteVirtualCourseUseCase(courseRepository, auditRepository);

        assertThat(useCase).isInstanceOf(DeleteVirtualCourseUseCaseImpl.class);
    }

    @Test
    void wires_the_publish_course_use_case_bean() {
        PublishVirtualCourseUseCase useCase = configuration.publishVirtualCourseUseCase(
            courseRepository, moduleRepository, lessonRepository, auditRepository
        );

        assertThat(useCase).isInstanceOf(PublishVirtualCourseUseCaseImpl.class);
    }

    @Test
    void wires_the_unpublish_course_use_case_bean() {
        UnpublishVirtualCourseUseCase useCase =
            configuration.unpublishVirtualCourseUseCase(courseRepository, auditRepository);

        assertThat(useCase).isInstanceOf(UnpublishVirtualCourseUseCaseImpl.class);
    }

    @Test
    void wires_the_create_module_use_case_bean() {
        CreateVirtualModuleUseCase useCase =
            configuration.createVirtualModuleUseCase(courseRepository, moduleRepository, auditRepository);

        assertThat(useCase).isInstanceOf(CreateVirtualModuleUseCaseImpl.class);
    }

    @Test
    void wires_the_update_module_use_case_bean() {
        UpdateVirtualModuleUseCase useCase =
            configuration.updateVirtualModuleUseCase(moduleRepository, courseRepository, auditRepository);

        assertThat(useCase).isInstanceOf(UpdateVirtualModuleUseCaseImpl.class);
    }

    @Test
    void wires_the_create_lesson_use_case_bean() {
        CreateVirtualLessonUseCase useCase = configuration.createVirtualLessonUseCase(
            moduleRepository, courseRepository, lessonRepository, auditRepository
        );

        assertThat(useCase).isInstanceOf(CreateVirtualLessonUseCaseImpl.class);
    }

    @Test
    void wires_the_update_lesson_use_case_bean() {
        UpdateVirtualLessonUseCase useCase =
            configuration.updateVirtualLessonUseCase(lessonRepository, courseRepository, auditRepository);

        assertThat(useCase).isInstanceOf(UpdateVirtualLessonUseCaseImpl.class);
    }

    @Test
    void wires_the_reorder_modules_use_case_bean() {
        ReorderVirtualModulesUseCase useCase =
            configuration.reorderVirtualModulesUseCase(courseRepository, moduleRepository, auditRepository);

        assertThat(useCase).isInstanceOf(ReorderVirtualModulesUseCaseImpl.class);
    }
}
