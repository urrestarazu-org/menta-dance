package com.menta.virtual.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.menta.virtual.application.port.in.CompleteLessonUseCase;
import com.menta.virtual.application.port.in.CreateVirtualCourseUseCase;
import com.menta.virtual.application.port.in.CreateVirtualLessonUseCase;
import com.menta.virtual.application.port.in.CreateVirtualModuleUseCase;
import com.menta.virtual.application.port.in.DeleteVirtualCourseUseCase;
import com.menta.virtual.application.port.in.GetLessonProgressUseCase;
import com.menta.virtual.application.port.in.GetPublicLessonStreamUseCase;
import com.menta.virtual.application.port.in.GetPublicLessonUseCase;
import com.menta.virtual.application.port.in.ListManagedVirtualCoursesUseCase;
import com.menta.virtual.application.port.in.PublishVirtualCourseUseCase;
import com.menta.virtual.application.port.in.ReorderVirtualModulesUseCase;
import com.menta.virtual.application.port.in.SaveLessonProgressUseCase;
import com.menta.virtual.application.port.in.UnpublishVirtualCourseUseCase;
import com.menta.virtual.application.port.in.UpdateVirtualCourseUseCase;
import com.menta.virtual.application.port.in.UpdateVirtualLessonUseCase;
import com.menta.virtual.application.port.in.UpdateVirtualModuleUseCase;
import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import com.menta.virtual.application.port.out.BunnyNetSignatureService;
import com.menta.virtual.application.port.out.Clock;
import com.menta.virtual.application.port.out.LessonProgressRepository;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.shared.billing.VirtualCourseEntitlementPort;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.application.usecase.GetLessonProgressUseCaseImpl;
import com.menta.virtual.application.usecase.GetPublicLessonStreamUseCaseImpl;
import com.menta.virtual.application.usecase.GetPublicLessonUseCaseImpl;
import com.menta.virtual.application.usecase.LessonAccessPolicy;
import com.menta.virtual.application.usecase.VirtualCourseCatalogPortImpl;
import com.menta.virtual.infrastructure.cdn.BunnyNetProperties;
import com.menta.virtual.infrastructure.cdn.StringFormatBunnyNetSignatureService;
import com.menta.virtual.infrastructure.cdn.local.LocalBunnyNetSignatureService;
import com.menta.virtual.infrastructure.transaction.RetryOnDuplicateKeyCompleteLessonUseCase;
import com.menta.virtual.infrastructure.transaction.RetryOnDuplicateKeySaveLessonProgressUseCase;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class VirtualConfigurationTest {

    private final VirtualConfiguration configuration = new VirtualConfiguration();
    private final VirtualCourseRepository courseRepository = mock(VirtualCourseRepository.class);
    private final VirtualModuleRepository moduleRepository = mock(VirtualModuleRepository.class);
    private final VirtualLessonRepository lessonRepository = mock(VirtualLessonRepository.class);
    private final VirtualCourseAuditRepository auditRepository = mock(VirtualCourseAuditRepository.class);
    private final VirtualCourseEntitlementPort entitlementPort = mock(VirtualCourseEntitlementPort.class);

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

    /**
     * US-VIRTUAL-003 (#48): the public-lesson read bean keeps its three
     * repository collaborators AND the cross-module entitlement port —
     * if a refactor drops an argument, this test catches it before
     * Spring tries to wire a missing bean at startup.
     */
    @Test
    void wires_the_get_public_lesson_use_case_bean_with_all_four_collaborators() {
        GetPublicLessonUseCase useCase = configuration.getPublicLessonUseCase(
            lessonRepository, moduleRepository, courseRepository, new LessonAccessPolicy(entitlementPort)
        );

        assertThat(useCase).isInstanceOf(GetPublicLessonUseCaseImpl.class);
    }

    /**
     * US-VIRTUAL-004: the stream bean takes four collaborators — the
     * lesson repository, the cross-module entitlement port, the CDN
     * signature service, and the application's {@link Clock}. The
     * signature service bean is split by profile (ADR-0040, issue #129):
     * this test drives the {@code !e2e-bunny-net} (default/production)
     * branch directly to make sure the cable chain goes all the way
     * through the lambda.
     */
    @Test
    void wires_the_get_public_lesson_stream_use_case_bean_with_all_four_collaborators() {
        BunnyNetProperties properties = new BunnyNetProperties();
        properties.setPullZoneHostname("vz-test.b-cdn.net");
        properties.setVideoLibraryId("9999");
        BunnyNetSignatureService signatureService = configuration.defaultBunnyNetSignatureService(properties);
        Clock clock = configuration.clock();

        GetPublicLessonStreamUseCase useCase = configuration.getPublicLessonStreamUseCase(
            lessonRepository, moduleRepository, new LessonAccessPolicy(entitlementPort), signatureService, clock
        );

        assertThat(useCase).isInstanceOf(GetPublicLessonStreamUseCaseImpl.class);
        // Beam smoke test: prove the service actually reads BunnyNetProperties.
        assertThat(signatureService).isInstanceOf(StringFormatBunnyNetSignatureService.class);
        assertThat(signatureService.generateSignedUrl("vid", 0L))
            .isEqualTo("vz-test.b-cdn.net/9999/vid");
    }

    @Test
    void clock_bean_point_in_time_produces_a_non_null_instant() {
        Clock clock = configuration.clock();

        assertThat(clock.now()).isNotNull();
    }

    @Test
    void wires_the_get_lesson_progress_use_case_bean() {
        LessonProgressRepository progressRepository = mock(LessonProgressRepository.class);

        GetLessonProgressUseCase useCase = configuration.getLessonProgressUseCase(
            lessonRepository, moduleRepository, progressRepository, new LessonAccessPolicy(entitlementPort)
        );

        assertThat(useCase).isInstanceOf(GetLessonProgressUseCaseImpl.class);
    }

    @Test
    void wires_the_save_lesson_progress_use_case_bean_with_retry_and_transaction() {
        LessonProgressRepository progressRepository = mock(LessonProgressRepository.class);

        SaveLessonProgressUseCase useCase = configuration.saveLessonProgressUseCase(
            lessonRepository, moduleRepository, progressRepository, new LessonAccessPolicy(entitlementPort),
            configuration.clock()
        );

        assertThat(useCase).isInstanceOf(RetryOnDuplicateKeySaveLessonProgressUseCase.class);
    }

    @Test
    void wires_the_complete_lesson_use_case_bean_with_retry_and_transaction() {
        LessonProgressRepository progressRepository = mock(LessonProgressRepository.class);

        CompleteLessonUseCase useCase = configuration.completeLessonUseCase(
            lessonRepository, moduleRepository, progressRepository, new LessonAccessPolicy(entitlementPort),
            configuration.clock()
        );

        assertThat(useCase).isInstanceOf(RetryOnDuplicateKeyCompleteLessonUseCase.class);
    }

    /**
     * ADR-0040 / issue #129 — profile-guarded, mutually exclusive adapter
     * selection. These tests boot a real Spring context (mirrors {@code
     * SecurityConfigTest}'s pattern) because the requirement is about bean
     * RESOLUTION under active profiles, not just direct method calls: a
     * missing/duplicate {@code @Profile} would only surface through the
     * container.
     */
    @Nested
    class BunnyNetAdapterProfileSelection {

        private AnnotationConfigApplicationContext context;

        @AfterEach
        void closeContext() {
            if (context != null) {
                context.close();
            }
        }

        @Test
        void e2e_bunny_net_profile_resolves_to_the_local_adapter_as_the_sole_candidate() {
            context = bootContext("e2e-bunny-net");

            assertThat(context.getBeanNamesForType(BunnyNetSignatureService.class)).hasSize(1);
            assertThat(context.getBean(BunnyNetSignatureService.class))
                .isInstanceOf(LocalBunnyNetSignatureService.class);
        }

        @Test
        void default_profiles_resolve_to_the_real_adapter_as_the_sole_candidate() {
            context = bootContext();

            assertThat(context.getBeanNamesForType(BunnyNetSignatureService.class)).hasSize(1);
            assertThat(context.getBean(BunnyNetSignatureService.class))
                .isInstanceOf(StringFormatBunnyNetSignatureService.class);
        }

        @Test
        void e2e_bunny_net_combined_with_prod_fails_the_context_refresh() {
            assertThatThrownBy(() -> bootContext("e2e-bunny-net", "prod"))
                .isInstanceOf(BeanCreationException.class)
                .hasStackTraceContaining("SECURITY");
        }

        @Test
        void e2e_bunny_net_combined_with_production_fails_the_context_refresh() {
            assertThatThrownBy(() -> bootContext("production", "e2e-bunny-net"))
                .isInstanceOf(BeanCreationException.class)
                .hasStackTraceContaining("SECURITY");
        }

        @Test
        void e2e_bunny_net_combined_with_staging_fails_the_context_refresh() {
            assertThatThrownBy(() -> bootContext("e2e-bunny-net", "staging"))
                .isInstanceOf(BeanCreationException.class)
                .hasStackTraceContaining("SECURITY");
        }

        private AnnotationConfigApplicationContext bootContext(String... profiles) {
            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
            ctx.getEnvironment().setActiveProfiles(profiles);
            ctx.register(VirtualConfiguration.class, TestSupportConfig.class);
            ctx.refresh();
            return ctx;
        }

        @Configuration
        static class TestSupportConfig {

            @Bean
            VirtualCourseRepository virtualCourseRepository() {
                return mock(VirtualCourseRepository.class);
            }

            @Bean
            VirtualModuleRepository virtualModuleRepository() {
                return mock(VirtualModuleRepository.class);
            }

            @Bean
            VirtualLessonRepository virtualLessonRepository() {
                return mock(VirtualLessonRepository.class);
            }

            @Bean
            VirtualCourseAuditRepository virtualCourseAuditRepository() {
                return mock(VirtualCourseAuditRepository.class);
            }

            @Bean
            VirtualCourseEntitlementPort virtualCourseEntitlementPort() {
                return mock(VirtualCourseEntitlementPort.class);
            }

            @Bean
            LessonProgressRepository lessonProgressRepository() {
                return mock(LessonProgressRepository.class);
            }
        }
    }
}
