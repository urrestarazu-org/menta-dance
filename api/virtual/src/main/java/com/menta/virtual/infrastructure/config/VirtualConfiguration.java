package com.menta.virtual.infrastructure.config;

import com.menta.shared.billing.VirtualCourseEntitlementPort;
import com.menta.virtual.application.port.in.CreateVirtualCourseUseCase;
import com.menta.virtual.application.port.in.CreateVirtualLessonUseCase;
import com.menta.virtual.application.port.in.CreateVirtualModuleUseCase;
import com.menta.virtual.application.port.in.DeleteVirtualCourseUseCase;
import com.menta.virtual.application.port.in.GetPublicLessonStreamUseCase;
import com.menta.virtual.application.port.in.GetPublicLessonUseCase;
import com.menta.virtual.application.port.in.ListManagedVirtualCoursesUseCase;
import com.menta.virtual.application.port.in.PublishVirtualCourseUseCase;
import com.menta.virtual.application.port.in.ReorderVirtualModulesUseCase;
import com.menta.virtual.application.port.in.UnpublishVirtualCourseUseCase;
import com.menta.virtual.application.port.in.UpdateVirtualCourseUseCase;
import com.menta.virtual.application.port.in.UpdateVirtualLessonUseCase;
import com.menta.virtual.application.port.in.UpdateVirtualModuleUseCase;
import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import com.menta.virtual.application.port.out.BunnyNetSignatureService;
import com.menta.virtual.application.port.out.Clock;
import com.menta.virtual.application.port.out.VirtualCourseAuditRepository;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.application.usecase.CreateVirtualCourseUseCaseImpl;
import com.menta.virtual.application.usecase.CreateVirtualLessonUseCaseImpl;
import com.menta.virtual.application.usecase.CreateVirtualModuleUseCaseImpl;
import com.menta.virtual.application.usecase.DeleteVirtualCourseUseCaseImpl;
import com.menta.virtual.application.usecase.GetPublicLessonStreamUseCaseImpl;
import com.menta.virtual.application.usecase.GetPublicLessonUseCaseImpl;
import com.menta.virtual.application.usecase.ListManagedVirtualCoursesUseCaseImpl;
import com.menta.virtual.application.usecase.PublishVirtualCourseUseCaseImpl;
import com.menta.virtual.application.usecase.ReorderVirtualModulesUseCaseImpl;
import com.menta.virtual.application.usecase.UnpublishVirtualCourseUseCaseImpl;
import com.menta.virtual.application.usecase.UpdateVirtualCourseUseCaseImpl;
import com.menta.virtual.application.usecase.UpdateVirtualLessonUseCaseImpl;
import com.menta.virtual.application.usecase.UpdateVirtualModuleUseCaseImpl;
import com.menta.virtual.application.usecase.VirtualCourseCatalogPortImpl;
import com.menta.virtual.infrastructure.cdn.BunnyNetProperties;
import com.menta.virtual.infrastructure.cdn.StringFormatBunnyNetSignatureService;
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
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
     * Wires Virtual's use cases. Adapters are {@code @Component}-scanned; the
     * use cases are plain Java classes composed here, mirroring {@code
     * AuthConfiguration}/{@code BillingConfiguration}'s rationale: no implicit
     * {@code @Autowired} on use-case classes.
     *
     * <p>Every mutating use case is wrapped in its {@code Transactional*}
     * decorator (#54 review follow-up) — each performs its domain mutation and
     * its {@code virtual_course_audit} write as separate repository calls, which
     * without a wrapping transaction would commit independently.</p>
     *
     * <p>{@link BunnyNetProperties} is picked up by the package-scoped
     * {@code @ConfigurationPropertiesScan} below: {@code MentaDanceApplication}'s
     * {@code scanBasePackages = "com.menta"} would otherwise auto-discover it
     * via component scan from {@code @Component}, but we keep the
     * properties classes annotation-free to avoid the dual-registration
     * gotcha and to be explicit about package ownership inside Virtual.</p>
     */
    @Configuration
    @ConfigurationPropertiesScan(basePackages = "com.menta.virtual.infrastructure.cdn")
    public class VirtualConfiguration {

    /**
     * Production {@link java.time.Clock} adapter for the application-layer
     * {@link Clock} port. Mirrors the {@code AuthConfiguration}/{@code
     * BillingConfiguration} "single test seam" approach: tests inject a
     * {@code Clock.fixed(...)} so the TTL assertion never depends on the
     * wall clock.
     */
    @Bean
    public Clock clock() {
        return () -> java.time.Clock.systemUTC().instant();
    }

    /**
     * US-VIRTUAL-004 placeholder. {@link StringFormatBunnyNetSignatureService}
     * returns a {@code pullZone/videoLibrary/videoId} string — unsafe for
     * production but structurally correct so a future HMAC-backed impl
     * (or the official Bunny.net SDK, once accepted onto the dep tree)
     * can replace THIS BEAN without touching the use case or the wire
     * contract. ADR-0040 is the architectural note behind that
     * isolation.
     */
    @Bean
    public BunnyNetSignatureService bunnyNetSignatureService(BunnyNetProperties properties) {
        return new StringFormatBunnyNetSignatureService(properties);
    }

    /**
     * US-VIRTUAL-004. Same shape as
     * {@link #getPublicLessonUseCase(VirtualLessonRepository, VirtualModuleRepository, VirtualCourseRepository, VirtualCourseEntitlementPort)},
     * but the cross-module {@link VirtualCourseEntitlementPort} is now a
     * stream-authorization dependency rather than a detail-visibility
     * dependency. Both beans share a process-level entitlement projection
     * (Redis-backed on the billing side) so the discriminator between
     * "no subscription" and "expired subscription" is intentionally
     * lost at this layer — issue #50 escenario 2 upgrades the port in
     * a follow-up ticket.
     */
    @Bean
    public GetPublicLessonStreamUseCase getPublicLessonStreamUseCase(
        VirtualLessonRepository lessonRepository,
        VirtualCourseEntitlementPort virtualCourseEntitlementPort,
        BunnyNetSignatureService bunnyNetSignatureService,
        Clock clock
    ) {
        return new GetPublicLessonStreamUseCaseImpl(
            lessonRepository,
            virtualCourseEntitlementPort,
            bunnyNetSignatureService,
            clock
        );
    }

    /**
     * US-VIRTUAL-003 (#48). Read-only on triple repositories + the
     * cross-module {@link VirtualCourseEntitlementPort}. ADR-0039 lets Virtual
     * consume the billing port — the alternative of HTTP / RabbitMQ would
     * reintroduce the cross-module smell #62 worked to remove. The bean
     * itself is registered by {@code BillingConfiguration} since Billing
     * owns the subscription lifecycle; we only inject.
     */
    @Bean
    public GetPublicLessonUseCase getPublicLessonUseCase(
        VirtualLessonRepository lessonRepository,
        VirtualModuleRepository moduleRepository,
        VirtualCourseRepository courseRepository,
        VirtualCourseEntitlementPort virtualCourseEntitlementPort
    ) {
        return new GetPublicLessonUseCaseImpl(
            lessonRepository, moduleRepository, courseRepository, virtualCourseEntitlementPort
        );
    }

    @Bean
    public VirtualCourseCatalogPort virtualCourseCatalogPort(
        VirtualCourseRepository virtualCourseRepository,
        VirtualModuleRepository moduleRepository,
        VirtualLessonRepository lessonRepository
    ) {
        // #47: detail read needs module + lesson repositories on top of the
        // course repository. The list/summary methods are unaffected because
        // they never reach into these extra collaborators.
        return new VirtualCourseCatalogPortImpl(
            virtualCourseRepository, moduleRepository, lessonRepository
        );
    }

    @Bean
    public CreateVirtualCourseUseCase createVirtualCourseUseCase(
        VirtualCourseRepository courseRepository, VirtualCourseAuditRepository auditRepository
    ) {
        return new TransactionalCreateVirtualCourseUseCase(
            new CreateVirtualCourseUseCaseImpl(courseRepository, auditRepository)
        );
    }

    @Bean
    public ListManagedVirtualCoursesUseCase listManagedVirtualCoursesUseCase(VirtualCourseRepository courseRepository) {
        return new ListManagedVirtualCoursesUseCaseImpl(courseRepository);
    }

    @Bean
    public UpdateVirtualCourseUseCase updateVirtualCourseUseCase(
        VirtualCourseRepository courseRepository, VirtualCourseAuditRepository auditRepository
    ) {
        return new TransactionalUpdateVirtualCourseUseCase(
            new UpdateVirtualCourseUseCaseImpl(courseRepository, auditRepository)
        );
    }

    @Bean
    public DeleteVirtualCourseUseCase deleteVirtualCourseUseCase(
        VirtualCourseRepository courseRepository, VirtualModuleRepository moduleRepository,
        VirtualLessonRepository lessonRepository, VirtualCourseAuditRepository auditRepository
    ) {
        return new TransactionalDeleteVirtualCourseUseCase(
            new DeleteVirtualCourseUseCaseImpl(courseRepository, moduleRepository, lessonRepository, auditRepository)
        );
    }

    @Bean
    public PublishVirtualCourseUseCase publishVirtualCourseUseCase(
        VirtualCourseRepository courseRepository, VirtualModuleRepository moduleRepository,
        VirtualLessonRepository lessonRepository, VirtualCourseAuditRepository auditRepository
    ) {
        return new TransactionalPublishVirtualCourseUseCase(new PublishVirtualCourseUseCaseImpl(
            courseRepository, moduleRepository, lessonRepository, auditRepository
        ));
    }

    @Bean
    public UnpublishVirtualCourseUseCase unpublishVirtualCourseUseCase(
        VirtualCourseRepository courseRepository, VirtualCourseAuditRepository auditRepository
    ) {
        return new TransactionalUnpublishVirtualCourseUseCase(
            new UnpublishVirtualCourseUseCaseImpl(courseRepository, auditRepository)
        );
    }

    @Bean
    public CreateVirtualModuleUseCase createVirtualModuleUseCase(
        VirtualCourseRepository courseRepository, VirtualModuleRepository moduleRepository,
        VirtualCourseAuditRepository auditRepository
    ) {
        return new TransactionalCreateVirtualModuleUseCase(
            new CreateVirtualModuleUseCaseImpl(courseRepository, moduleRepository, auditRepository)
        );
    }

    @Bean
    public UpdateVirtualModuleUseCase updateVirtualModuleUseCase(
        VirtualModuleRepository moduleRepository, VirtualCourseRepository courseRepository,
        VirtualCourseAuditRepository auditRepository
    ) {
        return new TransactionalUpdateVirtualModuleUseCase(
            new UpdateVirtualModuleUseCaseImpl(moduleRepository, courseRepository, auditRepository)
        );
    }

    @Bean
    public CreateVirtualLessonUseCase createVirtualLessonUseCase(
        VirtualModuleRepository moduleRepository, VirtualCourseRepository courseRepository,
        VirtualLessonRepository lessonRepository, VirtualCourseAuditRepository auditRepository
    ) {
        return new TransactionalCreateVirtualLessonUseCase(new CreateVirtualLessonUseCaseImpl(
            moduleRepository, courseRepository, lessonRepository, auditRepository
        ));
    }

    @Bean
    public UpdateVirtualLessonUseCase updateVirtualLessonUseCase(
        VirtualLessonRepository lessonRepository, VirtualCourseRepository courseRepository,
        VirtualCourseAuditRepository auditRepository
    ) {
        return new TransactionalUpdateVirtualLessonUseCase(
            new UpdateVirtualLessonUseCaseImpl(lessonRepository, courseRepository, auditRepository)
        );
    }

    @Bean
    public ReorderVirtualModulesUseCase reorderVirtualModulesUseCase(
        VirtualCourseRepository courseRepository, VirtualModuleRepository moduleRepository,
        VirtualCourseAuditRepository auditRepository
    ) {
        return new TransactionalReorderVirtualModulesUseCase(
            new ReorderVirtualModulesUseCaseImpl(courseRepository, moduleRepository, auditRepository)
        );
    }
}
