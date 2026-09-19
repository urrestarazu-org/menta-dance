package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import com.menta.billing.application.dto.PaymentProofNotification;
import com.menta.billing.application.dto.RateLimitDecision;
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
import com.menta.billing.infrastructure.persistence.mapper.PaymentJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PaymentProofJpaRepository;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.shared.domain.vo.Email;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end coverage for the now-reachable proof-upload flow (#31, US-BILLING-003, R5/R9,
 * design C8/C12) — Testcontainers MySQL and the real Spring Security filter chain, not MockMvc's
 * standalone setup, so {@code SecurityConfig}'s new matcher is exercised for real.
 *
 * <p>The {@code Payment} fixture is persisted directly through {@code PaymentJpaMapper} +
 * {@code PaymentJpaRepository} rather than driven through {@code POST /billing/subscriptions}:
 * that checkout endpoint's own bank-transfer routing (P2) is out of this phase's scope, and this
 * test's only concern is the proof-submission endpoint P3c adds. Seeding the row this way is the
 * same real-persistence shape the checkout flow itself produces, just without depending on a
 * different phase's HTTP surface.</p>
 *
 * <p>{@code BankTransferRateLimitPort} is mocked, mirroring every other rate-limited integration
 * test in this package ({@code SubscriptionCheckoutIntegrationTest}, {@code
 * BillingPlansIntegrationTest}) — the limiter's own Lua-script behavior already has dedicated unit
 * coverage in {@code RedisBankTransferRateLimitPortTest}; here it is a controllable double so the
 * "4th upload is rejected" scenario needs no real Redis. {@code PaymentProofNotificationPort} is
 * mocked for the same reason {@code PaymentPreferencePort} is mocked elsewhere: no real SMTP relay
 * in this harness. The filesystem storage adapter and the proof JPA repository are real — the
 * whole point of this test is proving the write path (store → save → notify → delete) end to
 * end.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class PaymentProofSubmissionIntegrationTest {

    private static final byte[] PNG_CONTENT = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("menta_test")
        .withUsername("test")
        .withPassword("test");

    private static Path storageRoot;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) throws IOException {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        storageRoot = Files.createTempDirectory("payment-proofs-it");
        registry.add("billing.bank-transfer.proof.storage-root", () -> storageRoot.toString());
    }

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessTokenIssuer accessTokenIssuer;
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private PaymentProofJpaRepository paymentProofRepository;

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
        when(tokenBlacklistPort.isBlacklisted(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(tokenBlacklistPort.currentTokenVersion(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(java.util.OptionalLong.empty());
        when(bankTransferRateLimitPort.consumeProofUpload(any())).thenReturn(RateLimitDecision.allowed());
    }

    @AfterEach
    void cleanUp() {
        paymentProofRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    // --- fixtures -----------------------------------------------------------

    private UUID seedStudent() {
        User user = User.create(
            Email.of("student-" + UUID.randomUUID() + "@example.com"), "irrelevant-hash", Role.STUDENT
        );
        userRepository.save(user);
        return user.getId().getValue();
    }

    /** Same real-persistence shape {@code CreateBankTransferSubscriptionUseCaseImpl} produces (P2). */
    private String seedAwaitingManualVerificationPayment(UUID ownerId) {
        PaymentId paymentId = PaymentId.generate();
        Payment payment = Payment.awaitingManualVerification(
            paymentId, ownerId, Money.of(new BigDecimal("15000.00"), "ARS"), "SUB-" + paymentId,
            "0000003100000000000000", new PaymentTarget.Virtual("plan-1"), Instant.now()
        );
        paymentRepository.save(PaymentJpaMapper.toEntity(payment));
        return paymentId.toString();
    }

    private static User principal(UUID userId) {
        return new User(
            UserId.of(userId), Email.of("token@example.com"), "hash", Role.STUDENT, UserStatus.ACTIVE,
            LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<java.util.Map> submitProof(
        String paymentId, UUID actingUserId, byte[] content, String filename
    ) {
        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        multipart.add("file", namedResource(content, filename));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(accessTokenIssuer.issue(principal(actingUserId)).token());

        return http.exchange(
            "/api/v1/billing/payments/" + paymentId + "/proof", HttpMethod.POST,
            new HttpEntity<>(multipart, headers), java.util.Map.class
        );
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<java.util.Map> submitProofUnauthenticated(String paymentId, byte[] content, String filename) {
        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        multipart.add("file", namedResource(content, filename));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        return http.exchange(
            "/api/v1/billing/payments/" + paymentId + "/proof", HttpMethod.POST,
            new HttpEntity<>(multipart, headers), java.util.Map.class
        );
    }

    private static ByteArrayResource namedResource(byte[] content, String filename) {
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    // --- R9: unreachable without authentication / for a non-owner ----------

    @Test
    void an_unauthenticated_submission_is_rejected_and_nothing_is_written() {
        UUID owner = seedStudent();
        String paymentId = seedAwaitingManualVerificationPayment(owner);

        ResponseEntity<java.util.Map> response = submitProofUnauthenticated(paymentId, PNG_CONTENT, "comprobante.png");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(paymentProofRepository.findAll()).isEmpty();
    }

    @Test
    void a_non_owner_submission_is_rejected_as_not_found_and_nothing_is_written() {
        UUID owner = seedStudent();
        UUID intruder = seedStudent();
        String paymentId = seedAwaitingManualVerificationPayment(owner);

        ResponseEntity<java.util.Map> response = submitProof(paymentId, intruder, PNG_CONTENT, "comprobante.png");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("PAYMENT_NOT_FOUND");
        assertThat(paymentProofRepository.findAll()).isEmpty();
    }

    // --- valid submission, replacement, budget exhaustion --------------------

    @Test
    void a_valid_submission_returns_200_stores_the_blob_and_notifies_operations() {
        UUID owner = seedStudent();
        String paymentId = seedAwaitingManualVerificationPayment(owner);

        ResponseEntity<java.util.Map> response = submitProof(paymentId, owner, PNG_CONTENT, "comprobante.png");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paymentProofRepository.findAll()).hasSize(1);
        var proof = paymentProofRepository.findAll().get(0);
        assertThat(readStoredBlob(proof.getStorageKey())).isEqualTo(PNG_CONTENT);

        org.mockito.ArgumentCaptor<PaymentProofNotification> notification =
            org.mockito.ArgumentCaptor.forClass(PaymentProofNotification.class);
        verify(paymentProofNotificationPort).notifyProofSubmitted(notification.capture());
        assertThat(notification.getValue().paymentId()).isEqualTo(UUID.fromString(paymentId));
        assertThat(notification.getValue().userId()).isEqualTo(owner);
    }

    @Test
    void a_second_submission_replaces_the_first_and_renotifies() {
        UUID owner = seedStudent();
        String paymentId = seedAwaitingManualVerificationPayment(owner);
        submitProof(paymentId, owner, PNG_CONTENT, "first.png");
        String firstStorageKey = paymentProofRepository.findAll().get(0).getStorageKey();

        ResponseEntity<java.util.Map> second = submitProof(paymentId, owner, PNG_CONTENT, "second.png");

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paymentProofRepository.findAll()).hasSize(1);
        String secondStorageKey = paymentProofRepository.findAll().get(0).getStorageKey();
        assertThat(secondStorageKey).isNotEqualTo(firstStorageKey);
        assertThat(storageRoot.resolve(firstStorageKey)).doesNotExist();
        assertThat(readStoredBlob(secondStorageKey)).isEqualTo(PNG_CONTENT);
        verify(paymentProofNotificationPort, times(2)).notifyProofSubmitted(any());
    }

    /** Design C7: the 3 proof uploads/payment/72h budget. */
    @Test
    void a_4th_upload_for_the_same_payment_is_rejected_with_429() {
        UUID owner = seedStudent();
        String paymentId = seedAwaitingManualVerificationPayment(owner);
        when(bankTransferRateLimitPort.consumeProofUpload(any()))
            .thenReturn(RateLimitDecision.allowed())
            .thenReturn(RateLimitDecision.allowed())
            .thenReturn(RateLimitDecision.allowed())
            .thenReturn(RateLimitDecision.limited(Duration.ofHours(1)));
        submitProof(paymentId, owner, PNG_CONTENT, "one.png");
        submitProof(paymentId, owner, PNG_CONTENT, "two.png");
        submitProof(paymentId, owner, PNG_CONTENT, "three.png");

        ResponseEntity<java.util.Map> fourth = submitProof(paymentId, owner, PNG_CONTENT, "four.png");

        assertThat(fourth.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(fourth.getHeaders().getFirst("Retry-After")).isEqualTo("3600");
        assertThat(paymentProofRepository.findAll()).hasSize(1);
    }

    private static byte[] readStoredBlob(String storageKey) {
        try {
            return Files.readAllBytes(storageRoot.resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
