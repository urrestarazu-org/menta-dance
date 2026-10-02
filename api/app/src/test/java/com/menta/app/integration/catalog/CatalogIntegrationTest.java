package com.menta.app.integration.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualLessonJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualModuleJpaRepository;
import com.menta.app.integration.support.CatalogAccessMocksIntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * MySQL-backed HTTP integration coverage for the composed public catalog
 * (#95, #47, #107): list composition, the virtual rich detail path, the
 * physical detail with its session window, the 404 rules, and the
 * unauthenticated-access requirement. The
 * "an owning module fails" case (#95 acceptance criteria) is covered with
 * mocked ports instead, in {@code CatalogControllerTest} — forcing a real
 * port to throw here would mean tearing down live infrastructure mid-test,
 * which buys nothing over a fast, deterministic unit test of the same
 * branch.
 */
class CatalogIntegrationTest extends CatalogAccessMocksIntegrationTestBase {

    @Autowired private TestRestTemplate http;
    @Autowired private PhysicalCourseJpaRepository physicalCourseRepository;
    @Autowired private PhysicalSessionJpaRepository physicalSessionRepository;
    @Autowired private PhysicalCapacityAssignmentJpaRepository physicalAssignmentRepository;
    @Autowired private VirtualCourseJpaRepository virtualCourseRepository;
    @Autowired private VirtualModuleJpaRepository virtualModuleRepository;
    @Autowired private VirtualLessonJpaRepository virtualLessonRepository;

    @AfterEach
    void cleanUp() {
        virtualLessonRepository.deleteAll();
        virtualModuleRepository.deleteAll();
        // FK V7: assignments reference sessions, sessions reference courses.
        physicalAssignmentRepository.deleteAll();
        physicalSessionRepository.deleteAll();
        physicalCourseRepository.deleteAll();
        virtualCourseRepository.deleteAll();
    }

    private UUID seedPhysicalCourse(String title, CourseStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        physicalCourseRepository.save(new PhysicalCourseJpaEntity(
            id, title, "desc " + title, UUID.randomUUID(), "María García", "TUESDAY", LocalTime.of(19, 0),
            60, "BEGINNER", 20, status, now, now
        ));
        return id;
    }

    private UUID seedSession(UUID courseId, Instant scheduledAt, int capacity, String status) {
        UUID id = UUID.randomUUID();
        physicalSessionRepository.save(
            new PhysicalSessionJpaEntity(id, courseId, scheduledAt, capacity, status, null));
        return id;
    }

    private void seedAssignments(UUID sessionId, int count) {
        for (int i = 0; i < count; i++) {
            physicalAssignmentRepository.save(new PhysicalCapacityAssignmentJpaEntity(
                UUID.randomUUID(), sessionId, UUID.randomUUID(), Instant.now()));
        }
    }

    /** Day-scale offsets keep the window assertions deterministic without a clock override. */
    private static Instant inDays(long days) {
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getDetailBody(UUID courseId, String query) {
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/catalog/courses/" + courseId + query, HttpMethod.GET, null, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> sessionsOf(Map<String, Object> body) {
        Map<String, Object> physical = (Map<String, Object>) body.get("physical");
        return (List<Map<String, Object>>) physical.get("sessions");
    }

    private UUID seedVirtualCourse(String title, com.menta.virtual.domain.model.CourseStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        virtualCourseRepository.save(new VirtualCourseJpaEntity(
            id, title, "desc " + title, "descripción larga", UUID.randomUUID(), "https://cdn/img.jpg", "tango",
            "BEGINNER", false, status, now, now
        ));
        return id;
    }

    /**
     * Wired exactly the way {@code VirtualLessonJpaRepository.findByModuleIdOrderByDisplayOrderAsc}
     * expects, with a live Bunny.net-style id on every lesson — the test then
     * asserts the public detail endpoint NEVER leaks that id, which is the
     * core invariant of US-VIRTUAL-002 escenario 1.
     */
    private void seedVirtualModuleWithLessons(UUID courseId, UUID moduleId, int order, List<SeedLesson> lessons) {
        virtualModuleRepository.save(new VirtualModuleJpaEntity(moduleId, courseId, "Módulo " + order, order));
        for (SeedLesson seed : lessons) {
            virtualLessonRepository.save(new VirtualLessonJpaEntity(
                seed.id, moduleId, courseId, seed.title, "desc " + seed.title,
                seed.videoId, seed.minutes, seed.free, seed.order
            ));
        }
    }

    private record SeedLesson(UUID id, String title, String videoId, int minutes, boolean free, int order) {
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_combines_a_physical_and_a_virtual_course() {
        UUID physicalId = seedPhysicalCourse("Salsa inicial", CourseStatus.ACTIVE);
        UUID virtualId = seedVirtualCourse("Tango Básico", com.menta.virtual.domain.model.CourseStatus.PUBLISHED);

        ResponseEntity<Map> response = http.exchange("/api/v1/catalog/courses", HttpMethod.GET, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> courses = (List<Map<String, Object>>) response.getBody().get("courses");
        assertThat(courses).extracting(c -> c.get("courseId"))
            .containsExactlyInAnyOrder(physicalId.toString(), virtualId.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void get_an_unknown_course_id_returns_a_404_problem() {
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/catalog/courses/" + UUID.randomUUID(), HttpMethod.GET, null, Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_NOT_FOUND");
    }

    @Test
    @SuppressWarnings("unchecked")
    void get_a_draft_virtual_course_returns_the_same_404_as_a_missing_one() {
        // Non-enumeration discipline — US-VIRTUAL-002 escenario 4. A
        // visitor must not be able to probe status.
        UUID id = seedVirtualCourse("Tango en borrador", com.menta.virtual.domain.model.CourseStatus.DRAFT);

        ResponseEntity<Map> response =
            http.exchange("/api/v1/catalog/courses/" + id, HttpMethod.GET, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_NOT_FOUND");
    }

    @Test
    void the_public_catalog_endpoint_requires_no_authentication() {
        ResponseEntity<Map> response = http.exchange("/api/v1/catalog/courses", HttpMethod.GET, null, Map.class);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void get_resolves_a_virtual_course_with_modules_lessons_stats_and_no_video_leak() {
        UUID courseId = seedVirtualCourse("Tango Básico", com.menta.virtual.domain.model.CourseStatus.PUBLISHED);
        UUID moduleOne = UUID.randomUUID();
        seedVirtualModuleWithLessons(courseId, moduleOne, 1, List.of(
            new SeedLesson(UUID.randomUUID(), "Historia", "BUNNY-VIDEO-SECRET-1", 10, true, 1),
            new SeedLesson(UUID.randomUUID(), "Postura básica", "BUNNY-VIDEO-SECRET-2", 15, false, 2)
        ));

        ResponseEntity<Map> response =
            http.exchange("/api/v1/catalog/courses/" + courseId, HttpMethod.GET, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> body = response.getBody();
        assertThat(body.get("courseId")).isEqualTo(courseId.toString());
        assertThat(body.get("title")).isEqualTo("Tango Básico");
        assertThat(body.get("category")).isEqualTo("tango");
        assertThat(body.get("level")).isEqualTo("BEGINNER");
        assertThat(body.get("isPremium")).isEqualTo(false);
        assertThat(body.get("thumbnailUrl")).isEqualTo("https://cdn/img.jpg");

        List<Map<String, Object>> modules = (List<Map<String, Object>>) body.get("modules");
        assertThat(modules).hasSize(1);
        assertThat(modules.get(0).get("moduleId")).isEqualTo(moduleOne.toString());
        assertThat(modules.get(0).get("order")).isEqualTo(1);

        List<Map<String, Object>> lessons = (List<Map<String, Object>>) modules.get(0).get("lessons");
        assertThat(lessons).hasSize(2);
        assertThat(lessons.get(0).get("title")).isEqualTo("Historia");
        assertThat(lessons.get(0).get("duration")).isEqualTo("10:00");
        assertThat(lessons.get(0).get("isFree")).isEqualTo(true);
        assertThat(lessons.get(1).get("duration")).isEqualTo("15:00");
        assertThat(lessons.get(1).get("isFree")).isEqualTo(false);

        Map<String, Object> stats = (Map<String, Object>) body.get("stats");
        assertThat(stats.get("moduleCount")).isEqualTo(1);
        // aggregate lesson count for the seeded course is 2 (history + postura)
        assertThat(stats.get("lessonCount")).isEqualTo(2);
        assertThat(stats.get("totalDuration")).isEqualTo("25m");

        // No videoUrl / videoId anywhere on the wire — even though every
        // lesson in storage has one.
        assertThat(body).doesNotContainKey("videoUrl");
        assertThat(modules.get(0)).doesNotContainKey("videoUrl");
        assertThat(lessons.get(0)).doesNotContainKey("videoId");
        assertThat(lessons.get(0)).doesNotContainKey("videoUrl");
        assertThat(lessons.get(1)).doesNotContainKey("videoId");
        assertThat(lessons.get(1)).doesNotContainKey("videoUrl");
    }

    @Test
    @SuppressWarnings("unchecked")
    void get_a_physical_only_course_returns_its_physical_detail() {
        // #107: a physical id no longer answers 404. The real physical ports
        // resolve the seeded ACTIVE course; with no session seeded, the
        // sessions collection is an empty array, not null.
        UUID physicalId = seedPhysicalCourse("Salsa inicial", CourseStatus.ACTIVE);

        ResponseEntity<Map> response =
            http.exchange("/api/v1/catalog/courses/" + physicalId, HttpMethod.GET, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body.get("courseId")).isEqualTo(physicalId.toString());
        assertThat(body.get("modality")).isEqualTo("PHYSICAL");
        assertThat(body.get("title")).isEqualTo("Salsa inicial");
        Map<String, Object> physical = (Map<String, Object>) body.get("physical");
        assertThat(physical.get("professorName")).isEqualTo("María García");
        assertThat(physical.get("capacity")).isEqualTo(20);
        assertThat((List<Object>) physical.get("sessions")).isEmpty();
    }

    @Test
    void get_lists_only_scheduled_sessions_inside_the_window_in_ascending_order() {
        UUID courseId = seedPhysicalCourse("Salsa inicial", CourseStatus.ACTIVE);
        seedSession(courseId, Instant.now().minus(Duration.ofHours(1)), 20, "SCHEDULED");
        seedSession(courseId, inDays(3), 20, "CANCELLED");
        seedSession(courseId, inDays(31), 20, "SCHEDULED");
        UUID otherCourseId = seedPhysicalCourse("Bachata", CourseStatus.ACTIVE);
        seedSession(otherCourseId, inDays(4), 20, "SCHEDULED");
        // Inserted out of chronological order on purpose.
        UUID later = seedSession(courseId, inDays(20), 20, "SCHEDULED");
        UUID sooner = seedSession(courseId, inDays(2), 20, "SCHEDULED");

        List<Map<String, Object>> sessions = sessionsOf(getDetailBody(courseId, ""));

        assertThat(sessions).extracting(s -> s.get("sessionId"))
            .containsExactly(sooner.toString(), later.toString());
    }

    @Test
    void get_exposes_exactly_the_four_public_session_fields_with_a_utc_instant() {
        UUID courseId = seedPhysicalCourse("Salsa inicial", CourseStatus.ACTIVE);
        Instant scheduledAt = inDays(5);
        UUID sessionId = seedSession(courseId, scheduledAt, 20, "SCHEDULED");
        seedAssignments(sessionId, 3);

        Map<String, Object> session = sessionsOf(getDetailBody(courseId, "")).get(0);

        assertThat(session.keySet())
            .containsExactlyInAnyOrder("sessionId", "scheduledAt", "capacity", "availableSpots");
        assertThat(session.get("sessionId")).isEqualTo(sessionId.toString());
        assertThat(session.get("capacity")).isEqualTo(20);
        assertThat(session.get("availableSpots")).isEqualTo(17);
        String wireInstant = (String) session.get("scheduledAt");
        assertThat(wireInstant).endsWith("Z");
        assertThat(Duration.between(Instant.parse(wireInstant), scheduledAt).abs())
            .isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void get_lists_a_sold_out_session_with_zero_available_spots() {
        UUID courseId = seedPhysicalCourse("Salsa inicial", CourseStatus.ACTIVE);
        UUID soldOut = seedSession(courseId, inDays(2), 2, "SCHEDULED");
        seedAssignments(soldOut, 2);
        UUID open = seedSession(courseId, inDays(4), 2, "SCHEDULED");

        List<Map<String, Object>> sessions = sessionsOf(getDetailBody(courseId, ""));

        assertThat(sessions).extracting(s -> s.get("sessionId"))
            .containsExactly(soldOut.toString(), open.toString());
        assertThat(sessions).extracting(s -> s.get("availableSpots")).containsExactly(0, 2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void get_an_inactive_physical_course_returns_the_same_404_as_a_missing_one() {
        UUID courseId = seedPhysicalCourse("Bachata pausada", CourseStatus.INACTIVE);
        seedSession(courseId, inDays(2), 20, "SCHEDULED");

        ResponseEntity<Map> response =
            http.exchange("/api/v1/catalog/courses/" + courseId, HttpMethod.GET, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_NOT_FOUND");
    }

    @Test
    void get_ignores_from_and_to_query_parameters_on_the_detail() {
        UUID courseId = seedPhysicalCourse("Salsa inicial", CourseStatus.ACTIVE);
        UUID sooner = seedSession(courseId, inDays(1), 20, "SCHEDULED");
        UUID later = seedSession(courseId, inDays(10), 20, "SCHEDULED");
        UUID outsideWindow = seedSession(courseId, inDays(40), 20, "SCHEDULED");
        String query = "?from=" + inDays(5) + "&to=" + inDays(20);

        List<Map<String, Object>> sessions = sessionsOf(getDetailBody(courseId, query));

        assertThat(sessions).extracting(s -> s.get("sessionId"))
            .containsExactly(sooner.toString(), later.toString())
            .doesNotContain(outsideWindow.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_items_carry_no_sessions_even_when_the_course_has_scheduled_sessions() {
        UUID courseId = seedPhysicalCourse("Salsa inicial", CourseStatus.ACTIVE);
        seedSession(courseId, inDays(2), 20, "SCHEDULED");

        ResponseEntity<Map> response =
            http.exchange("/api/v1/catalog/courses", HttpMethod.GET, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> courses =
            (List<Map<String, Object>>) response.getBody().get("courses");
        assertThat(courses).extracting(c -> c.get("courseId")).containsExactly(courseId.toString());
        Map<String, Object> item = courses.get(0);
        assertThat(item).doesNotContainKey("sessions");
        assertThat((Map<String, Object>) item.get("physical")).doesNotContainKey("sessions");
    }
}
