package com.menta.app.integration.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.physical.application.port.in.PhysicalCapacityAssignmentPort;
import com.menta.physical.application.usecase.AssignmentOutcome;
import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import com.menta.physical.domain.model.CourseStatus;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCapacityAssignmentJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalCourseJpaEntity;
import com.menta.physical.infrastructure.persistence.entity.PhysicalSessionJpaEntity;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCapacityAssignmentJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalCourseJpaRepository;
import com.menta.physical.infrastructure.persistence.repository.PhysicalSessionJpaRepository;
import com.menta.shared.physical.CapacityAssignmentCommand;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * RED-GREEN: end-to-end MySQL-backed coverage of the capacity invariant
 * (proposal §6 scenarios 5-6; design §5.3).
 *
 * <p>{@code capacity_available_one_insert_succeeds} and
 * {@code capacity_full_zero_inserts_and_exception} are the deterministic
 * scenarios; the cross-thread race (TASK-005 AC4 reference to
 * {@code CapacityBelowAssignedException} on UNIQUE race) is covered in
 * design §2.4, and the broader CROSS-STUDENT race losing path is covered
 * in TASK-010. Both races rely on the project's broader row-locking
 * strategy (issue #41 owner), so this file keeps scope tight to the
 * path the change actually wires.</p>
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
class AssignCapacityAdapterIntegrationTest {

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

    @Autowired private PhysicalCapacityAssignmentPort capacityPort;
    @Autowired private PhysicalCourseJpaRepository courseRepository;
    @Autowired private PhysicalSessionJpaRepository sessionRepository;
    @Autowired private PhysicalCapacityAssignmentJpaRepository assignmentRepository;

    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private CourseCatalogPort courseCatalogPort;
    @MockBean private PaymentProviderPort paymentProviderPort;

    @SuppressWarnings("rawtypes")
    @MockBean
    private RedisTemplate redisTemplate;

    @AfterEach
    void cleanUp() {
        assignmentRepository.deleteAll();
        sessionRepository.deleteAll();
        courseRepository.deleteAll();
    }

    private UUID seedSession(int capacity) {
        UUID professorId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Instant now = Instant.now();
        courseRepository.save(new PhysicalCourseJpaEntity(
            courseId, "Test course", "desc", professorId, "Maria Garcia",
            "WEDNESDAY", LocalTime.of(20, 0), 60, "INTERMEDIATE", capacity,
            CourseStatus.ACTIVE, now, now
        ));
        UUID sessionId = UUID.randomUUID();
        sessionRepository.save(new PhysicalSessionJpaEntity(
            sessionId, courseId, now.plusSeconds(86400), capacity,
            "SCHEDULED", null
        ));
        return sessionId;
    }

    private static CapacityAssignmentCommand cmd(UUID sessionId, UUID studentId) {
        return new CapacityAssignmentCommand(
            sessionId, studentId, UUID.randomUUID()
        );
    }

    @Test
    void capacity_available_one_insert_succeeds() {
        UUID sessionId = seedSession(2);

        AssignmentOutcome result = capacityPort.assign(cmd(sessionId, UUID.randomUUID()));

        assertThat(result).isEqualTo(AssignmentOutcome.ASSIGNED.INSTANCE);
        assertThat(assignmentRepository.findAll()).hasSize(1);
    }

    @Test
    void capacity_full_zero_inserts_and_exception() {
        UUID sessionId = seedSession(1);
        // Pre-seed one assignment so capacity is exhausted.
        UUID existingStudent = UUID.randomUUID();
        assignmentRepository.save(new PhysicalCapacityAssignmentJpaEntity(
            UUID.randomUUID(), sessionId, existingStudent, Instant.now()
        ));

        assertThatThrownBy(() -> capacityPort.assign(cmd(sessionId, UUID.randomUUID())))
            .isInstanceOf(CapacityBelowAssignedException.class);

        assertThat(assignmentRepository.findAll())
            .as("Capacity-full trip must NOT insert a second row")
            .hasSize(1);
    }
}
