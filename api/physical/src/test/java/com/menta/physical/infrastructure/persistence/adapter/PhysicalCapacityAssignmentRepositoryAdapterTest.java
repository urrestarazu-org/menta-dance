package com.menta.physical.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.physical.application.dto.AttendanceHistoryRow;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.AttendanceJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.AttendanceJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import java.time.Instant;
import java.time.LocalTime;
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
 * #39, US-PHYSICAL-002, design C1/C5 — real MySQL 8.0, mirrors {@code
 * LessonProgressRepositoryAdapterProjectionTest} in {@code api/virtual}: half-open {@code
 * Instant} bounds and the explicit entity join with a nullable {@code professorId} parameter
 * need real database semantics, not Mockito.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PhysicalCapacityAssignmentRepositoryAdapterTest.JpaConfiguration.class)
@Testcontainers
class PhysicalCapacityAssignmentRepositoryAdapterTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_physical_attendance_test")
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
    @EntityScan(basePackageClasses = PhysicalCapacityAssignmentJpaEntity.class)
    @EnableJpaRepositories(basePackageClasses = PhysicalCapacityAssignmentJpaRepository.class)
    static class JpaConfiguration {
    }

    @Autowired private PhysicalCapacityAssignmentJpaRepository assignmentRepository;
    @Autowired private PhysicalSessionJpaRepository sessionRepository;
    @Autowired private PhysicalCourseJpaRepository courseRepository;
    @Autowired private AttendanceJpaRepository attendanceRepository;

    private PhysicalCapacityAssignmentRepositoryAdapter adapter() {
        return new PhysicalCapacityAssignmentRepositoryAdapter(assignmentRepository);
    }

    private UUID seedCourse(UUID professorId) {
        UUID courseId = UUID.randomUUID();
        courseRepository.saveAndFlush(new PhysicalCourseJpaEntity(
            courseId, "Salsa Intermedio", "desc", professorId, "Ana Perez", "MONDAY",
            LocalTime.of(19, 0), 60, "INTERMEDIATE", 20, CourseStatus.ACTIVE, Instant.now(), Instant.now()
        ));
        return courseId;
    }

    private UUID seedSession(UUID courseId, Instant scheduledAt) {
        UUID sessionId = UUID.randomUUID();
        sessionRepository.saveAndFlush(
            new PhysicalSessionJpaEntity(sessionId, courseId, scheduledAt, 20, "SCHEDULED", null)
        );
        return sessionId;
    }

    private void seedAssignment(UUID sessionId, UUID studentId) {
        assignmentRepository.saveAndFlush(
            new PhysicalCapacityAssignmentJpaEntity(UUID.randomUUID(), sessionId, studentId, Instant.now())
        );
    }

    private void seedAttendance(UUID sessionId, UUID studentId, Instant recordedAt) {
        attendanceRepository.saveAndFlush(new AttendanceJpaEntity(
            UUID.randomUUID(), sessionId, studentId, recordedAt, "reader-1", "QR"
        ));
    }

    @Test
    void orders_by_scheduled_at_then_session_id_ascending() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = seedCourse(UUID.randomUUID());
        UUID laterSession = seedSession(courseId, Instant.parse("2026-09-20T22:00:00Z"));
        UUID earlierSession = seedSession(courseId, Instant.parse("2026-09-05T22:00:00Z"));
        seedAssignment(laterSession, studentId);
        seedAssignment(earlierSession, studentId);

        List<AttendanceHistoryRow> rows = adapter().findMonthlyAttendance(
            studentId, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z")
        );

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).sessionId()).isEqualTo(earlierSession);
        assertThat(rows.get(1).sessionId()).isEqualTo(laterSession);
    }

    @Test
    void a_row_with_no_matching_attendance_survives_the_left_join_with_null_recorded_at() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = seedCourse(UUID.randomUUID());
        UUID sessionId = seedSession(courseId, Instant.parse("2026-09-10T22:00:00Z"));
        seedAssignment(sessionId, studentId);

        List<AttendanceHistoryRow> rows = adapter().findMonthlyAttendance(
            studentId, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z")
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).recordedAt()).isNull();
        assertThat(rows.get(0).attended()).isFalse();
    }

    @Test
    void half_open_bounds_include_month_start_and_exclude_month_next() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = seedCourse(UUID.randomUUID());
        UUID atStart = seedSession(courseId, Instant.parse("2026-09-01T00:00:00Z"));
        UUID atNext = seedSession(courseId, Instant.parse("2026-10-01T00:00:00Z"));
        seedAssignment(atStart, studentId);
        seedAssignment(atNext, studentId);

        List<AttendanceHistoryRow> rows = adapter().findMonthlyAttendance(
            studentId, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z")
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).sessionId()).isEqualTo(atStart);
    }

    @Test
    void unrestricted_query_returns_the_full_row_set_across_multiple_courses() {
        UUID studentId = UUID.randomUUID();
        UUID courseA = seedCourse(UUID.randomUUID());
        UUID courseB = seedCourse(UUID.randomUUID());
        UUID sessionA = seedSession(courseA, Instant.parse("2026-09-03T22:00:00Z"));
        UUID sessionB = seedSession(courseB, Instant.parse("2026-09-08T22:00:00Z"));
        seedAssignment(sessionA, studentId);
        seedAssignment(sessionB, studentId);
        seedAttendance(sessionA, studentId, Instant.parse("2026-09-03T22:05:00Z"));

        List<AttendanceHistoryRow> rows = adapter().findMonthlyAttendance(
            studentId, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z")
        );

        assertThat(rows).hasSize(2);
        assertThat(rows).anyMatch(AttendanceHistoryRow::attended);
        assertThat(rows).anyMatch(row -> !row.attended());
    }
}
