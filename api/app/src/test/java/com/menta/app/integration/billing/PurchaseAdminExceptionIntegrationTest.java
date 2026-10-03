package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.infrastructure.persistence.mapper.PaymentJpaMapper;
import com.menta.billing.infrastructure.persistence.mapper.PurchaseJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PurchaseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PurchaseSessionJpaRepository;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.shared.domain.vo.Email;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end coverage for the admin EXCEPTION-purchases inbox (#237, design C4, top risk).
 *
 * <p>Mirrors {@link PaymentAdminResolutionIntegrationTest}'s harness (Testcontainers MySQL + real
 * Spring Security filter chain via {@link TestRestTemplate}), but its whole reason to exist is
 * NOT the security assertions — it is proving {@link
 * com.menta.billing.infrastructure.persistence.adapter.PurchaseRepositoryAdapter#findInException}
 * actually runs with {@code Propagation.REQUIRED}, not {@code MANDATORY} (design C4). A
 * controller-unit or adapter-only test would pass under either propagation setting because
 * nothing calls it from within an ambient transaction either way; only a real HTTP round trip
 * through {@code :api:app}'s full Spring context — where the request truly starts with no
 * transaction open — actually exercises the trap.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class PurchaseAdminExceptionIntegrationTest extends AbstractBillingMySqlIntegrationTest {

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private PurchaseJpaRepository purchaseRepository;
    @Autowired private PurchaseSessionJpaRepository purchaseSessionRepository;

    @MockBean private PaymentPreferencePort paymentPreferencePort;
    @MockBean private PaymentProviderPort paymentProviderPort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private BankTransferRateLimitPort bankTransferRateLimitPort;
    @MockBean private CourseCatalogPort courseCatalogPort;
    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    // ProcessPhysicalCheckInUseCaseImpl needs a RedisTemplate this Redis-less
    // context would otherwise fail to resolve (same rationale as sibling tests).
    @MockBean private ProcessPhysicalCheckInUseCase processPhysicalCheckInUseCase;

    @BeforeEach
    void stubCollaborators() {
        when(tokenBlacklistPort.isBlacklisted(anyString())).thenReturn(false);
        when(tokenBlacklistPort.currentTokenVersion(anyString())).thenReturn(java.util.OptionalLong.empty());
    }

    @AfterEach
    void cleanUp() {
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

    /** Seeds one purchase already at {@code EXCEPTION}, real-persistence shape (design C1/C4/C7). */
    private void seedExceptionPurchase(UUID ownerId) {
        PaymentId paymentId = PaymentId.generate();
        Payment payment = Payment.awaitingManualVerification(
            paymentId, ownerId, Money.of(new BigDecimal("100.00"), "ARS"), "PHY-EXC-" + paymentId,
            "0000003100000000000000", new PaymentTarget.Physical(UUID.randomUUID().toString()), Instant.now()
        );
        paymentRepository.save(PaymentJpaMapper.toEntity(payment));
        Purchase purchase = Purchase.exception(paymentId, List.of());
        purchaseRepository.save(PurchaseJpaMapper.toEntity(purchase));
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> listExceptions(HttpHeaders headers) {
        return http.exchange(
            "/api/v1/admin/billing/purchases?status=EXCEPTION", HttpMethod.GET,
            new HttpEntity<>(headers), Map.class
        );
    }

    // --- 3.1: no ambient transaction wraps this HTTP call ---------------------

    /**
     * Proves the {@code REQUIRED} fix (design C4): the fixtures above commit and close their own
     * transactions before this call ever starts — there is NO ambient transaction open when the
     * controller invokes {@code findInException}. Under the {@code MANDATORY} trap this design
     * explicitly rejected, every such call would fail with {@code IllegalTransactionStateException}
     * and surface as a {@code 500}, never a {@code 200}.
     */
    @Test
    void an_admin_lists_exception_purchases_with_no_ambient_transaction_wrapping_the_call() {
        UUID admin = seedAdmin();
        seedExceptionPurchase(admin);

        ResponseEntity<Map> response = listExceptions(headersFor(admin, Role.ADMIN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
        assertThat(items).isNotEmpty();
    }

    // --- 3.2: anonymous caller -------------------------------------------------

    @Test
    void an_anonymous_caller_is_rejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = listExceptions(headers);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
