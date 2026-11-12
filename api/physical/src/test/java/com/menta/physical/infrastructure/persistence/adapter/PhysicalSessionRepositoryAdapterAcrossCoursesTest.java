package com.menta.physical.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.physical.application.dto.SessionWithCourse;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.SessionStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityHoldJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityHoldJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import com.menta.shared.physical.AssignmentType;
import com.menta.testsupport.MySqlTestImage;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * #355 — the cross-course day read against real MySQL: half-open bounds, the join with the course,
 * the ordering and, above all, that the live counts follow the same rules as the availability reads.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PhysicalSessionRepositoryAdapterAcrossCoursesTest.JpaConfiguration.class)
@Testcontainers
class PhysicalSessionRepositoryAdapterAcrossCoursesTest {

    private static final Instant FROM = Instant.parse("2026-10-10T03:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-11T03:00:00Z");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MySqlTestImage.NAME)
        .withDatabaseName("menta_physical_sessions_across_courses_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Configuration
    @EntityScan(basePackageClasses = PhysicalSessionJpaEntity.class)
    @EnableJpaRepositories(basePackageClasses = PhysicalSessionJpaRepository.class)
    static class JpaConfiguration {
    }

    @Autowired private PhysicalSessionJpaRepository sessionRepository;
    @Autowired private PhysicalCourseJpaRepository courseRepository;
    @Autowired private PhysicalCapacityAssignmentJpaRepository assignmentRepository;
    @Autowired private PhysicalCapacityHoldJpaRepository holdRepository;

    private PhysicalSessionRepositoryAdapter adapter() {
        return new PhysicalSessionRepositoryAdapter(sessionRepository);
    }

    private UUID seedCourse(String title, int durationMinutes, CourseStatus status) {
        UUID courseId = UUID.randomUUID();
        courseRepository.saveAndFlush(new PhysicalCourseJpaEntity(
            courseId, title, "desc", UUID.randomUUID(), "Ana Perez", "SATURDAY",
            LocalTime.of(19, 0), durationMinutes, "INTERMEDIATE", 20, status, Instant.now(), Instant.now()
        ));
        return courseId;
    }

    private UUID seedCourse(String title) {
        return seedCourse(title, 60, CourseStatus.ACTIVE);
    }

    private UUID seedSession(UUID courseId, String scheduledAt, int capacity, String status) {
        UUID sessionId = UUID.randomUUID();
        sessionRepository.saveAndFlush(new PhysicalSessionJpaEntity(
            sessionId, courseId, Instant.parse(scheduledAt), capacity, status, null
        ));
        return sessionId;
    }

    private UUID seedSession(UUID courseId, String scheduledAt) {
        return seedSession(courseId, scheduledAt, 20, "SCHEDULED");
    }

    private void seedAssignment(UUID sessionId) {
        assignmentRepository.saveAndFlush(new PhysicalCapacityAssignmentJpaEntity(
            UUID.randomUUID(), sessionId, UUID.randomUUID(), AssignmentType.MONTHLY, Instant.now()
        ));
    }

    private void seedHold(UUID sessionId, Instant expiresAt, Instant convertedAt) {
        holdRepository.saveAndFlush(new PhysicalCapacityHoldJpaEntity(
            UUID.randomUUID(), sessionId, UUID.randomUUID(), expiresAt, convertedAt, Instant.now()
        ));
    }

    @Test
    void the_range_is_half_open_so_the_start_is_included_and_the_end_is_excluded() {
        UUID courseId = seedCourse("Salsa");
        seedSession(courseId, "2026-10-10T02:59:59Z");
        UUID atStart = seedSession(courseId, "2026-10-10T03:00:00Z");
        UUID lastMoment = seedSession(courseId, "2026-10-11T02:59:59Z");
        seedSession(courseId, "2026-10-11T03:00:00Z");

        List<SessionWithCourse> rows = adapter().findManagedAcrossCourses(FROM, TO);

        assertThat(rows).extracting(row -> row.session().getId().getValue()).containsExactly(atStart, lastMoment);
    }

    @Test
    void lists_the_sessions_of_every_course_with_the_course_title_and_duration() {
        UUID salsa = seedCourse("Salsa", 90, CourseStatus.ACTIVE);
        UUID bachata = seedCourse("Bachata", 45, CourseStatus.ACTIVE);
        seedSession(salsa, "2026-10-10T22:00:00Z");
        seedSession(bachata, "2026-10-10T13:00:00Z");

        List<SessionWithCourse> rows = adapter().findManagedAcrossCourses(FROM, TO);

        assertThat(rows).extracting(SessionWithCourse::courseTitle).containsExactly("Bachata", "Salsa");
        assertThat(rows).extracting(SessionWithCourse::courseDurationMinutes).containsExactly(45, 90);
        assertThat(rows.get(0).session().getCourseId().getValue()).isEqualTo(bachata);
        assertThat(rows.get(1).session().getCourseId().getValue()).isEqualTo(salsa);
    }

    @Test
    void orders_by_start_time_then_course_title_then_session_id() {
        UUID salsa = seedCourse("Salsa");
        UUID bachata = seedCourse("Bachata");
        UUID zumba = seedCourse("Zumba");
        UUID lateZumba = seedSession(zumba, "2026-10-10T23:00:00Z");
        UUID eveningSalsa = seedSession(salsa, "2026-10-10T22:00:00Z");
        UUID eveningBachata = seedSession(bachata, "2026-10-10T22:00:00Z");
        UUID morningZumba = seedSession(zumba, "2026-10-10T13:00:00Z");

        List<SessionWithCourse> rows = adapter().findManagedAcrossCourses(FROM, TO);

        assertThat(rows).extracting(row -> row.session().getId().getValue())
            .containsExactly(morningZumba, eveningBachata, eveningSalsa, lateZumba);
    }

    @Test
    void two_sessions_of_the_same_course_at_the_same_time_come_out_in_a_stable_id_order() {
        UUID courseId = seedCourse("Salsa");
        UUID first = seedSession(courseId, "2026-10-10T22:00:00Z");
        UUID second = seedSession(courseId, "2026-10-10T22:00:00Z");

        List<SessionWithCourse> rows = adapter().findManagedAcrossCourses(FROM, TO);

        List<UUID> expected = List.of(first, second).stream().sorted((a, b) -> compareAsBytes(a, b)).toList();
        assertThat(rows).extracting(row -> row.session().getId().getValue()).containsExactlyElementsOf(expected);
    }

    @Test
    void cancelled_sessions_and_sessions_of_inactive_courses_are_listed_too() {
        UUID active = seedCourse("Salsa");
        UUID inactive = seedCourse("Tango", 60, CourseStatus.INACTIVE);
        seedSession(active, "2026-10-10T13:00:00Z", 20, "CANCELLED");
        seedSession(inactive, "2026-10-10T22:00:00Z");

        List<SessionWithCourse> rows = adapter().findManagedAcrossCourses(FROM, TO);

        assertThat(rows).extracting(row -> row.session().getStatus())
            .containsExactly(SessionStatus.CANCELLED, SessionStatus.SCHEDULED);
        assertThat(rows).extracting(SessionWithCourse::courseTitle).containsExactly("Salsa", "Tango");
    }

    @Test
    void a_session_without_assignments_or_holds_reports_zero_occupied_spots() {
        seedSession(seedCourse("Salsa"), "2026-10-10T22:00:00Z", 12, "SCHEDULED");

        SessionWithCourse row = adapter().findManagedAcrossCourses(FROM, TO).get(0);

        assertThat(row.session().getCapacity()).isEqualTo(12);
        assertThat(row.session().getOccupiedSpots()).isZero();
        assertThat(row.session().getAvailableSpots()).isEqualTo(12);
    }

    @Test
    void occupied_counts_assignments_and_unexpired_unconverted_holds_only() {
        UUID courseId = seedCourse("Salsa");
        UUID session = seedSession(courseId, "2026-10-10T22:00:00Z");
        final UUID neighbour = seedSession(courseId, "2026-10-10T23:00:00Z");
        Instant now = Instant.now();
        seedAssignment(session);
        seedAssignment(session);
        seedHold(session, now.plus(1, ChronoUnit.HOURS), null);
        seedHold(session, now.minus(1, ChronoUnit.HOURS), null);
        seedHold(session, now.plus(1, ChronoUnit.HOURS), now.minus(5, ChronoUnit.MINUTES));
        seedAssignment(neighbour);

        List<SessionWithCourse> rows = adapter().findManagedAcrossCourses(FROM, TO);

        SessionWithCourse counted = rows.get(0);
        assertThat(counted.session().getId().getValue()).isEqualTo(session);
        assertThat(counted.session().getAssignedSpots()).isEqualTo(2);
        assertThat(counted.session().getActiveCapacityHolds()).isEqualTo(1);
        assertThat(counted.session().getOccupiedSpots()).isEqualTo(3);
        assertThat(rows.get(1).session().getOccupiedSpots()).isEqualTo(1);
    }

    @Test
    void occupied_agrees_with_the_availability_the_single_session_read_reports() {
        UUID session = seedSession(seedCourse("Salsa"), "2026-10-10T22:00:00Z", 10, "SCHEDULED");
        seedAssignment(session);
        seedHold(session, Instant.now().plus(1, ChronoUnit.HOURS), null);

        SessionWithCourse row = adapter().findManagedAcrossCourses(FROM, TO).get(0);

        int available = adapter().findById(row.session().getId()).orElseThrow().getAvailableSpots();
        assertThat(row.session().getCapacity() - row.session().getOccupiedSpots()).isEqualTo(available);
    }

    @Test
    void returns_an_empty_list_when_no_session_starts_in_the_range() {
        seedSession(seedCourse("Salsa"), "2026-10-12T22:00:00Z");

        assertThat(adapter().findManagedAcrossCourses(FROM, TO)).isEmpty();
    }

    /** MySQL compares {@code BINARY(16)} ids bytewise, most significant byte first. */
    private static int compareAsBytes(UUID left, UUID right) {
        int high = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }
}
