package com.menta.physical.infrastructure.config;

import com.menta.physical.application.port.in.BatchCreatePhysicalSessionsUseCase;
import com.menta.physical.application.port.in.CreatePhysicalCourseUseCase;
import com.menta.physical.application.port.in.CreatePhysicalSessionUseCase;
import com.menta.physical.application.port.in.IssuePhysicalAccessQrUseCase;
import com.menta.physical.application.port.in.ListManagedPhysicalCoursesUseCase;
import com.menta.physical.application.port.in.ListManagedPhysicalSessionsUseCase;
import com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort;
import com.menta.physical.application.port.in.PhysicalCourseOwnershipPort;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.physical.application.port.in.UpdatePhysicalCourseUseCase;
import com.menta.physical.application.port.in.UpdatePhysicalSessionUseCase;
import com.menta.physical.application.port.out.AttendanceRepository;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.PhysicalCapacityAssignmentRepository;
import com.menta.physical.application.port.out.PhysicalCourseRepository;
import com.menta.physical.application.port.out.PhysicalSessionRepository;
import com.menta.physical.application.usecase.BatchCreatePhysicalSessionsUseCaseImpl;
import com.menta.physical.application.usecase.CreatePhysicalCourseUseCaseImpl;
import com.menta.physical.application.usecase.CreatePhysicalSessionUseCaseImpl;
import com.menta.physical.application.usecase.IssuePhysicalAccessQrUseCaseImpl;
import com.menta.physical.application.usecase.ListManagedPhysicalCoursesUseCaseImpl;
import com.menta.physical.application.usecase.ListManagedPhysicalSessionsUseCaseImpl;
import com.menta.physical.application.usecase.PhysicalCourseAvailabilityPortImpl;
import com.menta.physical.application.usecase.PhysicalCourseOwnershipPortImpl;
import com.menta.physical.application.usecase.ProcessPhysicalCheckInUseCaseImpl;
import com.menta.physical.application.usecase.UpdatePhysicalCourseUseCaseImpl;
import com.menta.physical.application.usecase.UpdatePhysicalSessionUseCaseImpl;
import com.menta.physical.infrastructure.qr.FormatQrCredentialSignatureService;
import com.menta.physical.infrastructure.qr.QrProperties;
import com.menta.physical.infrastructure.redis.RedisCheckInLockPort;
import jakarta.annotation.PostConstruct;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Wires Physical's use cases. Adapters are {@code @Component}-scanned; the
 * use cases are plain Java classes composed here, mirroring {@code
 * VirtualConfiguration}'s rationale: no implicit {@code @Autowired} on
 * use-case classes.
 *
 * <p>{@link QrProperties} is picked up by the package-scoped {@code
 * @ConfigurationPropertiesScan} below, same rationale {@code
 * VirtualConfiguration} documents for {@code BunnyNetProperties}: kept
 * annotation-free to avoid the dual-registration gotcha and to be explicit
 * about package ownership inside Physical.</p>
 */
@Configuration
@ConfigurationPropertiesScan(basePackages = "com.menta.physical.infrastructure.qr")
public class PhysicalConfiguration {

    /**
     * Dev-only default check-in device token — same criterion as {@code
     * BillingConfiguration.DEV_DEFAULT_WEBHOOK_HMAC_SECRET}: detects
     * insecure configuration in production.
     */
    private static final String DEV_DEFAULT_DEVICE_TOKEN =
        "ZGV2LW9ubHktY2hlY2tpbi1kZXZpY2UtdG9rZW4tbm90LWZvci1wcm9kdWN0aW9uLXVzZQ==";

    private static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production", "staging");

    private final Environment environment;

    @Value("${app.physical.checkin.device-token:" + DEV_DEFAULT_DEVICE_TOKEN + "}")
    private String checkInDeviceToken;

    public PhysicalConfiguration(Environment environment) {
        this.environment = environment;
    }

    /**
     * Fail-fast: reject the dev-only check-in device token in production
     * profiles (US-PHYSICAL-001).
     */
    @PostConstruct
    void validateDeviceTokenNotDefaultInProduction() {
        if (isProductionProfile() && DEV_DEFAULT_DEVICE_TOKEN.equals(checkInDeviceToken)) {
            throw new IllegalStateException(
                "SECURITY: production requires a non-default check-in device token. "
                    + "Set app.physical.checkin.device-token via environment variables. "
                    + "Active profiles: " + String.join(", ", environment.getActiveProfiles())
            );
        }
    }

    private boolean isProductionProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if (PRODUCTION_PROFILES.contains(profile.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Production {@link Clock} adapter for the application-layer port.
     * Same "single test seam" approach as {@code VirtualConfiguration}'s
     * own {@code clock()} bean: tests inject a fixed clock so TTL/window
     * assertions never depend on the wall clock.
     *
     * <p>Named {@code physicalClock}, not {@code clock}: Spring bean
     * definitions are registered by method name regardless of the declaring
     * {@code @Configuration} class, so reusing {@code clock()} verbatim
     * collides with {@code VirtualConfiguration.clock()} — a same-name,
     * different-type {@code BeanDefinitionOverrideException} at context
     * startup, the exact class of bug the app-level test suite exists to
     * catch (see {@code PhysicalCheckInIntegrationTest} and the shared
     * {@code :api:app:test} run). Autowiring by type is unaffected: this is
     * still the only bean of {@code com.menta.physical...Clock} in the
     * context.</p>
     */
    @Bean
    public Clock physicalClock() {
        return () -> java.time.Clock.systemUTC().instant();
    }

    @Bean
    public IssuePhysicalAccessQrUseCase issuePhysicalAccessQrUseCase(
        PhysicalSessionRepository sessionRepository,
        PhysicalCapacityAssignmentRepository assignmentRepository,
        Clock clock, QrProperties qrProperties
    ) {
        return new IssuePhysicalAccessQrUseCaseImpl(
            sessionRepository, assignmentRepository, new FormatQrCredentialSignatureService(),
            clock, qrProperties.getCredentialTtl(),
            qrProperties.getSessionWindowBefore(), qrProperties.getSessionWindowAfter()
        );
    }

    @Bean
    public ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase(
        PhysicalSessionRepository sessionRepository,
        PhysicalCapacityAssignmentRepository assignmentRepository,
        AttendanceRepository attendanceRepository, RedisTemplate<String, String> redisTemplate,
        Clock clock, QrProperties qrProperties
    ) {
        return new ProcessPhysicalCheckInUseCaseImpl(
            sessionRepository, assignmentRepository, attendanceRepository,
            new FormatQrCredentialSignatureService(), new RedisCheckInLockPort(redisTemplate),
            clock, checkInDeviceToken, qrProperties.getSessionWindowBefore(),
            qrProperties.getSessionWindowAfter(), qrProperties.getLockTtl()
        );
    }

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
