package com.menta.app.integration.physical;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.physical.application.dto.PhysicalCourseSummary;
import com.menta.physical.application.dto.PhysicalSessionAvailability;
import com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityHoldJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityHoldJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * MySQL-backed coverage for {@link PhysicalCourseAvailabilityPort} (US-PHYSICAL-003).
 * No HTTP: this module exposes no public endpoint of its own (see #40/#95) —
 * this test wires the real Spring context and calls the port bean directly,
 * proving the JPA layer, cursor pagination and the live availability COUNT
 * actually work against real MySQL. physical_capacity_assignments/holds have
 * no port-owned entity in production (only a native subquery reads them, see
 * PhysicalSessionJpaRepository) — seeded here through their own schema-only
 * entities.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
@Testcontainers
class PhysicalCourseAvailabilityIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired private PhysicalCourseAvailabilityPort physicalCourseAvailabilityPort;
    @Autowired private PhysicalCourseJpaRepository courseRepository;
    @Autowired private PhysicalSessionJpaRepository sessionRepository;
    @Autowired private PhysicalCapacityAssignmentJpaRepository assignmentRepository;
    @Autowired private PhysicalCapacityHoldJpaRepository holdRepository;

    // Every Redis-backed port needs a mock: integration-test excludes
    // RedisAutoConfiguration and this is one shared Spring context. Mirrors
    // VirtualCourseCatalogIntegrationTest's exact mock set.
    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private CourseCatalogPort courseCatalogPort;
    // US-PHYSICAL-001: ProcessPhysicalCheckInUseCaseImpl needs a RedisTemplate
    // its bean factory would otherwise fail to resolve in this Redis-less context.
    @MockBean private ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase;

    @AfterEach
    void cleanUp() {
        holdRepository.deleteAll();
        assignmentRepository.deleteAll();
        sessionRepository.deleteAll();
        courseRepository.deleteAll();
    }

    private UUID seedCourse(String title, CourseStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        courseRepository.save(new PhysicalCourseJpaEntity(
            id, title, "desc " + title, UUID.randomUUID(), "María García", "TUESDAY", LocalTime.of(19, 0),
            60, "BEGINNER", 20, status, now, now
        ));
        return id;
    }

    private UUID seedSession(UUID courseId, Instant scheduledAt, int capacity) {
        UUID id = UUID.randomUUID();
        sessionRepository.save(new PhysicalSessionJpaEntity(id, courseId, scheduledAt, capacity, "SCHEDULED", null));
        return id;
    }

    private UUID seedCancelledSession(UUID courseId, Instant scheduledAt, int capacity) {
        UUID id = UUID.randomUUID();
        sessionRepository.save(new PhysicalSessionJpaEntity(id, courseId, scheduledAt, capacity, "CANCELLED", null));
        return id;
    }

    private void seedAssignment(UUID sessionId) {
        assignmentRepository.save(new PhysicalCapacityAssignmentJpaEntity(
            UUID.randomUUID(), sessionId, UUID.randomUUID(), Instant.now()
        ));
    }

    private void seedHold(UUID sessionId, Instant expiresAt) {
        holdRepository.save(new PhysicalCapacityHoldJpaEntity(UUID.randomUUID(), sessionId, expiresAt, Instant.now()));
    }

    @Test
    void lists_only_active_courses() {
        UUID activeId = seedCourse("Salsa inicial", CourseStatus.ACTIVE);
        seedCourse("Bachata pausada", CourseStatus.INACTIVE);

        List<PhysicalCourseSummary> result = physicalCourseAvailabilityPort.listCourses(null, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).courseId()).isEqualTo(activeId.toString());
        assertThat(result.get(0).title()).isEqualTo("Salsa inicial");
    }

    @Test
    void returns_an_empty_list_when_there_are_no_active_courses() {
        seedCourse("Bachata pausada", CourseStatus.INACTIVE);

        assertThat(physicalCourseAvailabilityPort.listCourses(null, 10)).isEmpty();
    }

    @Test
    void cursor_pagination_never_repeats_or_skips_a_course() {
        seedCourse("A", CourseStatus.ACTIVE);
        seedCourse("B", CourseStatus.ACTIVE);
        seedCourse("C", CourseStatus.ACTIVE);

        List<PhysicalCourseSummary> firstPage = physicalCourseAvailabilityPort.listCourses(null, 1);
        assertThat(firstPage).hasSize(1);
        String cursor = firstPage.get(0).courseId();

        List<PhysicalCourseSummary> secondPage = physicalCourseAvailabilityPort.listCourses(cursor, 10);

        assertThat(secondPage).hasSize(2);
        assertThat(secondPage).noneMatch(c -> c.courseId().equals(cursor));
    }

    @Test
    void computes_available_spots_as_a_live_count_of_assignments_and_active_holds() {
        UUID courseId = seedCourse("Salsa inicial", CourseStatus.ACTIVE);
        Instant scheduledAt = Instant.parse("2026-08-25T22:00:00Z");
        UUID sessionId = seedSession(courseId, scheduledAt, 20);
        seedAssignment(sessionId);
        seedAssignment(sessionId);
        seedHold(sessionId, Instant.now().plusSeconds(300));
        seedHold(sessionId, Instant.now().minusSeconds(300)); // expired: must not count

        List<PhysicalSessionAvailability> result = physicalCourseAvailabilityPort.listSessions(
            courseId.toString(), scheduledAt.minusSeconds(60), scheduledAt.plusSeconds(60)
        );

        assertThat(result).hasSize(1);
        PhysicalSessionAvailability availability = result.get(0);
        assertThat(availability.capacity()).isEqualTo(20);
        assertThat(availability.assignedSpots()).isEqualTo(2);
        assertThat(availability.activeCapacityHolds()).isEqualTo(1);
        assertThat(availability.availableSpots()).isEqualTo(17);
    }

    @Test
    void find_active_by_id_returns_the_course_when_active() {
        UUID id = seedCourse("Salsa inicial", CourseStatus.ACTIVE);

        assertThat(physicalCourseAvailabilityPort.findActiveById(id.toString()))
            .isPresent()
            .get()
            .satisfies(course -> assertThat(course.title()).isEqualTo("Salsa inicial"));
    }

    @Test
    void find_active_by_id_returns_empty_when_the_course_is_inactive() {
        UUID id = seedCourse("Bachata pausada", CourseStatus.INACTIVE);

        assertThat(physicalCourseAvailabilityPort.findActiveById(id.toString())).isEmpty();
    }

    @Test
    void find_active_by_id_returns_empty_when_the_course_does_not_exist() {
        assertThat(physicalCourseAvailabilityPort.findActiveById(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void a_session_without_assignments_or_holds_reports_full_availability() {
        UUID courseId = seedCourse("Salsa inicial", CourseStatus.ACTIVE);
        Instant scheduledAt = Instant.parse("2026-08-25T22:00:00Z");
        seedSession(courseId, scheduledAt, 20);

        PhysicalSessionAvailability availability = physicalCourseAvailabilityPort.listSessions(
            courseId.toString(), scheduledAt.minusSeconds(60), scheduledAt.plusSeconds(60)
        ).get(0);

        assertThat(availability.assignedSpots()).isZero();
        assertThat(availability.activeCapacityHolds()).isZero();
        assertThat(availability.availableSpots()).isEqualTo(20);
    }

    @Test
    void excludes_sessions_scheduled_outside_the_requested_range() {
        UUID courseId = seedCourse("Salsa inicial", CourseStatus.ACTIVE);
        seedSession(courseId, Instant.parse("2026-08-25T22:00:00Z"), 20);

        List<PhysicalSessionAvailability> result = physicalCourseAvailabilityPort.listSessions(
            courseId.toString(), Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-30T00:00:00Z")
        );

        assertThat(result).isEmpty();
    }

    @Test
    void a_cancelled_session_never_appears_in_the_public_availability_read() {
        // US-PHYSICAL-006 (#43): a cancelled class must never be offered as
        // available to an external visitor -- only the management view
        // (findManaged) shows every status.
        UUID courseId = seedCourse("Salsa inicial", CourseStatus.ACTIVE);
        Instant scheduledAt = Instant.parse("2026-08-25T22:00:00Z");
        seedCancelledSession(courseId, scheduledAt, 20);

        List<PhysicalSessionAvailability> result = physicalCourseAvailabilityPort.listSessions(
            courseId.toString(), scheduledAt.minusSeconds(60), scheduledAt.plusSeconds(60)
        );

        assertThat(result).isEmpty();
    }
}
