package com.menta.app.integration.physical;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.physical.application.dto.PhysicalCourseSummary;
import com.menta.physical.application.dto.PhysicalSessionAvailability;
import com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityHoldJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityHoldJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import com.menta.app.integration.support.PhysicalVirtualCatalogPortMocksIntegrationTestBase;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
class PhysicalCourseAvailabilityIntegrationTest extends PhysicalVirtualCatalogPortMocksIntegrationTestBase {

    @Autowired private PhysicalCourseAvailabilityPort physicalCourseAvailabilityPort;
    @Autowired private PhysicalCourseJpaRepository courseRepository;
    @Autowired private PhysicalSessionJpaRepository sessionRepository;
    @Autowired private PhysicalCapacityAssignmentJpaRepository assignmentRepository;
    @Autowired private PhysicalCapacityHoldJpaRepository holdRepository;

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
        holdRepository.save(new PhysicalCapacityHoldJpaEntity(
            UUID.randomUUID(), sessionId, UUID.randomUUID(), expiresAt, null, Instant.now()
        ));
    }

    private void seedConvertedHold(UUID sessionId, Instant expiresAt) {
        holdRepository.save(new PhysicalCapacityHoldJpaEntity(
            UUID.randomUUID(), sessionId, UUID.randomUUID(), expiresAt, Instant.now(), Instant.now()
        ));
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
    void a_converted_hold_is_not_subtracted_from_availability_while_an_active_one_is() {
        UUID courseId = seedCourse("Salsa inicial", CourseStatus.ACTIVE);
        Instant scheduledAt = Instant.parse("2026-08-25T22:00:00Z");
        UUID sessionId = seedSession(courseId, scheduledAt, 20);
        // Both holds are unexpired: only converted_at tells them apart.
        seedHold(sessionId, Instant.now().plusSeconds(300));
        seedConvertedHold(sessionId, Instant.now().plusSeconds(300));

        PhysicalSessionAvailability availability = physicalCourseAvailabilityPort.listSessions(
            courseId.toString(), scheduledAt.minusSeconds(60), scheduledAt.plusSeconds(60)
        ).get(0);

        assertThat(availability.activeCapacityHolds()).isEqualTo(1);
        assertThat(availability.availableSpots()).isEqualTo(19);
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
    void a_session_exactly_at_the_upper_bound_is_excluded_and_one_at_the_lower_bound_included() {
        UUID courseId = seedCourse("Salsa inicial", CourseStatus.ACTIVE);
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-01T00:00:00Z");
        UUID atFrom = seedSession(courseId, from, 20);
        UUID inside = seedSession(courseId, from.plusSeconds(3600), 20);
        seedSession(courseId, to, 20);

        List<PhysicalSessionAvailability> result = physicalCourseAvailabilityPort.listSessions(
            courseId.toString(), from, to
        );

        assertThat(result)
            .extracting(PhysicalSessionAvailability::sessionId)
            .containsExactly(atFrom.toString(), inside.toString());
    }

    @Test
    void a_zero_width_or_inverted_range_is_empty_even_with_a_session_on_the_bound() {
        UUID courseId = seedCourse("Salsa inicial", CourseStatus.ACTIVE);
        Instant at = Instant.parse("2026-09-01T00:00:00Z");
        UUID sessionId = seedSession(courseId, at, 20);

        assertThat(physicalCourseAvailabilityPort.listSessions(
            courseId.toString(), at, at
        )).isEmpty();
        assertThat(physicalCourseAvailabilityPort.listSessions(
            courseId.toString(), at.plusSeconds(3600), at
        )).isEmpty();
        // Control: the same session is returned by a range that does contain it.
        assertThat(physicalCourseAvailabilityPort.listSessions(
            courseId.toString(), at, at.plusSeconds(1)
        )).extracting(PhysicalSessionAvailability::sessionId).containsExactly(sessionId.toString());
    }

    @Test
    void sessions_are_listed_in_ascending_scheduled_order() {
        UUID courseId = seedCourse("Salsa inicial", CourseStatus.ACTIVE);
        Instant base = Instant.parse("2026-09-01T00:00:00Z");
        UUID latest = seedSession(courseId, base.plusSeconds(7200), 20);
        UUID earliest = seedSession(courseId, base, 20);
        UUID middle = seedSession(courseId, base.plusSeconds(3600), 20);

        List<PhysicalSessionAvailability> result = physicalCourseAvailabilityPort.listSessions(
            courseId.toString(), base, base.plusSeconds(86400)
        );

        assertThat(result)
            .extracting(PhysicalSessionAvailability::sessionId)
            .containsExactly(earliest.toString(), middle.toString(), latest.toString());
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

    @Test
    void batch_and_single_id_lookups_return_only_the_active_course_with_the_same_title() {
        UUID activeId = seedCourse("Salsa inicial", CourseStatus.ACTIVE);
        UUID inactiveId = seedCourse("Bachata pausada", CourseStatus.INACTIVE);
        UUID unknownId = UUID.randomUUID();
        List<String> ids =
            List.of(activeId.toString(), inactiveId.toString(), unknownId.toString());

        Map<String, PhysicalCourseSummary> batch =
            physicalCourseAvailabilityPort.findActiveByIds(ids);

        assertThat(batch).containsOnlyKeys(activeId.toString());
        PhysicalCourseSummary single =
            physicalCourseAvailabilityPort.findActiveById(activeId.toString()).orElseThrow();
        assertThat(batch.get(activeId.toString())).isEqualTo(single);
        assertThat(batch.get(activeId.toString()).title()).isEqualTo("Salsa inicial");
        assertThat(ids.stream().filter(id -> !id.equals(activeId.toString()))
            .map(physicalCourseAvailabilityPort::findActiveById)).allMatch(Optional::isEmpty);
    }

    @Test
    void batch_lookup_skips_a_malformed_id_and_keys_an_uppercase_id_by_its_input_form() {
        UUID activeId = seedCourse("Salsa inicial", CourseStatus.ACTIVE);
        String upperCaseInput = activeId.toString().toUpperCase();

        Map<String, PhysicalCourseSummary> batch =
            physicalCourseAvailabilityPort.findActiveByIds(List.of("course-1", upperCaseInput));

        assertThat(batch).containsOnlyKeys(upperCaseInput);
        assertThat(batch.get(upperCaseInput).courseId()).isEqualTo(activeId.toString());
    }
}
