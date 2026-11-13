package com.menta.physical.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.menta.physical.application.dto.AssignedSessionRow;
import com.menta.physical.application.dto.AttendanceHistoryRow;
import com.menta.physical.application.dto.SessionRosterRow;
import com.menta.physical.domain.model.AttendanceKind;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.domain.model.SessionId;
import com.menta.physical.infrastructure.persistence.entity.AttendanceJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.AttendanceJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import com.menta.shared.physical.AssignmentType;
import com.menta.testsupport.MySqlTestImage;
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
 * #39, US-PHYSICAL-002, design C1/C5 — real MySQL, mirrors {@code
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
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MySqlTestImage.NAME)
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
        seedAssignment(sessionId, studentId, AssignmentType.MONTHLY);
    }

    private void seedAssignment(UUID sessionId, UUID studentId, AssignmentType type) {
        assignmentRepository.saveAndFlush(
            new PhysicalCapacityAssignmentJpaEntity(
                UUID.randomUUID(), sessionId, studentId, type, Instant.now()
            )
        );
    }

    private void seedAttendance(UUID sessionId, UUID studentId, Instant recordedAt) {
        seedAttendance(sessionId, studentId, recordedAt, "QR");
    }

    private void seedAttendance(UUID sessionId, UUID studentId, Instant recordedAt, String kind) {
        attendanceRepository.saveAndFlush(new AttendanceJpaEntity(
            UUID.randomUUID(), sessionId, studentId, recordedAt, "reader-1", kind
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

        List<AttendanceHistoryRow> rows = adapter().findAttendance(
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

        List<AttendanceHistoryRow> rows = adapter().findAttendance(
            studentId, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z")
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).recordedAt()).isNull();
        assertThat(rows.get(0).attended()).isFalse();
    }

    @Test
    void half_open_bounds_include_from_and_exclude_to() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = seedCourse(UUID.randomUUID());
        UUID atStart = seedSession(courseId, Instant.parse("2026-09-01T00:00:00Z"));
        UUID atNext = seedSession(courseId, Instant.parse("2026-10-01T00:00:00Z"));
        seedAssignment(atStart, studentId);
        seedAssignment(atNext, studentId);

        List<AttendanceHistoryRow> rows = adapter().findAttendance(
            studentId, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z")
        );

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).sessionId()).isEqualTo(atStart);
    }

    /**
     * #729 — a Buenos Aires local day computed by the client, {@code [03:00Z, next day 03:00Z)}: the
     * 00:00 and 23:59 local sessions are in, the 23:59 of the previous day and the 00:00 of the next
     * one (03:00Z) are out. Nothing in the query depends on a zone.
     */
    @Test
    void a_buenos_aires_day_in_utc_keeps_the_00_30_and_23_30_local_sessions_and_nothing_around_them() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = seedCourse(UUID.randomUUID());
        UUID previousNight = seedSession(courseId, Instant.parse("2026-11-12T02:59:59Z"));
        seedAssignment(previousNight, studentId);
        UUID justAfterMidnight = seedSession(courseId, Instant.parse("2026-11-12T03:30:00Z"));
        seedAssignment(justAfterMidnight, studentId);
        UUID lateEvening = seedSession(courseId, Instant.parse("2026-11-13T02:30:00Z"));
        seedAssignment(lateEvening, studentId);
        UUID nextMidnight = seedSession(courseId, Instant.parse("2026-11-13T03:00:00Z"));
        seedAssignment(nextMidnight, studentId);

        List<AttendanceHistoryRow> rows = adapter().findAttendance(
            studentId, Instant.parse("2026-11-12T03:00:00Z"), Instant.parse("2026-11-13T03:00:00Z")
        );

        assertThat(rows).extracting(AttendanceHistoryRow::sessionId)
            .containsExactly(justAfterMidnight, lateEvening);
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

        List<AttendanceHistoryRow> rows = adapter().findAttendance(
            studentId, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z")
        );

        assertThat(rows).hasSize(2);
        assertThat(rows).anyMatch(AttendanceHistoryRow::attended);
        assertThat(rows).anyMatch(row -> !row.attended());
    }

    /**
     * #39, US-PHYSICAL-002 P2, design C1/R7 — the High-risk row's own proof: a course owned by
     * another professor MUST contribute ZERO rows to the scoped query, not a filtered subset
     * that happens to be empty by coincidence. Seeds rows from two different professors' courses
     * for the SAME student and month, then asserts the scoped method returns only the owning
     * professor's row while the unrestricted method (same fixture) returns both.
     */
    @Test
    void scoped_query_returns_zero_rows_for_a_course_owned_by_another_professor() {
        UUID studentId = UUID.randomUUID();
        UUID owningProfessor = UUID.randomUUID();
        UUID otherProfessor = UUID.randomUUID();
        UUID ownedCourse = seedCourse(owningProfessor);
        UUID otherCourse = seedCourse(otherProfessor);
        UUID ownedSession = seedSession(ownedCourse, Instant.parse("2026-09-05T22:00:00Z"));
        UUID otherSession = seedSession(otherCourse, Instant.parse("2026-09-12T22:00:00Z"));
        seedAssignment(ownedSession, studentId);
        seedAssignment(otherSession, studentId);

        List<AttendanceHistoryRow> scopedToOtherProfessor = adapter().findAttendanceInCoursesOwnedBy(
            studentId, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"), otherProfessor
        );
        List<AttendanceHistoryRow> unrestricted = adapter().findAttendance(
            studentId, Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z")
        );

        assertThat(scopedToOtherProfessor).hasSize(1);
        assertThat(scopedToOtherProfessor.get(0).sessionId()).isEqualTo(otherSession);
        assertThat(unrestricted).hasSize(2);
    }

    private UUID seedSessionWithStatus(UUID courseId, Instant scheduledAt, String status) {
        UUID sessionId = UUID.randomUUID();
        sessionRepository.saveAndFlush(
            new PhysicalSessionJpaEntity(sessionId, courseId, scheduledAt, 20, status, null)
        );
        return sessionId;
    }

    @Test
    void assigned_sessions_are_ordered_ascending_with_course_display_fields_and_half_open_bounds() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = seedCourse(UUID.randomUUID());
        UUID atFrom = seedSession(courseId, Instant.parse("2026-10-10T03:00:00Z"));
        UUID later = seedSession(courseId, Instant.parse("2026-11-20T22:00:00Z"));
        UUID atTo = seedSession(courseId, Instant.parse("2026-12-09T03:00:00Z"));
        seedAssignment(later, studentId);
        seedAssignment(atTo, studentId);
        seedAssignment(atFrom, studentId);
        seedAssignment(seedSession(courseId, Instant.parse("2026-10-10T02:59:59Z")), studentId);

        List<AssignedSessionRow> rows = adapter().findAssignedSessions(
            studentId, Instant.parse("2026-10-10T03:00:00Z"), Instant.parse("2026-12-09T03:00:00Z")
        );

        assertThat(rows).extracting(AssignedSessionRow::sessionId).containsExactly(atFrom, later);
        assertThat(rows.get(0).courseId()).isEqualTo(courseId);
        assertThat(rows.get(0).courseName()).isEqualTo("Salsa Intermedio");
        assertThat(rows.get(0).instructorName()).isEqualTo("Ana Perez");
        assertThat(rows.get(0).scheduledAt()).isEqualTo(Instant.parse("2026-10-10T03:00:00Z"));
    }

    @Test
    void assigned_sessions_carry_the_persisted_assignment_type_of_each_row() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = seedCourse(UUID.randomUUID());
        UUID monthly = seedSession(courseId, Instant.parse("2026-10-15T22:00:00Z"));
        UUID individual = seedSession(courseId, Instant.parse("2026-10-16T22:00:00Z"));
        seedAssignment(monthly, studentId, AssignmentType.MONTHLY);
        seedAssignment(individual, studentId, AssignmentType.INDIVIDUAL);

        List<AssignedSessionRow> rows = adapter().findAssignedSessions(
            studentId, Instant.parse("2026-10-10T03:00:00Z"), Instant.parse("2026-12-09T03:00:00Z")
        );

        assertThat(rows).extracting(AssignedSessionRow::sessionId).containsExactly(monthly, individual);
        assertThat(rows).extracting(AssignedSessionRow::assignmentType)
            .containsExactly(AssignmentType.MONTHLY, AssignmentType.INDIVIDUAL);
    }

    @Test
    void assigned_sessions_exclude_sessions_without_the_students_assignment_and_cancelled_ones() {
        UUID studentId = UUID.randomUUID();
        UUID courseId = seedCourse(UUID.randomUUID());
        UUID mine = seedSession(courseId, Instant.parse("2026-10-15T22:00:00Z"));
        UUID someoneElses = seedSession(courseId, Instant.parse("2026-10-16T22:00:00Z"));
        UUID cancelled = seedSessionWithStatus(courseId, Instant.parse("2026-10-17T22:00:00Z"), "CANCELLED");
        seedAssignment(mine, studentId);
        seedAssignment(someoneElses, UUID.randomUUID());
        seedAssignment(cancelled, studentId);
        // A session with no assignment at all (e.g. only a hold) has no row to list.
        seedSession(courseId, Instant.parse("2026-10-18T22:00:00Z"));

        List<AssignedSessionRow> rows = adapter().findAssignedSessions(
            studentId, Instant.parse("2026-10-10T03:00:00Z"), Instant.parse("2026-12-09T03:00:00Z")
        );

        assertThat(rows).extracting(AssignedSessionRow::sessionId).containsExactly(mine);
    }

    @Test
    void the_roster_has_one_row_per_assignment_of_the_session_and_none_of_other_sessions() {
        UUID courseId = seedCourse(UUID.randomUUID());
        UUID sessionId = seedSession(courseId, Instant.parse("2026-10-10T22:00:00Z"));
        UUID otherSessionId = seedSession(courseId, Instant.parse("2026-10-11T22:00:00Z"));
        UUID monthly = UUID.randomUUID();
        UUID individual = UUID.randomUUID();
        seedAssignment(sessionId, monthly, AssignmentType.MONTHLY);
        seedAssignment(sessionId, individual, AssignmentType.INDIVIDUAL);
        seedAssignment(otherSessionId, UUID.randomUUID(), AssignmentType.MONTHLY);

        List<SessionRosterRow> rows = adapter().findRoster(SessionId.of(sessionId));

        assertThat(rows).extracting(SessionRosterRow::studentId, SessionRosterRow::assignmentType)
            .containsExactlyInAnyOrder(
            tuple(monthly, AssignmentType.MONTHLY), tuple(individual, AssignmentType.INDIVIDUAL)
        );
    }

    @Test
    void the_roster_row_of_a_student_with_a_check_in_carries_its_instant_and_kind_and_the_others_have_none() {
        UUID courseId = seedCourse(UUID.randomUUID());
        UUID sessionId = seedSession(courseId, Instant.parse("2026-10-10T22:00:00Z"));
        final UUID otherSessionId = seedSession(courseId, Instant.parse("2026-10-11T22:00:00Z"));
        UUID present = UUID.randomUUID();
        UUID manual = UUID.randomUUID();
        UUID pending = UUID.randomUUID();
        seedAssignment(sessionId, present);
        seedAssignment(sessionId, manual);
        seedAssignment(sessionId, pending);
        seedAttendance(sessionId, present, Instant.parse("2026-10-10T21:47:12Z"), "QR");
        seedAttendance(sessionId, manual, Instant.parse("2026-10-10T22:05:30Z"), "MANUAL");
        // The pending student checked in to ANOTHER session: the join must not leak it here.
        seedAssignment(otherSessionId, pending);
        seedAttendance(otherSessionId, pending, Instant.parse("2026-10-11T21:50:00Z"), "QR");

        List<SessionRosterRow> rows = adapter().findRoster(SessionId.of(sessionId));

        assertThat(rows)
            .extracting(SessionRosterRow::studentId, SessionRosterRow::checkedInAt, SessionRosterRow::checkInKind)
            .containsExactlyInAnyOrder(
                tuple(present, Instant.parse("2026-10-10T21:47:12Z"), AttendanceKind.QR),
                tuple(manual, Instant.parse("2026-10-10T22:05:30Z"), AttendanceKind.MANUAL),
                tuple(pending, null, null)
            );
    }

    @Test
    void the_roster_of_a_session_without_assignments_or_unknown_is_empty() {
        UUID courseId = seedCourse(UUID.randomUUID());
        UUID sessionId = seedSession(courseId, Instant.parse("2026-10-10T22:00:00Z"));

        assertThat(adapter().findRoster(SessionId.of(sessionId))).isEmpty();
        assertThat(adapter().findRoster(SessionId.generate())).isEmpty();
    }
}
