package com.menta.virtual.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import com.menta.shared.billing.VirtualCourseEntitlementPort;
import com.menta.shared.billing.VirtualCourseReferencePort;
import com.menta.virtual.application.dto.LibraryVideo;
import com.menta.virtual.application.dto.LibraryVideoStatus;
import com.menta.virtual.application.port.in.CompleteLessonUseCase;
import com.menta.virtual.application.port.in.CreateVirtualCourseUseCase;
import com.menta.virtual.application.port.in.CreateVirtualLessonUseCase;
import com.menta.virtual.application.port.in.CreateVirtualModuleUseCase;
import com.menta.virtual.application.port.in.DeleteVirtualCourseUseCase;
import com.menta.virtual.application.port.in.DeleteVirtualLessonUseCase;
import com.menta.virtual.application.port.in.GetCourseProgressUseCase;
import com.menta.virtual.application.port.in.GetLessonProgressUseCase;
import com.menta.virtual.application.port.in.GetLibraryVideoStreamUseCase;
import com.menta.virtual.application.port.in.GetPublicLessonStreamUseCase;
import com.menta.virtual.application.port.in.GetPublicLessonUseCase;
import com.menta.virtual.application.port.in.ListInProgressCoursesUseCase;
import com.menta.virtual.application.port.in.ListLibraryVideosUseCase;
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
import com.menta.virtual.application.port.out.VideoLibraryPort;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.application.port.out.VirtualVideoAuditRepository;
import com.menta.virtual.application.usecase.CourseProgressAccessPolicy;
import com.menta.virtual.application.usecase.GetCourseProgressUseCaseImpl;
import com.menta.virtual.application.usecase.GetLessonProgressUseCaseImpl;
import com.menta.virtual.application.usecase.GetLibraryVideoStreamUseCaseImpl;
import com.menta.virtual.application.usecase.GetPublicLessonStreamUseCaseImpl;
import com.menta.virtual.application.usecase.GetPublicLessonUseCaseImpl;
import com.menta.virtual.application.usecase.LessonAccessPolicy;
import com.menta.virtual.application.usecase.ListInProgressCoursesUseCaseImpl;
import com.menta.virtual.application.usecase.ListLibraryVideosUseCaseImpl;
import com.menta.virtual.application.usecase.VirtualCourseCatalogPortImpl;
import com.menta.virtual.infrastructure.cdn.BunnyNetProperties;
import com.menta.virtual.infrastructure.cdn.BunnyStreamVideoLibrary;
import com.menta.virtual.infrastructure.cdn.CapturedLogs;
import com.menta.virtual.infrastructure.cdn.ProbingBunnyNetSignatureService;
import com.menta.virtual.infrastructure.cdn.TokenAuthBunnyNetSignatureService;
import com.menta.virtual.infrastructure.cdn.local.InMemoryVideoLibrary;
import com.menta.virtual.infrastructure.cdn.local.LocalBunnyNetSignatureService;
import com.menta.virtual.infrastructure.transaction.RetryOnDuplicateKeyCompleteLessonUseCase;
import com.menta.virtual.infrastructure.transaction.RetryOnDuplicateKeySaveLessonProgressUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalCreateVirtualCourseUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalCreateVirtualLessonUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalCreateVirtualModuleUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalDeleteVirtualCourseUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalDeleteVirtualLessonUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalPublishVirtualCourseUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalReorderVirtualModulesUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalUnpublishVirtualCourseUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalUpdateVirtualCourseUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalUpdateVirtualLessonUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalUpdateVirtualModuleUseCase;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

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
            courseRepository, moduleRepository, lessonRepository, mock(LessonProgressRepository.class),
            mock(VirtualCourseReferencePort.class), auditRepository
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
    void wires_the_delete_lesson_use_case_bean_transactionally() {
        DeleteVirtualLessonUseCase useCase = configuration.deleteVirtualLessonUseCase(
            lessonRepository, courseRepository, mock(LessonProgressRepository.class), auditRepository
        );

        assertThat(useCase).isInstanceOf(TransactionalDeleteVirtualLessonUseCase.class);
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
     * signature service bean is selected by profile and property
     * (ADR-0040, amended by #751): this test drives the default
     * (production-like) branch directly to make sure the cable chain goes
     * all the way through the lambda.
     */
    @Test
    void wires_the_get_public_lesson_stream_use_case_bean_with_all_four_collaborators() {
        BunnyNetProperties properties = new BunnyNetProperties();
        properties.setPullZoneHostname("vz-test.b-cdn.net");
        properties.setVideoLibraryId("9999");
        properties.setSecurityKey("test-security-key");
        BunnyNetSignatureService signatureService =
            configuration.bunnyNetSignatureService(properties, new MockEnvironment());
        Clock clock = configuration.clock();

        GetPublicLessonStreamUseCase useCase = configuration.getPublicLessonStreamUseCase(
            lessonRepository, moduleRepository, new LessonAccessPolicy(entitlementPort), signatureService, clock
        );

        assertThat(useCase).isInstanceOf(GetPublicLessonStreamUseCaseImpl.class);
        // Beam smoke test: prove the service actually reads BunnyNetProperties.
        assertThat(signatureService).isInstanceOf(TokenAuthBunnyNetSignatureService.class);
        assertThat(signatureService.generateSignedUrl("vid", 1_700_000_000L))
            .startsWith("https://vz-test.b-cdn.net/vid/play_720p.mp4?token=HS256-");
    }

    /** #864: the preview bean is a plain read: the library, the signer and the clock, no transactional decorator. */
    @Test
    void wires_the_get_library_video_stream_use_case_bean_without_a_transactional_decorator() {
        InMemoryVideoLibrary library = new InMemoryVideoLibrary();
        library.add(new LibraryVideo("v-1", "Tango", LibraryVideoStatus.FINISHED, 10, null, true, List.of()));

        GetLibraryVideoStreamUseCase useCase = configuration.getLibraryVideoStreamUseCase(
            library, (videoId, expires) -> "https://cdn/" + videoId + "?expires=" + expires, () -> Instant.EPOCH
        );

        assertThat(useCase).isInstanceOf(GetLibraryVideoStreamUseCaseImpl.class);
        assertThat(useCase.get("v-1").streamUrl()).isEqualTo("https://cdn/v-1?expires=14400");
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

    @Test
    void wires_the_course_progress_access_policy_bean() {
        CourseProgressAccessPolicy policy = configuration.courseProgressAccessPolicy(entitlementPort);

        assertThat(policy).isNotNull();
    }

    @Test
    void wires_the_get_course_progress_use_case_bean() {
        LessonProgressRepository progressRepository = mock(LessonProgressRepository.class);

        GetCourseProgressUseCase useCase = configuration.getCourseProgressUseCase(
            courseRepository, progressRepository, new CourseProgressAccessPolicy(entitlementPort)
        );

        assertThat(useCase).isInstanceOf(GetCourseProgressUseCaseImpl.class);
    }

    @Test
    void wires_the_list_in_progress_courses_use_case_bean() {
        LessonProgressRepository progressRepository = mock(LessonProgressRepository.class);

        ListInProgressCoursesUseCase useCase = configuration.listInProgressCoursesUseCase(
            progressRepository, new CourseProgressAccessPolicy(entitlementPort)
        );

        assertThat(useCase).isInstanceOf(ListInProgressCoursesUseCaseImpl.class);
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

        private static final String SECURITY_KEY = "test-security-key";
        private static final String API_KEY = "test-library-api-key";

        /** Fake, obviously non-real values; the real pull zone, library and key come from the local environment. */
        private static final Map<String, Object> REAL_CDN = Map.of(
            "app.cdn.bunny-net.pull-zone-hostname", "vz-example.b-cdn.net",
            "app.cdn.bunny-net.video-library-id", "123456",
            "app.cdn.bunny-net.security-key", SECURITY_KEY,
            "app.cdn.bunny-net.api-key", API_KEY
        );

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
        void demo_profile_resolves_to_the_local_adapter_as_the_sole_candidate() {
            context = bootContext("demo");

            assertThat(context.getBeanNamesForType(BunnyNetSignatureService.class)).hasSize(1);
            assertThat(context.getBean(BunnyNetSignatureService.class))
                .isInstanceOf(LocalBunnyNetSignatureService.class);
        }

        @Test
        void demo_combined_with_e2e_bunny_net_resolves_to_a_single_local_adapter() {
            context = bootContext("demo", "e2e-bunny-net");

            assertThat(context.getBeanNamesForType(BunnyNetSignatureService.class)).hasSize(1);
            assertThat(context.getBean(BunnyNetSignatureService.class))
                .isInstanceOf(LocalBunnyNetSignatureService.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"prod", "production", "staging", "PROD", "Staging"})
        void demo_combined_with_a_production_like_profile_fails_the_context_refresh(String profile) {
            assertThatThrownBy(() -> bootContext("demo", profile))
                .isInstanceOf(BeanCreationException.class)
                .hasStackTraceContaining("SECURITY");
        }

        @Test
        void default_profiles_resolve_to_the_token_auth_adapter_when_configured() {
            context = bootContext(REAL_CDN);

            assertThat(context.getBeanNamesForType(BunnyNetSignatureService.class)).hasSize(1);
            assertThat(context.getBean(BunnyNetSignatureService.class))
                .isInstanceOf(TokenAuthBunnyNetSignatureService.class);
        }

        @Test
        void default_profiles_fail_closed_without_any_bunny_net_configuration() {
            assertThatThrownBy(() -> bootContext(Map.of()))
                .isInstanceOf(BeanCreationException.class)
                .hasStackTraceContaining("app.cdn.bunny-net.security-key")
                .hasStackTraceContaining("app.cdn.bunny-net.pull-zone-hostname")
                .hasStackTraceContaining("app.cdn.bunny-net.video-library-id");
        }

        @Test
        void default_profiles_fail_closed_when_only_the_security_key_is_missing() {
            Map<String, Object> withoutKey = new HashMap<>(REAL_CDN);
            withoutKey.remove("app.cdn.bunny-net.security-key");

            assertThatThrownBy(() -> bootContext(withoutKey))
                .isInstanceOf(BeanCreationException.class)
                .hasStackTraceContaining("app.cdn.bunny-net.security-key")
                .satisfies(failure -> assertThat(stackTrace(failure)).doesNotContain("pull-zone-hostname"));
        }

        @Test
        void default_profiles_fail_closed_when_the_host_keeps_the_placeholder_and_never_echo_the_key() {
            Map<String, Object> placeholderHost = new HashMap<>(REAL_CDN);
            placeholderHost.remove("app.cdn.bunny-net.pull-zone-hostname");

            assertThatThrownBy(() -> bootContext(placeholderHost))
                .isInstanceOf(BeanCreationException.class)
                .hasStackTraceContaining("app.cdn.bunny-net.pull-zone-hostname")
                .satisfies(failure -> assertThat(stackTrace(failure)).doesNotContain(SECURITY_KEY));
        }

        @ParameterizedTest
        @ValueSource(strings = {"prod", "production", "staging"})
        void production_like_profiles_resolve_to_the_token_auth_adapter_when_configured(String profile) {
            context = bootContext(REAL_CDN, profile);

            assertThat(context.getBean(BunnyNetSignatureService.class))
                .isInstanceOf(TokenAuthBunnyNetSignatureService.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"prod", "production", "staging"})
        void production_like_profiles_fail_closed_without_configuration(String profile) {
            assertThatThrownBy(() -> bootContext(Map.of(), profile))
                .isInstanceOf(BeanCreationException.class)
                .hasStackTraceContaining("app.cdn.bunny-net.security-key");
        }

        @Test
        void demo_with_a_security_key_resolves_to_the_token_auth_adapter() {
            context = bootContext(REAL_CDN, "demo");

            assertThat(context.getBeanNamesForType(BunnyNetSignatureService.class)).hasSize(1);
            assertThat(context.getBean(BunnyNetSignatureService.class))
                .isInstanceOf(TokenAuthBunnyNetSignatureService.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void demo_with_a_blank_security_key_keeps_the_local_adapter(String blankKey) {
            context = bootContext(Map.of("app.cdn.bunny-net.security-key", blankKey), "demo");

            assertThat(context.getBean(BunnyNetSignatureService.class))
                .isInstanceOf(LocalBunnyNetSignatureService.class);
        }

        @Test
        void demo_with_a_security_key_but_placeholder_host_fails_closed_without_echoing_the_key() {
            assertThatThrownBy(() -> bootContext(Map.of("app.cdn.bunny-net.security-key", SECURITY_KEY), "demo"))
                .isInstanceOf(BeanCreationException.class)
                .hasStackTraceContaining("app.cdn.bunny-net.pull-zone-hostname")
                .satisfies(failure -> assertThat(stackTrace(failure)).doesNotContain(SECURITY_KEY));
        }

        @Test
        void e2e_bunny_net_never_uses_the_real_adapter_even_with_a_security_key() {
            context = bootContext(REAL_CDN, "e2e-bunny-net");

            assertThat(context.getBeanNamesForType(BunnyNetSignatureService.class)).hasSize(1);
            assertThat(context.getBean(BunnyNetSignatureService.class))
                .isInstanceOf(LocalBunnyNetSignatureService.class);
        }

        @Test
        void the_diagnostic_probe_is_off_by_default_so_the_real_adapter_is_not_decorated() {
            context = bootContext(REAL_CDN);

            assertThat(context.getBean(BunnyNetSignatureService.class))
                .isExactlyInstanceOf(TokenAuthBunnyNetSignatureService.class);
        }

        @Test
        void the_diagnostic_probe_decorates_the_real_adapter_and_keeps_a_single_signature_bean() {
            Map<String, Object> probeOn = new HashMap<>(REAL_CDN);
            probeOn.put("app.cdn.bunny-net.diagnostic-probe", "true");

            context = bootContext(probeOn);

            assertThat(context.getBeanNamesForType(BunnyNetSignatureService.class)).hasSize(1);
            assertThat(context.getBean(BunnyNetSignatureService.class))
                .isExactlyInstanceOf(ProbingBunnyNetSignatureService.class);
        }

        @Test
        void the_diagnostic_probe_is_ignored_by_the_local_adapter() {
            context = bootContext(Map.of("app.cdn.bunny-net.diagnostic-probe", "true"), "demo");

            assertThat(context.getBean(BunnyNetSignatureService.class))
                .isExactlyInstanceOf(LocalBunnyNetSignatureService.class);
        }

        @Test
        void the_real_adapter_logs_a_startup_summary_without_the_key_and_with_the_probe_off() {
            try (CapturedLogs logs = CapturedLogs.of(VirtualConfiguration.class)) {
                context = bootContext(REAL_CDN);

                assertThat(logs.messages(Level.INFO)).contains(
                    "Bunny.net signature adapter: token authentication (real CDN)",
                    "Bunny.net configuration: host=vz-example.b-cdn.net libraryId=123456 file=play_720p.mp4 "
                        + "securityKey=configured diagnosticProbe=off probeReferer=none");
                assertThat(logs.text()).doesNotContain(SECURITY_KEY);
            }
        }

        @Test
        void the_startup_summary_reports_the_probe_as_on_and_normalises_the_host() {
            Map<String, Object> probeOn = new HashMap<>(REAL_CDN);
            probeOn.put("app.cdn.bunny-net.diagnostic-probe", "true");
            probeOn.put("app.cdn.bunny-net.pull-zone-hostname", "https://vz-example.b-cdn.net/");

            try (CapturedLogs logs = CapturedLogs.of(VirtualConfiguration.class)) {
                context = bootContext(probeOn);

                assertThat(logs.messages(Level.INFO)).contains(
                    "Bunny.net configuration: host=vz-example.b-cdn.net libraryId=123456 file=play_720p.mp4 "
                        + "securityKey=configured diagnosticProbe=on probeReferer=none");
            }
        }

        @Test
        void the_startup_summary_shows_the_probe_referer_because_it_is_not_a_secret() {
            Map<String, Object> probeOn = new HashMap<>(REAL_CDN);
            probeOn.put("app.cdn.bunny-net.diagnostic-probe", "true");
            probeOn.put("app.cdn.bunny-net.diagnostic-probe-referer", " http://localhost:8080/ ");

            try (CapturedLogs logs = CapturedLogs.of(VirtualConfiguration.class)) {
                context = bootContext(probeOn);

                assertThat(logs.messages(Level.INFO)).contains(
                    "Bunny.net configuration: host=vz-example.b-cdn.net libraryId=123456 file=play_720p.mp4 "
                        + "securityKey=configured diagnosticProbe=on probeReferer=http://localhost:8080/");
                assertThat(logs.text()).doesNotContain(SECURITY_KEY);
            }
        }

        @Test
        void an_invalid_probe_referer_aborts_the_boot_only_when_the_probe_is_enabled() {
            Map<String, Object> probeOn = new HashMap<>(REAL_CDN);
            probeOn.put("app.cdn.bunny-net.diagnostic-probe", "true");
            probeOn.put("app.cdn.bunny-net.diagnostic-probe-referer", "localhost:8080");

            assertThatThrownBy(() -> bootContext(probeOn))
                .isInstanceOf(BeanCreationException.class)
                .hasStackTraceContaining("app.cdn.bunny-net.diagnostic-probe-referer")
                .satisfies(failure -> assertThat(stackTrace(failure)).doesNotContain(SECURITY_KEY));

            Map<String, Object> probeOff = new HashMap<>(REAL_CDN);
            probeOff.put("app.cdn.bunny-net.diagnostic-probe-referer", "localhost:8080");

            context = bootContext(probeOff);

            assertThat(context.getBean(BunnyNetSignatureService.class))
                .isExactlyInstanceOf(TokenAuthBunnyNetSignatureService.class);
        }

        @Test
        void the_local_adapter_logs_no_cdn_configuration_summary() {
            try (CapturedLogs logs = CapturedLogs.of(VirtualConfiguration.class)) {
                context = bootContext("demo");

                assertThat(logs.messages(Level.INFO))
                    .contains("Bunny.net signature adapter: local (no real CDN is contacted)")
                    .noneMatch(message -> message.startsWith("Bunny.net configuration:"));
            }
        }

        @Test
        void demo_and_e2e_bunny_net_with_a_security_key_resolve_to_the_local_adapter() {
            context = bootContext(REAL_CDN, "demo", "e2e-bunny-net");

            assertThat(context.getBean(BunnyNetSignatureService.class))
                .isInstanceOf(LocalBunnyNetSignatureService.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"prod", "staging"})
        void demo_with_a_security_key_combined_with_a_production_like_profile_still_fails(String profile) {
            assertThatThrownBy(() -> bootContext(REAL_CDN, "demo", profile))
                .isInstanceOf(BeanCreationException.class)
                .hasStackTraceContaining("SECURITY");
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

        // --- video library selection (ADR-0049, #840) ---------------------------------------------------

        @ParameterizedTest
        @ValueSource(strings = {"e2e-bunny-net", "e2e-catalog-content", "test", "integration-test"})
        void the_in_memory_library_is_selected_by_the_e2e_and_test_profiles_even_with_an_api_key(String profile) {
            context = bootContext(REAL_CDN, profile);

            assertThat(context.getBeanNamesForType(VideoLibraryPort.class)).hasSize(1);
            assertThat(context.getBean(VideoLibraryPort.class)).isInstanceOf(InMemoryVideoLibrary.class);
        }

        @Test
        void demo_without_an_api_key_resolves_to_the_in_memory_library() {
            context = bootContext("demo");

            assertThat(context.getBean(VideoLibraryPort.class)).isInstanceOf(InMemoryVideoLibrary.class);
        }

        @Test
        void demo_with_an_api_key_resolves_to_the_real_library_client() {
            context = bootContext(REAL_CDN, "demo");

            assertThat(context.getBean(VideoLibraryPort.class)).isInstanceOf(BunnyStreamVideoLibrary.class);
        }

        @Test
        void default_profiles_resolve_to_the_real_library_client_when_configured() {
            context = bootContext(REAL_CDN);

            assertThat(context.getBean(VideoLibraryPort.class)).isInstanceOf(BunnyStreamVideoLibrary.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"prod", "production", "staging"})
        void production_like_profiles_resolve_to_the_real_library_client_when_configured(String profile) {
            context = bootContext(REAL_CDN, profile);

            assertThat(context.getBean(VideoLibraryPort.class)).isInstanceOf(BunnyStreamVideoLibrary.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"prod", "production", "staging"})
        void production_like_profiles_fail_closed_without_the_api_key_and_never_echo_any_secret(String profile) {
            Map<String, Object> withoutApiKey = new HashMap<>(REAL_CDN);
            withoutApiKey.remove("app.cdn.bunny-net.api-key");

            assertThatThrownBy(() -> bootContext(withoutApiKey, profile))
                .isInstanceOf(BeanCreationException.class)
                .hasStackTraceContaining("app.cdn.bunny-net.api-key")
                .hasStackTraceContaining("APP_CDN_BUNNYNET_APIKEY")
                .satisfies(failure -> assertThat(stackTrace(failure)).doesNotContain(SECURITY_KEY));
        }

        @Test
        void default_profiles_fail_closed_when_the_api_key_equals_the_security_key() {
            Map<String, Object> reused = new HashMap<>(REAL_CDN);
            reused.put("app.cdn.bunny-net.api-key", SECURITY_KEY);

            assertThatThrownBy(() -> bootContext(reused))
                .isInstanceOf(BeanCreationException.class)
                .hasStackTraceContaining("app.cdn.bunny-net.api-key")
                .satisfies(failure -> assertThat(stackTrace(failure)).doesNotContain(SECURITY_KEY + "\""));
        }

        @ParameterizedTest
        @ValueSource(strings = {"prod", "staging"})
        void the_in_memory_library_cannot_run_alongside_a_production_like_profile(String profile) {
            assertThatThrownBy(() -> bootContext(REAL_CDN, "test", profile))
                .isInstanceOf(BeanCreationException.class)
                .hasStackTraceContaining("SECURITY");
        }

        @Test
        void the_real_library_client_logs_a_summary_without_the_api_key() {
            try (CapturedLogs logs = CapturedLogs.of(VirtualConfiguration.class)) {
                context = bootContext(REAL_CDN);

                assertThat(logs.messages(Level.INFO))
                    .contains("Bunny.net video library adapter: management API (real library)")
                    .anyMatch(message -> message.startsWith("Bunny.net library configuration:")
                        && message.contains("apiKey=configured") && !message.contains(API_KEY));
            }
        }

        @Test
        void the_in_memory_library_logs_that_no_real_library_is_contacted() {
            try (CapturedLogs logs = CapturedLogs.of(VirtualConfiguration.class)) {
                context = bootContext("e2e-bunny-net");

                assertThat(logs.messages(Level.INFO))
                    .contains("Bunny.net video library adapter: in-memory (no real library is contacted)");
            }
        }

        @Test
        void wires_the_list_library_videos_use_case_bean() {
            ListLibraryVideosUseCase useCase = configuration.listLibraryVideosUseCase(
                new InMemoryVideoLibrary(), courseRepository, moduleRepository, lessonRepository
            );

            assertThat(useCase).isInstanceOf(ListLibraryVideosUseCaseImpl.class);
        }

        private static String stackTrace(Throwable failure) {
            return org.assertj.core.util.Throwables.getStackTrace(failure);
        }

        private AnnotationConfigApplicationContext bootContext(String... profiles) {
            return bootContext(Map.of(), profiles);
        }

        private AnnotationConfigApplicationContext bootContext(Map<String, Object> properties, String... profiles) {
            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
            ctx.getEnvironment().setActiveProfiles(profiles);
            ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-properties", properties));
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
            VirtualVideoAuditRepository virtualVideoAuditRepository() {
                return mock(VirtualVideoAuditRepository.class);
            }

            @Bean
            VirtualCourseEntitlementPort virtualCourseEntitlementPort() {
                return mock(VirtualCourseEntitlementPort.class);
            }

            @Bean
            LessonProgressRepository lessonProgressRepository() {
                return mock(LessonProgressRepository.class);
            }

            @Bean
            VirtualCourseReferencePort virtualCourseReferencePort() {
                return mock(VirtualCourseReferencePort.class);
            }
        }
    }
}
