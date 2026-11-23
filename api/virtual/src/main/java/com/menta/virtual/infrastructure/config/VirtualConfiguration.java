package com.menta.virtual.infrastructure.config;

import com.menta.shared.billing.VirtualCourseEntitlementPort;
import com.menta.shared.billing.VirtualCourseReferencePort;
import com.menta.virtual.application.port.in.CompleteLessonUseCase;
import com.menta.virtual.application.port.in.CreateVirtualCourseUseCase;
import com.menta.virtual.application.port.in.CreateVirtualLessonUseCase;
import com.menta.virtual.application.port.in.CreateVirtualModuleUseCase;
import com.menta.virtual.application.port.in.DeleteVirtualCourseUseCase;
import com.menta.virtual.application.port.in.DeleteVirtualLessonUseCase;
import com.menta.virtual.application.port.in.GetCourseProgressUseCase;
import com.menta.virtual.application.port.in.GetLessonProgressUseCase;
import com.menta.virtual.application.port.in.GetPublicLessonStreamUseCase;
import com.menta.virtual.application.port.in.GetPublicLessonUseCase;
import com.menta.virtual.application.port.in.GetVirtualCourseAdminDetailUseCase;
import com.menta.virtual.application.port.in.ListInProgressCoursesUseCase;
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
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.application.usecase.CompleteLessonUseCaseImpl;
import com.menta.virtual.application.usecase.CourseProgressAccessPolicy;
import com.menta.virtual.application.usecase.CreateVirtualCourseUseCaseImpl;
import com.menta.virtual.application.usecase.CreateVirtualLessonUseCaseImpl;
import com.menta.virtual.application.usecase.CreateVirtualModuleUseCaseImpl;
import com.menta.virtual.application.usecase.DeleteVirtualCourseUseCaseImpl;
import com.menta.virtual.application.usecase.DeleteVirtualLessonUseCaseImpl;
import com.menta.virtual.application.usecase.GetCourseProgressUseCaseImpl;
import com.menta.virtual.application.usecase.GetLessonProgressUseCaseImpl;
import com.menta.virtual.application.usecase.GetPublicLessonStreamUseCaseImpl;
import com.menta.virtual.application.usecase.GetPublicLessonUseCaseImpl;
import com.menta.virtual.application.usecase.GetVirtualCourseAdminDetailUseCaseImpl;
import com.menta.virtual.application.usecase.LessonAccessPolicy;
import com.menta.virtual.application.usecase.ListInProgressCoursesUseCaseImpl;
import com.menta.virtual.application.usecase.ListManagedVirtualCoursesUseCaseImpl;
import com.menta.virtual.application.usecase.PublishVirtualCourseUseCaseImpl;
import com.menta.virtual.application.usecase.ReorderVirtualModulesUseCaseImpl;
import com.menta.virtual.application.usecase.SaveLessonProgressUseCaseImpl;
import com.menta.virtual.application.usecase.UnpublishVirtualCourseUseCaseImpl;
import com.menta.virtual.application.usecase.UpdateVirtualCourseUseCaseImpl;
import com.menta.virtual.application.usecase.UpdateVirtualLessonUseCaseImpl;
import com.menta.virtual.application.usecase.UpdateVirtualModuleUseCaseImpl;
import com.menta.virtual.application.usecase.VirtualCourseCatalogPortImpl;
import com.menta.virtual.infrastructure.cdn.BunnyNetProperties;
import com.menta.virtual.infrastructure.cdn.ProbingBunnyNetSignatureService;
import com.menta.virtual.infrastructure.cdn.TokenAuthBunnyNetSignatureService;
import com.menta.virtual.infrastructure.cdn.local.LocalBunnyNetSignatureService;
import com.menta.virtual.infrastructure.transaction.RetryOnDuplicateKeyCompleteLessonUseCase;
import com.menta.virtual.infrastructure.transaction.RetryOnDuplicateKeySaveLessonProgressUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalCompleteLessonUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalCreateVirtualCourseUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalCreateVirtualLessonUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalCreateVirtualModuleUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalDeleteVirtualCourseUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalDeleteVirtualLessonUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalPublishVirtualCourseUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalReorderVirtualModulesUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalSaveLessonProgressUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalUnpublishVirtualCourseUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalUpdateVirtualCourseUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalUpdateVirtualLessonUseCase;
import com.menta.virtual.infrastructure.transaction.TransactionalUpdateVirtualModuleUseCase;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

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

    private static final Logger LOG = LoggerFactory.getLogger(VirtualConfiguration.class);

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
     * Production-like profiles that MUST NEVER combine with {@code
     * e2e-bunny-net} or {@code demo} (ADR-0040, issue #129). Mirrors {@code
     * BillingConfiguration}'s {@code PRODUCTION_PROFILES} set — same
     * production/staging vocabulary across modules.
     */
    private static final Set<String> PRODUCTION_LIKE_PROFILES = Set.of("prod", "production", "staging");

    /**
     * US-VIRTUAL-004 / ADR-0040 (amended 2026-11-14, #751). The single
     * {@link BunnyNetSignatureService} bean; exactly one adapter exists in any
     * context. Selection, evaluated in this order:
     * <ol>
     *   <li>{@code e2e-bunny-net} active: {@link LocalBunnyNetSignatureService}, always (never reaches the real
     *       CDN, even if a security key is present).</li>
     *   <li>{@code demo} active: {@link TokenAuthBunnyNetSignatureService} when
     *       {@code app.cdn.bunny-net.security-key} is non-blank (a developer who wants to watch a real video),
     *       otherwise {@link LocalBunnyNetSignatureService}.</li>
     *   <li>Any other profile set: {@link TokenAuthBunnyNetSignatureService}.</li>
     * </ol>
     * Whenever the real adapter is chosen, {@link BunnyNetProperties#requireRealCdnConfiguration()} aborts the
     * boot if the key, host or library are missing or still placeholders. The local adapter (cases 1 and 2) is
     * guarded against production-like profiles. Both guards live HERE, inside the factory method, rather than in
     * an {@code ApplicationRunner}: a {@code @Bean} factory throw aborts context refresh before the web server
     * accepts requests. A single method (instead of two complementary {@code @Profile} methods) is needed because
     * a profile expression cannot test whether a property is blank.
     *
     * <p>With {@code app.cdn.bunny-net.diagnostic-probe=true} the real adapter is wrapped in
     * {@link ProbingBunnyNetSignatureService} (a logging-only HEAD probe); the bean stays single. The local
     * adapter is never wrapped.</p>
     */
    @Bean
    public BunnyNetSignatureService bunnyNetSignatureService(
        BunnyNetProperties properties, Environment environment
    ) {
        boolean e2e = environment.acceptsProfiles(Profiles.of("e2e-bunny-net"));
        boolean demo = environment.acceptsProfiles(Profiles.of("demo"));
        boolean hasSecurityKey = properties.getSecurityKey() != null && !properties.getSecurityKey().isBlank();
        if (e2e || (demo && !hasSecurityKey)) {
            failClosedIfProductionLike(environment);
            LOG.info("Bunny.net signature adapter: local (no real CDN is contacted)");
            return new LocalBunnyNetSignatureService(properties);
        }
        if (demo) {
            failClosedIfProductionLike(environment);
        }
        properties.requireRealCdnConfiguration();
        LOG.info("Bunny.net signature adapter: token authentication (real CDN)");
        LOG.info("Bunny.net configuration: host={} libraryId={} file={} securityKey=configured diagnosticProbe={} "
                + "probeReferer={}",
            properties.pullZoneHost(), properties.getVideoLibraryId(), properties.getFileName(),
            properties.isDiagnosticProbe() ? "on" : "off",
            properties.probeReferer() == null ? "none" : properties.probeReferer());
        BunnyNetSignatureService real = new TokenAuthBunnyNetSignatureService(properties);
        return properties.isDiagnosticProbe() ? new ProbingBunnyNetSignatureService(real, properties) : real;
    }

    private static void failClosedIfProductionLike(Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if (PRODUCTION_LIKE_PROFILES.contains(profile.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException(
                    "SECURITY: local Bunny.net signature adapter or demo profile (profiles e2e-bunny-net, demo) "
                        + "cannot run alongside a production-like profile. Active profiles: "
                        + String.join(", ", environment.getActiveProfiles())
                );
            }
        }
    }

    /**
     * US-VIRTUAL-004. Same shape as
     * {@link #getPublicLessonUseCase(VirtualLessonRepository, VirtualModuleRepository, VirtualCourseRepository,
     * VirtualCourseEntitlementPort)},
     * but the cross-module {@link VirtualCourseEntitlementPort} is now a
     * stream-authorization dependency rather than a detail-visibility
     * dependency. Both beans share a process-level entitlement projection
     * (Redis-backed on the billing side) so the discriminator between
     * "no subscription" and "expired subscription" is intentionally
     * lost at this layer — issue #50 escenario 2 upgrades the port in
     * a follow-up ticket.
     */
    @Bean
    public LessonAccessPolicy lessonAccessPolicy(VirtualCourseEntitlementPort virtualCourseEntitlementPort) {
        return new LessonAccessPolicy(virtualCourseEntitlementPort);
    }

    @Bean
    public GetPublicLessonStreamUseCase getPublicLessonStreamUseCase(
        VirtualLessonRepository lessonRepository,
        VirtualModuleRepository moduleRepository,
        LessonAccessPolicy lessonAccessPolicy,
        BunnyNetSignatureService bunnyNetSignatureService,
        Clock clock
    ) {
        return new GetPublicLessonStreamUseCaseImpl(
            lessonRepository,
            moduleRepository,
            lessonAccessPolicy,
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
        LessonAccessPolicy lessonAccessPolicy
    ) {
        return new GetPublicLessonUseCaseImpl(
            lessonRepository, moduleRepository, courseRepository, lessonAccessPolicy
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
    public GetVirtualCourseAdminDetailUseCase getVirtualCourseAdminDetailUseCase(
        VirtualCourseRepository courseRepository, VirtualCourseCatalogPort virtualCourseCatalogPort
    ) {
        return new GetVirtualCourseAdminDetailUseCaseImpl(courseRepository, virtualCourseCatalogPort);
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
        VirtualLessonRepository lessonRepository, LessonProgressRepository progressRepository,
        VirtualCourseReferencePort courseReferencePort, VirtualCourseAuditRepository auditRepository
    ) {
        return new TransactionalDeleteVirtualCourseUseCase(new DeleteVirtualCourseUseCaseImpl(
            courseRepository, moduleRepository, lessonRepository, progressRepository, courseReferencePort,
            auditRepository
        ));
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
    public DeleteVirtualLessonUseCase deleteVirtualLessonUseCase(
        VirtualLessonRepository lessonRepository, VirtualCourseRepository courseRepository,
        LessonProgressRepository progressRepository, VirtualCourseAuditRepository auditRepository
    ) {
        return new TransactionalDeleteVirtualLessonUseCase(new DeleteVirtualLessonUseCaseImpl(
            lessonRepository, courseRepository, progressRepository, auditRepository
        ));
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

    /**
     * US-VIRTUAL-005. Read-only, so it skips the transactional/retry decorators entirely — same
     * shape as {@link #getPublicLessonUseCase}.
     */
    @Bean
    public GetLessonProgressUseCase getLessonProgressUseCase(
        VirtualLessonRepository lessonRepository, VirtualModuleRepository moduleRepository,
        LessonProgressRepository progressRepository, LessonAccessPolicy lessonAccessPolicy
    ) {
        return new GetLessonProgressUseCaseImpl(
            lessonRepository, moduleRepository, progressRepository, lessonAccessPolicy
        );
    }

    /**
     * US-VIRTUAL-005. Write path per design's "Upsert concurrency": the retry decorator wraps the
     * transactional one, so a duplicate-key collision retries in a fresh transaction.
     */
    @Bean
    public SaveLessonProgressUseCase saveLessonProgressUseCase(
        VirtualLessonRepository lessonRepository, VirtualModuleRepository moduleRepository,
        LessonProgressRepository progressRepository, LessonAccessPolicy lessonAccessPolicy, Clock clock
    ) {
        SaveLessonProgressUseCaseImpl impl = new SaveLessonProgressUseCaseImpl(
            lessonRepository, moduleRepository, progressRepository, lessonAccessPolicy, clock
        );
        return new RetryOnDuplicateKeySaveLessonProgressUseCase(new TransactionalSaveLessonProgressUseCase(impl));
    }

    /** US-VIRTUAL-005. Same write-path shape as {@link #saveLessonProgressUseCase}. */
    @Bean
    public CompleteLessonUseCase completeLessonUseCase(
        VirtualLessonRepository lessonRepository, VirtualModuleRepository moduleRepository,
        LessonProgressRepository progressRepository, LessonAccessPolicy lessonAccessPolicy, Clock clock
    ) {
        CompleteLessonUseCaseImpl impl = new CompleteLessonUseCaseImpl(
            lessonRepository, moduleRepository, progressRepository, lessonAccessPolicy, clock
        );
        return new RetryOnDuplicateKeyCompleteLessonUseCase(new TransactionalCompleteLessonUseCase(impl));
    }

    /**
     * US-VIRTUAL-005, Slice 3. Unlike {@link #lessonAccessPolicy}, there is no free/preview
     * exception here (design.md decision 5) — see {@link CourseProgressAccessPolicy}'s Javadoc.
     */
    @Bean
    public CourseProgressAccessPolicy courseProgressAccessPolicy(
        VirtualCourseEntitlementPort virtualCourseEntitlementPort) {
        return new CourseProgressAccessPolicy(virtualCourseEntitlementPort);
    }

    /**
     * US-VIRTUAL-005, Slice 3. Read-only, so it skips the transactional/retry decorators entirely
     * — same shape as {@link #getLessonProgressUseCase}. {@link
     * com.menta.virtual.application.usecase.CourseProgressAssembler} is a pure static helper, not
     * a bean — it needs no collaborators and is called directly by the use case.
     */
    @Bean
    public GetCourseProgressUseCase getCourseProgressUseCase(
        VirtualCourseRepository courseRepository, LessonProgressRepository progressRepository,
        CourseProgressAccessPolicy courseProgressAccessPolicy
    ) {
        return new GetCourseProgressUseCaseImpl(courseRepository, progressRepository, courseProgressAccessPolicy);
    }

    /**
     * #765. Read-only like {@link #getCourseProgressUseCase}, so no transactional/retry decorator,
     * and the same {@link CourseProgressAccessPolicy} bean: the listing applies exactly the access
     * rule of the per-course progress.
     */
    @Bean
    public ListInProgressCoursesUseCase listInProgressCoursesUseCase(
        LessonProgressRepository progressRepository, CourseProgressAccessPolicy courseProgressAccessPolicy
    ) {
        return new ListInProgressCoursesUseCaseImpl(progressRepository, courseProgressAccessPolicy);
    }
}
