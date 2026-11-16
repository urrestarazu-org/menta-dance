package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.menta.app.integration.support.AbstractBillingMySqlIntegrationTest;
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
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.infrastructure.persistence.entity.PaymentJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PaymentProofJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PhysicalCourseQuoteJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.SubscriptionJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PaymentProofJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PhysicalCourseQuoteJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionJpaRepository;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.physical.application.port.out.CheckInRateLimitPort;
import com.menta.shared.domain.vo.Email;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * End-to-end coverage for {@code GET /api/v1/billing/payments/me} (#767) over the real {@code
 * SecurityConfig} filter chain and MySQL persistence. Mirrors {@code
 * SubscriptionStatusIntegrationTest}'s harness.
 *
 * <p>Rows are seeded directly through the JPA repositories, as {@code checkout} itself writes them
 * (a Checkout Pro subscription has a provider preference, a bank-transfer one does not; a physical
 * bank transfer has the {@code PHY-BT-} reference), so one student can hold every status and both
 * payment methods without driving each provider flow.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class OwnPaymentsIntegrationTest extends AbstractBillingMySqlIntegrationTest {

    private static final String URL = "/api/v1/billing/payments/me";
    private static final String MERCHANT_ACCOUNT_ID = "merchant-own-payments";
    private static final String CBU = "0000003100010000000001";
    private static final String PROVIDER_PAYMENT_ID = "mp-own-payments-secret";
    private static final String RECONCILIATION_REASON = "provider outcome does not match expected payment fields";
    private static final String PROOF_STORAGE_KEY = "proofs/own-payments-secret.png";

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("billing.webhook.mercadopago.hmac-secret", () -> "integration-test-own-payments-secret");
        registry.add("billing.webhook.reconcile-rate-ms", () -> "999999999");
        registry.add("billing.mercadopago.merchant-account-id", () -> MERCHANT_ACCOUNT_ID);
    }

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private PaymentProofJpaRepository proofRepository;
    @Autowired private SubscriptionJpaRepository subscriptionRepository;
    @Autowired private PlanJpaRepository planRepository;
    @Autowired private PhysicalCourseQuoteJpaRepository quoteRepository;

    @MockitoBean private PaymentPreferencePort paymentPreferencePort;
    @MockitoBean private PaymentProviderPort paymentProviderPort;
    @MockitoBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockitoBean private BankTransferRateLimitPort bankTransferRateLimitPort;
    @MockitoBean private CourseCatalogPort courseCatalogPort;
    @MockitoBean private AuthDegradedGuard authDegradedGuard;
    @MockitoBean private TokenBlacklistPort tokenBlacklistPort;
    @MockitoBean private LoginRateLimitPort loginRateLimitPort;
    @MockitoBean private ActivationRateLimitPort activationRateLimitPort;
    @MockitoBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockitoBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockitoBean private ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase;
    // ADR-0044: backed by a RedisTemplate this Redis-less context does not have.
    @MockitoBean private CheckInRateLimitPort checkInRateLimitPort;

    private final List<UUID> paymentIds = new ArrayList<>();
    private final List<UUID> subscriptionIds = new ArrayList<>();
    private final List<UUID> planIds = new ArrayList<>();
    private final List<String> quoteIds = new ArrayList<>();
    private final List<UUID> proofIds = new ArrayList<>();
    private final Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(30, ChronoUnit.DAYS);

    @BeforeEach
    void stubDefensively() {
        when(tokenBlacklistPort.isBlacklisted(anyString())).thenReturn(false);
        when(tokenBlacklistPort.currentTokenVersion(anyString())).thenReturn(java.util.OptionalLong.empty());
        when(authDegradedGuard.isDegraded()).thenReturn(false);
    }

    @AfterEach
    void cleanUp() {
        proofRepository.deleteAllById(proofIds);
        subscriptionRepository.deleteAllById(subscriptionIds);
        paymentRepository.deleteAllById(paymentIds);
        quoteRepository.deleteAllById(quoteIds);
        planRepository.deleteAllById(planIds);
    }

    // --- fixtures -------------------------------------------------------------

    private UUID seedUser() {
        User user = User.create(Email.of("user-" + UUID.randomUUID() + "@example.com"), "irrelevant-hash",
            Role.STUDENT);
        userRepository.save(user);
        return user.getId().getValue();
    }

    private HttpHeaders headersFor(UUID userId) {
        User user = new User(
            UserId.of(userId), Email.of("token-" + UUID.randomUUID() + "@example.com"), "hash", Role.STUDENT,
            UserStatus.ACTIVE, java.time.LocalDateTime.now(), java.time.LocalDateTime.now()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessTokenIssuer.issue(user).token());
        return headers;
    }

    private UUID payment(
        UUID userId, String modality, String reference, String externalReference, String merchant,
        String paymentMethod, String status, int minutesAfterBase
    ) {
        UUID id = UUID.randomUUID();
        Instant createdAt = base.plus(minutesAfterBase, ChronoUnit.MINUTES);
        boolean terminal = List.of("COMPLETED", "REJECTED", "CANCELLED", "EXPIRED").contains(status);
        paymentRepository.save(new PaymentJpaEntity(
            id, userId, "COMPLETED".equals(status) ? PROVIDER_PAYMENT_ID + "-" + id : null, new BigDecimal("15000.00"),
            "ARS", externalReference + id, merchant, paymentMethod, modality, reference, status,
            "RECONCILIATION_REQUIRED".equals(status) ? RECONCILIATION_REASON : null,
            terminal ? createdAt.plusSeconds(60) : null, createdAt
        ));
        paymentIds.add(id);
        return id;
    }

    /** A plan subscription paid through Mercado Pago (with a preference) or bank transfer (without one). */
    private UUID subscriptionPayment(UUID userId, String planName, boolean mercadoPago, String status, int minutes) {
        UUID planId = UUID.randomUUID();
        planRepository.save(new PlanJpaEntity(
            planId, planName, "desc", new BigDecimal("15000.00"), "ARS", 30, false, PlanStatus.ACTIVE, "terms",
            "policy", base, base
        ));
        planIds.add(planId);
        UUID paymentId = payment(
            userId, "VIRTUAL", planId.toString(), "SUB-", mercadoPago ? MERCHANT_ACCOUNT_ID : CBU,
            mercadoPago ? "MERCADO_PAGO" : "BANK_TRANSFER", status, minutes
        );
        UUID subscriptionId = UUID.randomUUID();
        subscriptionRepository.save(new SubscriptionJpaEntity(
            subscriptionId, paymentId, userId, planId, "idem-" + subscriptionId, null, "CANCELLED", "ASSIGNED",
            null, null, mercadoPago ? "pref-" + subscriptionId : null, mercadoPago ? "https://mp.example/c" : null,
            base, null, null, null, "PAID", null, null, null, null, 0L
        ));
        subscriptionIds.add(subscriptionId);
        return paymentId;
    }

    /** A physical-course purchase paid through Mercado Pago ({@code PHY-}) or bank transfer ({@code PHY-BT-}). */
    private UUID physicalPayment(UUID userId, String courseId, boolean mercadoPago, String status, int minutes) {
        String quoteId = UUID.randomUUID().toString();
        quoteRepository.save(new PhysicalCourseQuoteJpaEntity(
            quoteId, courseId, "MONTHLY", new BigDecimal("15000.00"), "ARS", BigDecimal.ZERO, 1, 4, null,
            new BigDecimal("15000.00"), "ARS", "AVAILABLE", base, base.plusSeconds(900)
        ));
        quoteIds.add(quoteId);
        return payment(
            userId, "PHYSICAL", quoteId, mercadoPago ? "PHY-" : "PHY-BT-", mercadoPago ? MERCHANT_ACCOUNT_ID : CBU,
            mercadoPago ? "MERCADO_PAGO" : "BANK_TRANSFER", status, minutes
        );
    }

    private void proofFor(UUID paymentId) {
        UUID id = UUID.randomUUID();
        proofRepository.save(new PaymentProofJpaEntity(
            id, paymentId, PROOF_STORAGE_KEY, "comprobante-secreto.png", "image/png", 100L, base
        ));
        proofIds.add(id);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Map<String, Object>> listOwn(UUID userId, String query) {
        ResponseEntity<List> response = http.exchange(
            URL + query, HttpMethod.GET, new HttpEntity<>(headersFor(userId)), List.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static List<Object> field(List<Map<String, Object>> body, String name) {
        return body.stream().map(row -> row.get(name)).toList();
    }

    // --- scenarios --------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void every_status_and_method_is_listed_newest_first_with_what_was_bought() {
        final UUID student = seedUser();
        final UUID mpSubscription = subscriptionPayment(student, "Plan Mensual", true, "COMPLETED", 0);
        final UUID btSubscription =
            subscriptionPayment(student, "Plan Anual", false, "AWAITING_MANUAL_VERIFICATION", 1);
        final UUID mpPhysical = physicalPayment(student, "course-salsa", true, "REJECTED", 2);
        final UUID btPhysical = physicalPayment(student, "course-tango", false, "EXPIRED", 3);
        final UUID awaitingProvider = subscriptionPayment(student, "Plan Mensual", true, "AWAITING_PROVIDER", 4);
        final UUID reconciliation =
            subscriptionPayment(student, "Plan Mensual", true, "RECONCILIATION_REQUIRED", 5);
        final UUID cancelled = physicalPayment(student, "course-salsa", true, "CANCELLED", 6);
        proofFor(btSubscription);

        List<Map<String, Object>> body = listOwn(student, "");

        assertThat(field(body, "paymentId")).containsExactly(
            cancelled.toString(), reconciliation.toString(), awaitingProvider.toString(), btPhysical.toString(),
            mpPhysical.toString(), btSubscription.toString(), mpSubscription.toString()
        );
        assertThat(field(body, "status")).containsExactly(
            "CANCELLED", "RECONCILIATION_REQUIRED", "AWAITING_PROVIDER", "EXPIRED", "REJECTED",
            "AWAITING_MANUAL_VERIFICATION", "COMPLETED"
        );
        assertThat(field(body, "paymentMethod")).containsExactly(
            "MERCADO_PAGO", "MERCADO_PAGO", "MERCADO_PAGO", "BANK_TRANSFER", "MERCADO_PAGO", "BANK_TRANSFER",
            "MERCADO_PAGO"
        );
        Map<String, Object> oldest = body.get(6);
        assertThat(oldest).containsOnlyKeys(
            "paymentId", "status", "amount", "currency", "paymentMethod", "createdAt", "item"
        );
        assertThat(oldest.get("amount")).isEqualTo(15000.0);
        assertThat(oldest.get("currency")).isEqualTo("ARS");
        assertThat(Instant.parse((String) oldest.get("createdAt"))).isEqualTo(base);
        assertThat((Map<String, Object>) oldest.get("item"))
            .containsEntry("type", "SUBSCRIPTION").containsEntry("name", "Plan Mensual");
        assertThat((Map<String, Object>) body.get(3).get("item"))
            .containsEntry("type", "PHYSICAL_COURSE").containsEntry("id", "course-tango").containsEntry("name", null);
    }

    @Test
    void the_response_never_carries_provider_proof_or_reconciliation_data() {
        UUID student = seedUser();
        subscriptionPayment(student, "Plan Mensual", true, "COMPLETED", 0);
        UUID bankTransfer = subscriptionPayment(student, "Plan Anual", false, "AWAITING_MANUAL_VERIFICATION", 1);
        subscriptionPayment(student, "Plan Mensual", true, "RECONCILIATION_REQUIRED", 2);
        proofFor(bankTransfer);

        ResponseEntity<String> response = http.exchange(
            URL, HttpMethod.GET, new HttpEntity<>(headersFor(student)), String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).doesNotContain(
            PROVIDER_PAYMENT_ID, "SUB-", MERCHANT_ACCOUNT_ID, CBU, RECONCILIATION_REASON, PROOF_STORAGE_KEY,
            "comprobante-secreto", "pref-", "mp.example", student.toString()
        );
    }

    /** Explicit authorization check: two students, each one only ever sees their own payments. */
    @Test
    void a_student_never_sees_another_students_payment() {
        UUID ana = seedUser();
        UUID bruno = seedUser();
        UUID anaPayment = subscriptionPayment(ana, "Plan Mensual", true, "COMPLETED", 0);
        UUID brunoPayment = physicalPayment(bruno, "course-salsa", false, "AWAITING_MANUAL_VERIFICATION", 1);

        assertThat(field(listOwn(ana, ""), "paymentId")).containsExactly(anaPayment.toString());
        assertThat(field(listOwn(bruno, ""), "paymentId")).containsExactly(brunoPayment.toString());
    }

    @Test
    void the_limit_keeps_only_the_newest_payments() {
        UUID student = seedUser();
        subscriptionPayment(student, "Plan Mensual", true, "COMPLETED", 0);
        UUID middle = subscriptionPayment(student, "Plan Mensual", true, "COMPLETED", 1);
        UUID newest = subscriptionPayment(student, "Plan Mensual", true, "COMPLETED", 2);

        assertThat(field(listOwn(student, "?limit=2"), "paymentId"))
            .containsExactly(newest.toString(), middle.toString());
    }

    @Test
    void a_student_without_payments_gets_an_empty_list() {
        assertThat(listOwn(seedUser(), "")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", "abc", "-1"})
    @SuppressWarnings("rawtypes")
    void an_invalid_limit_is_a_400_with_a_stable_code(String limit) {
        ResponseEntity<Map> response = http.exchange(
            URL + "?limit=" + limit, HttpMethod.GET, new HttpEntity<>(headersFor(seedUser())), Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void an_unauthenticated_request_is_rejected() {
        ResponseEntity<String> response = http.exchange(URL, HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
