package com.menta.app.integration.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseJpaRepository;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * MySQL-backed HTTP integration coverage for the composed public catalog
 * (#95): a physical course, a virtual course, an unknown courseId, and the
 * unauthenticated-access requirement. The "an owning module fails" case
 * (#95 acceptance criteria) is covered with mocked ports instead, in
 * {@code CatalogControllerTest} — forcing a real port to throw here would
 * mean tearing down live infrastructure mid-test, which buys nothing over a
 * fast, deterministic unit test of the same branch.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class CatalogIntegrationTest {

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

    @Autowired private TestRestTemplate http;
    @Autowired private PhysicalCourseJpaRepository physicalCourseRepository;
    @Autowired private VirtualCourseJpaRepository virtualCourseRepository;

    // Every Redis-backed port needs a mock: integration-test excludes
    // RedisAutoConfiguration and this is one shared Spring context. Mirrors
    // BillingPlansIntegrationTest's exact mock set.
    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private CourseCatalogPort courseCatalogPort;

    @AfterEach
    void cleanUp() {
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

    private UUID seedVirtualCourse(String title, com.menta.virtual.domain.model.CourseStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        virtualCourseRepository.save(new VirtualCourseJpaEntity(
            id, title, "desc " + title, "https://cdn/img.jpg", "tango", "BEGINNER",
            false, status, now, now
        ));
        return id;
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
    void get_resolves_a_physical_course_by_id() {
        UUID id = seedPhysicalCourse("Salsa inicial", CourseStatus.ACTIVE);

        ResponseEntity<Map> response =
            http.exchange("/api/v1/catalog/courses/" + id, HttpMethod.GET, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("modality")).isEqualTo("PHYSICAL");
        Map<String, Object> physicalBlock = (Map<String, Object>) response.getBody().get("physical");
        assertThat(physicalBlock.get("professorName")).isEqualTo("María García");
    }

    @Test
    @SuppressWarnings("unchecked")
    void get_resolves_a_virtual_course_by_id() {
        UUID id = seedVirtualCourse("Tango Básico", com.menta.virtual.domain.model.CourseStatus.PUBLISHED);

        ResponseEntity<Map> response =
            http.exchange("/api/v1/catalog/courses/" + id, HttpMethod.GET, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("modality")).isEqualTo("VIRTUAL");
        Map<String, Object> virtualBlock = (Map<String, Object>) response.getBody().get("virtual");
        assertThat(virtualBlock.get("category")).isEqualTo("tango");
    }

    @Test
    void get_an_unknown_course_id_returns_a_404_problem() {
        ResponseEntity<Map> response = http.exchange(
            "/api/v1/catalog/courses/" + UUID.randomUUID(), HttpMethod.GET, null, Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("COURSE_NOT_FOUND");
    }

    @Test
    void the_public_catalog_endpoint_requires_no_authentication() {
        ResponseEntity<Map> response = http.exchange("/api/v1/catalog/courses", HttpMethod.GET, null, Map.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
