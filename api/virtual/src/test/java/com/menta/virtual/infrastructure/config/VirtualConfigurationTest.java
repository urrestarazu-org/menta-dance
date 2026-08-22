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
import com.menta.virtual.application.usecase.VirtualCourseCatalogPortImpl;
import com.menta.virtual.infrastructure.transaction.TransactionalCreateVirtualCourseUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalCreateVirtualLessonUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalCreateVirtualModuleUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalDeleteVirtualCourseUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalPublishVirtualCourseUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalReorderVirtualModulesUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalUnpublishVirtualCourseUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalUpdateVirtualCourseUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalUpdateVirtualLessonUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalUpdateVirtualModuleUseCase;
import org.junit.jupiter.api.Test;

class VirtualConfigurationTest {

    private final VirtualConfiguration configuration = new VirtualConfiguration();
    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualModuleRepository moduleRepository = mock(VirtualModuleRepository.class);
    private final VirtualLessonRepository lessonRepository = mock(VirtualLessonRepository.class);
    private final VirtualCourseAuditRepository auditRepository = mock(VirtualCourseAuditRepository.class);

    @Test
    void wires_the_catalog_port_bean_with_the_given_repositories() {
        // #47: the detail read needs module + lesson repositories alongside
        // the course repository; the bean must wire all three.
        VirtualCourseCatalogPort port = configuration.virtualCourseCatalogPort(
            courseRepository, moduleRepository, lessonRepository
        );

        assertThat(port).isInstanceOf(VirtualCourseCatalogPortImpl.class);
    }

    @Test
    void wires_the_create_course_use_case_bean_transactionally() {
        CreateVirtualCourseUseCase useCase =
            configuration.createVirtualCourseUseCase(courseRepository, auditRepository);

        assertThat(useCase).isInstanceOf(TransactionalCreateVirtualCourseUseCase.class);
    }

    @Test
    void wires_the_list_managed_courses_use_case_bean() {
        ListManagedVirtualCoursesUseCase useCase = configuration.listManagedVirtualCoursesUseCase(courseRepository);

        assertThat(useCase).isNotNull();
    }

    @Test
    void wires_the_update_course_use_case_bean_transactionally() {
        UpdateVirtualCourseUseCase useCase =
            configuration.updateVirtualCourseUseCase(courseRepository, auditRepository);

        assertThat(useCase).isInstanceOf(TransactionalUpdateVirtualCourseUseCase.class);
    }

    @Test
    void wires_the_delete_course_use_case_bean_transactionally() {
        DeleteVirtualCourseUseCase useCase = configuration.deleteVirtualCourseUseCase(
            courseRepository, moduleRepository, lessonRepository, auditRepository
        );

        assertThat(useCase).isInstanceOf(TransactionalDeleteVirtualCourseUseCase.class);
    }

    @Test
    void wires_the_publish_course_use_case_bean_transactionally() {
        PublishVirtualCourseUseCase useCase = configuration.publishVirtualCourseUseCase(
            courseRepository, moduleRepository, lessonRepository, auditRepository
        );

        assertThat(useCase).isInstanceOf(TransactionalPublishVirtualCourseUseCase.class);
    }

    @Test
    void wires_the_unpublish_course_use_case_bean_transactionally() {
        UnpublishVirtualCourseUseCase useCase =
            configuration.unpublishVirtualCourseUseCase(courseRepository, auditRepository);

        assertThat(useCase).isInstanceOf(TransactionalUnpublishVirtualCourseUseCase.class);
    }

    @Test
    void wires_the_create_module_use_case_bean_transactionally() {
        CreateVirtualModuleUseCase useCase =
            configuration.createVirtualModuleUseCase(courseRepository, moduleRepository, auditRepository);

        assertThat(useCase).isInstanceOf(TransactionalCreateVirtualModuleUseCase.class);
    }

    @Test
    void wires_the_update_module_use_case_bean_transactionally() {
        UpdateVirtualModuleUseCase useCase =
            configuration.updateVirtualModuleUseCase(moduleRepository, courseRepository, auditRepository);

        assertThat(useCase).isInstanceOf(TransactionalUpdateVirtualModuleUseCase.class);
    }

    @Test
    void wires_the_create_lesson_use_case_bean_transactionally() {
        CreateVirtualLessonUseCase useCase = configuration.createVirtualLessonUseCase(
            moduleRepository, courseRepository, lessonRepository, auditRepository
        );

        assertThat(useCase).isInstanceOf(TransactionalCreateVirtualLessonUseCase.class);
    }

    @Test
    void wires_the_update_lesson_use_case_bean_transactionally() {
        UpdateVirtualLessonUseCase useCase =
            configuration.updateVirtualLessonUseCase(lessonRepository, courseRepository, auditRepository);

        assertThat(useCase).isInstanceOf(TransactionalUpdateVirtualLessonUseCase.class);
    }

    @Test
    void wires_the_reorder_modules_use_case_bean_transactionally() {
        ReorderVirtualModulesUseCase useCase =
            configuration.reorderVirtualModulesUseCase(courseRepository, moduleRepository, auditRepository);

        assertThat(useCase).isInstanceOf(TransactionalReorderVirtualModulesUseCase.class);
    }
}
