package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.menta.app.integration.support.AbstractBillingMySqlIntegrationTest;
import com.menta.app.outbox.OutboxReconciliationWorker;
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
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.billing.application.contract.BillingOutboxEventTypes;
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PaymentPreferencePort;
import com.menta.billing.application.port.out.PaymentProviderPort;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.infrastructure.persistence.entity.PurchaseJpaEntity;
import com.menta.billing.infrastructure.persistence.mapper.PaymentJpaMapper;
import com.menta.billing.infrastructure.persistence.mapper.PurchaseJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PurchaseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PurchaseSessionJpaRepository;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.physical.application.port.out.CheckInRateLimitPort;
import com.menta.shared.domain.vo.Email;
import com.menta.shared.outbox.OutboxStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * End-to-end coverage for {@code POST /api/v1/admin/billing/purchases/{id}/resolution} (#539).
 *
 * <p>Real Spring Security filter chain and Testcontainers MySQL, like {@link
 * PurchaseAdminExceptionIntegrationTest}. Proves that the admin decision, the audit record and the
 * {@code billing.PurchaseResolved} outbox row commit together, that the row is claimed by a
 * registered handler, and that the endpoint answers the documented error statuses.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class PurchaseAdminResolutionIntegrationTest extends AbstractBillingMySqlIntegrationTest {

    private static final String NOTE = "Reembolsado manualmente desde el panel de MercadoPago";

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private PurchaseJpaRepository purchaseRepository;
    @Autowired private PurchaseSessionJpaRepository purchaseSessionRepository;
    @Autowired private OutboxRowJpaRepository outboxRepository;
    @Autowired private OutboxReconciliationWorker outboxWorker;

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
    // Backed by a RedisTemplate this Redis-less context does not have (same rationale as siblings).
    @MockitoBean private ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase;
    @MockitoBean private CheckInRateLimitPort checkInRateLimitPort;

    @BeforeEach
    void stubCollaborators() {
        when(tokenBlacklistPort.isBlacklisted(anyString())).thenReturn(false);
        when(tokenBlacklistPort.currentTokenVersion(anyString())).thenReturn(java.util.OptionalLong.empty());
    }

    @AfterEach
    void cleanUp() {
        outboxRepository.deleteAll();
        purchaseSessionRepository.deleteAll();
        purchaseRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    // --- fixtures -----------------------------------------------------------

    private UUID seedAdmin() {
        User user = User.create(
            Email.of("admin-" + UUID.randomUUID() + "@example.com"), "irrelevant-hash", Role.ADMIN
        );
        userRepository.save(user);
        return user.getId().getValue();
    }

    private HttpHeaders headersFor(UUID userId, Role role) {
        User user = new User(
            UserId.of(userId), Email.of("token-" + UUID.randomUUID() + "@example.com"), "hash", role,
            UserStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessTokenIssuer.issue(user).token());
        return headers;
    }

    private UUID seedPurchase(UUID ownerId, boolean inException) {
        PaymentId paymentId = PaymentId.generate();
        Payment payment = Payment.awaitingManualVerification(
            paymentId, ownerId, Money.of(new BigDecimal("100.00"), "ARS"), "PHY-RES-" + paymentId,
            "0000003100000000000000", new PaymentTarget.Physical(UUID.randomUUID().toString()), Instant.now()
        );
        paymentRepository.save(PaymentJpaMapper.toEntity(payment));
        Purchase purchase = inException
            ? Purchase.exception(paymentId, List.of())
            : Purchase.pendingFulfillment(paymentId, List.of("session-1"));
        purchaseRepository.save(PurchaseJpaMapper.toEntity(purchase));
        purchaseSessionRepository.saveAll(PurchaseJpaMapper.toSessionEntities(purchase));
        return purchase.getId();
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> resolve(UUID purchaseId, Map<String, String> body, HttpHeaders headers) {
        return http.exchange(
            "/api/v1/admin/billing/purchases/" + purchaseId + "/resolution", HttpMethod.POST,
            new HttpEntity<>(body, headers), Map.class
        );
    }

    private List<OutboxRowJpaEntity> resolvedEvents() {
        return outboxRepository.findAll().stream()
            .filter(row -> BillingOutboxEventTypes.PURCHASE_RESOLVED.equals(row.getEventType()))
            .toList();
    }

    // --- happy path and idempotency -----------------------------------------

    @Test
    void an_admin_closes_an_exception_purchase_and_the_event_is_processed_by_its_handler() {
        UUID admin = seedAdmin();
        UUID purchaseId = seedPurchase(admin, true);

        ResponseEntity<Map> response = resolve(
            purchaseId, Map.of("resolution", "REFUNDED", "note", NOTE), headersFor(admin, Role.ADMIN)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
            .containsEntry("purchaseId", purchaseId.toString())
            .containsEntry("status", "RESOLVED")
            .containsEntry("resolution", "REFUNDED")
            .containsEntry("note", NOTE)
            .containsEntry("resolvedBy", admin.toString());
        PurchaseJpaEntity persisted = purchaseRepository.findById(purchaseId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo("RESOLVED");
        assertThat(persisted.getResolution()).isEqualTo("REFUNDED");
        assertThat(persisted.getResolutionNote()).isEqualTo(NOTE);
        assertThat(persisted.getResolvedBy()).isEqualTo(admin);
        assertThat(persisted.getResolvedAt()).isNotNull();
        List<OutboxRowJpaEntity> events = resolvedEvents();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getPayload()).doesNotContain(NOTE);
        assertThat(outboxWorker.process(events.getFirst())).isFalse();
        assertThat(outboxRepository.findById(events.getFirst().getId()).orElseThrow().getStatus())
            .isEqualTo(OutboxStatus.COMPLETED);
    }

    @Test
    void repeating_the_identical_resolution_is_a_no_op_that_publishes_nothing() {
        UUID admin = seedAdmin();
        UUID purchaseId = seedPurchase(admin, true);
        Map<String, String> body = Map.of("resolution", "COMPENSATED", "note", NOTE);
        HttpHeaders headers = headersFor(admin, Role.ADMIN);
        resolve(purchaseId, body, headers);
        Instant firstResolvedAt = purchaseRepository.findById(purchaseId).orElseThrow().getResolvedAt();

        ResponseEntity<Map> repeated = resolve(purchaseId, body, headers);

        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repeated.getBody()).containsEntry("resolution", "COMPENSATED");
        assertThat(resolvedEvents()).hasSize(1);
        assertThat(purchaseRepository.findById(purchaseId).orElseThrow().getResolvedAt())
            .isEqualTo(firstResolvedAt);
    }

    // --- errors --------------------------------------------------------------

    @Test
    void a_different_resolution_on_a_resolved_purchase_is_a_409() {
        UUID admin = seedAdmin();
        UUID purchaseId = seedPurchase(admin, true);
        HttpHeaders headers = headersFor(admin, Role.ADMIN);
        resolve(purchaseId, Map.of("resolution", "REFUNDED", "note", NOTE), headers);

        ResponseEntity<Map> response = resolve(
            purchaseId, Map.of("resolution", "COMPENSATED", "note", NOTE), headers
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "PURCHASE_ALREADY_RESOLVED");
        assertThat(purchaseRepository.findById(purchaseId).orElseThrow().getResolution()).isEqualTo("REFUNDED");
        assertThat(resolvedEvents()).hasSize(1);
    }

    @Test
    void a_purchase_not_in_exception_is_a_409_and_stays_untouched() {
        UUID admin = seedAdmin();
        UUID purchaseId = seedPurchase(admin, false);

        ResponseEntity<Map> response = resolve(
            purchaseId, Map.of("resolution", "REFUNDED", "note", NOTE), headersFor(admin, Role.ADMIN)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "ILLEGAL_PURCHASE_STATE_TRANSITION");
        assertThat(purchaseRepository.findById(purchaseId).orElseThrow().getStatus())
            .isEqualTo("PENDING_FULFILLMENT");
        assertThat(resolvedEvents()).isEmpty();
    }

    @Test
    void an_unknown_purchase_is_a_404() {
        UUID admin = seedAdmin();

        ResponseEntity<Map> response = resolve(
            UUID.randomUUID(), Map.of("resolution", "REFUNDED", "note", NOTE), headersFor(admin, Role.ADMIN)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", "PURCHASE_NOT_FOUND");
    }

    @Test
    void a_non_admin_is_forbidden_and_the_purchase_stays_in_exception() {
        UUID admin = seedAdmin();
        UUID purchaseId = seedPurchase(admin, true);

        ResponseEntity<Map> response = resolve(
            purchaseId, Map.of("resolution", "REFUNDED", "note", NOTE), headersFor(UUID.randomUUID(), Role.STUDENT)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(purchaseRepository.findById(purchaseId).orElseThrow().getStatus()).isEqualTo("EXCEPTION");
    }

    @Test
    void a_blank_note_is_a_400_and_the_purchase_stays_in_exception() {
        UUID admin = seedAdmin();
        UUID purchaseId = seedPurchase(admin, true);

        ResponseEntity<Map> response = resolve(
            purchaseId, Map.of("resolution", "REFUNDED", "note", "   "), headersFor(admin, Role.ADMIN)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(purchaseRepository.findById(purchaseId).orElseThrow().getStatus()).isEqualTo("EXCEPTION");
        assertThat(resolvedEvents()).isEmpty();
    }

    @Test
    void an_anonymous_caller_is_rejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = resolve(
            UUID.randomUUID(), Map.of("resolution", "REFUNDED", "note", NOTE), headers
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
