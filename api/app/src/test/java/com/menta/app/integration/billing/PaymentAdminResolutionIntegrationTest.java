package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.AccessTokenIssuer;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentPreferencePort;
import com.menta.billing.application.port.out.PaymentProofNotificationPort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.infrastructure.persistence.entity.PlanCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanPaymentMethodJpaEntity;
import com.menta.billing.infrastructure.persistence.mapper.PaymentJpaMapper;
import com.menta.billing.infrastructure.persistence.mapper.SubscriptionJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanPaymentMethodJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionJpaRepository;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.shared.domain.vo.Email;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end coverage for admin resolution of a bank-transfer payment (#31, US-BILLING-003,
 * R8, design D1) — Testcontainers MySQL and the real Spring Security filter chain, not MockMvc's
 * standalone setup, so {@code SecurityConfig}'s generic {@code /api/v1/admin/**} rule and {@link
 * com.menta.billing.infrastructure.web.controller.PaymentAdminController}'s own defense-in-depth
 * check are both exercised for real.
 *
 * <p>{@code Payment} and {@code Subscription} fixtures are persisted directly through their JPA
 * mappers/repositories rather than driven through {@code POST /billing/subscriptions}, the same
 * shape {@code PaymentProofSubmissionIntegrationTest} already uses — the bank-transfer checkout
 * endpoint is out of this phase's scope.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class PaymentAdminResolutionIntegrationTest {

    private static final BigDecimal PLAN_PRICE = new BigDecimal("15000.00");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) throws IOException {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private SubscriptionJpaRepository subscriptionRepository;
    @Autowired private SubscriptionCourseJpaRepository subscriptionCourseRepository;
    @Autowired private PlanJpaRepository planRepository;
    @Autowired private PlanCourseJpaRepository planCourseRepository;
    @Autowired private PlanPaymentMethodJpaRepository planPaymentMethodRepository;

    @MockBean private PaymentPreferencePort paymentPreferencePort;
    @MockBean private PaymentProviderPort paymentProviderPort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private BankTransferRateLimitPort bankTransferRateLimitPort;
    @MockBean private PaymentProofNotificationPort paymentProofNotificationPort;
    @MockBean private CourseCatalogPort courseCatalogPort;
    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    // US-PHYSICAL-001: ProcessPhysicalCheckInUseCaseImpl needs a RedisTemplate its bean factory
    // would otherwise fail to resolve in this Redis-less context.
    @MockBean private ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase;

    @BeforeEach
    void stubCollaborators() {
        when(tokenBlacklistPort.isBlacklisted(any())).thenReturn(false);
        when(tokenBlacklistPort.currentTokenVersion(any())).thenReturn(java.util.OptionalLong.empty());
    }

    @AfterEach
    void cleanUp() {
        subscriptionCourseRepository.deleteAll();
        subscriptionRepository.deleteAll();
        paymentRepository.deleteAll();
        planCourseRepository.deleteAll();
        planPaymentMethodRepository.deleteAll();
        planRepository.deleteAll();
    }

    // --- fixtures -----------------------------------------------------------

    private UUID seedStudent() {
        User user = User.create(
            Email.of("student-" + UUID.randomUUID() + "@example.com"), "irrelevant-hash", Role.STUDENT
        );
        userRepository.save(user);
        return user.getId().getValue();
    }

    private UUID seedAdmin() {
        User user = User.create(
            Email.of("admin-" + UUID.randomUUID() + "@example.com"), "irrelevant-hash", Role.ADMIN
        );
        userRepository.save(user);
        return user.getId().getValue();
    }

    private UUID seedPlan(String... courseIds) {
        UUID planId = UUID.randomUUID();
        Instant now = Instant.now();
        planRepository.save(new PlanJpaEntity(
            planId, "Plan Mensual", "Acceso mensual", PLAN_PRICE, "ARS", 30, false, PlanStatus.ACTIVE,
            "Términos", "Política", now, now
        ));
        planPaymentMethodRepository.save(new PlanPaymentMethodJpaEntity(planId, "BANK_TRANSFER"));
        for (String courseId : courseIds) {
            planCourseRepository.save(new PlanCourseJpaEntity(planId, courseId));
        }
        return planId;
    }

    /** Same real-persistence shape {@code CreateBankTransferSubscriptionUseCaseImpl} produces (P2). */
    private PaymentId seedAwaitingManualVerificationPayment(UUID ownerId, UUID planId) {
        PaymentId paymentId = PaymentId.generate();
        Payment payment = Payment.awaitingManualVerification(
            paymentId, ownerId, Money.of(PLAN_PRICE, "ARS"), "SUB-" + paymentId,
            "0000003100000000000000", new PaymentTarget.Virtual(planId.toString()), Instant.now()
        );
        paymentRepository.save(PaymentJpaMapper.toEntity(payment));
        subscriptionRepository.save(SubscriptionJpaMapper.toEntity(Subscription.pendingCheckout(
            UUID.randomUUID(), paymentId, ownerId, com.menta.billing.domain.model.PlanId.of(planId),
            "idem-" + paymentId, Instant.now()
        )));
        return paymentId;
    }

    private static User principal(UUID userId, Role role) {
        return new User(
            UserId.of(userId), Email.of("token-" + UUID.randomUUID() + "@example.com"), "hash", role,
            UserStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private HttpHeaders headersFor(UUID userId, Role role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessTokenIssuer.issue(principal(userId, role)).token());
        return headers;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> approve(UUID callerId, Role role, PaymentId paymentId) {
        return http.exchange(
            "/api/v1/admin/billing/payments/" + paymentId + "/approve", HttpMethod.POST,
            new HttpEntity<>(headersFor(callerId, role)), Map.class
        );
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> reject(UUID callerId, Role role, PaymentId paymentId, String reason) {
        Map<String, Object> body = reason == null ? Map.of() : Map.of("reason", reason);
        return http.exchange(
            "/api/v1/admin/billing/payments/" + paymentId + "/reject", HttpMethod.POST,
            new HttpEntity<>(body, headersFor(callerId, role)), Map.class
        );
    }

    // --- R8 ------------------------------------------------------------------

    @Test
    void an_admin_approving_a_payment_completes_it_and_activates_the_subscription() {
        UUID owner = seedStudent();
        UUID admin = seedAdmin();
        UUID planId = seedPlan("course-1", "course-2");
        PaymentId paymentId = seedAwaitingManualVerificationPayment(owner, planId);

        ResponseEntity<Map> response = approve(admin, Role.ADMIN, paymentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paymentRepository.findById(paymentId.getValue()).orElseThrow().getStatusType())
            .isEqualTo("COMPLETED");
        assertThat(subscriptionRepository.findAll().get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void an_admin_rejecting_a_payment_rejects_it_and_cancels_the_subscription() {
        UUID owner = seedStudent();
        UUID admin = seedAdmin();
        UUID planId = seedPlan("course-1");
        PaymentId paymentId = seedAwaitingManualVerificationPayment(owner, planId);

        ResponseEntity<Map> response = reject(admin, Role.ADMIN, paymentId, "Comprobante ilegible");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paymentRepository.findById(paymentId.getValue()).orElseThrow().getStatusType())
            .isEqualTo("REJECTED");
        assertThat(subscriptionRepository.findAll().get(0).getStatus()).isEqualTo("CANCELLED");
    }

    /** C4: a second resolve on an already-resolved payment is a 409 and mutates nothing further. */
    @Test
    void a_second_resolve_after_approval_returns_409_and_mutates_nothing() {
        UUID owner = seedStudent();
        UUID admin = seedAdmin();
        UUID planId = seedPlan("course-1");
        PaymentId paymentId = seedAwaitingManualVerificationPayment(owner, planId);
        approve(admin, Role.ADMIN, paymentId);

        ResponseEntity<Map> response = reject(admin, Role.ADMIN, paymentId, "Demasiado tarde");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(paymentRepository.findById(paymentId.getValue()).orElseThrow().getStatusType())
            .isEqualTo("COMPLETED");
        assertThat(subscriptionRepository.findAll().get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void a_non_admin_cannot_approve_and_nothing_changes() {
        UUID owner = seedStudent();
        UUID planId = seedPlan("course-1");
        PaymentId paymentId = seedAwaitingManualVerificationPayment(owner, planId);

        ResponseEntity<Map> response = approve(owner, Role.STUDENT, paymentId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(paymentRepository.findById(paymentId.getValue()).orElseThrow().getStatusType())
            .isEqualTo("AWAITING_MANUAL_VERIFICATION");
    }

    @Test
    void rejecting_with_a_blank_reason_returns_400_and_changes_nothing() {
        UUID owner = seedStudent();
        UUID admin = seedAdmin();
        UUID planId = seedPlan("course-1");
        PaymentId paymentId = seedAwaitingManualVerificationPayment(owner, planId);

        ResponseEntity<Map> response = reject(admin, Role.ADMIN, paymentId, "");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(paymentRepository.findById(paymentId.getValue()).orElseThrow().getStatusType())
            .isEqualTo("AWAITING_MANUAL_VERIFICATION");
    }
}
