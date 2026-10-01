package com.menta.app.integration.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
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
import com.menta.billing.domain.model.PaymentProof;
import com.menta.billing.domain.model.PaymentProofId;
import com.menta.billing.domain.model.PaymentTarget;
import com.menta.billing.infrastructure.persistence.mapper.PaymentJpaMapper;
import com.menta.billing.infrastructure.persistence.mapper.PaymentProofJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.PaymentJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PaymentProofJpaRepository;
import com.menta.billing.infrastructure.proof.ProofAccessTokenSigner;
import com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase;
import com.menta.shared.domain.vo.Email;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end coverage for the token-authenticated proof-serving endpoint (#33, US-BILLING-005,
 * R3/Delta 2, design D1/C5/C6) — Testcontainers MySQL and the real Spring Security filter chain,
 * not MockMvc's standalone setup, so {@code SecurityConfig}'s new {@code permitAll} matcher is
 * exercised for real (proposal's #1 risk).
 *
 * <p>{@code Payment} and {@code PaymentProof} fixtures are persisted directly through their JPA
 * mappers/repositories, the same shape {@code PaymentProofSubmissionIntegrationTest} and {@code
 * PaymentAdminResolutionIntegrationTest} already use. Tokens are minted through the real {@link
 * ProofAccessTokenSigner} bean, wired against the test-only secret configured for the {@code
 * integration-test} profile — never hand-crafted, so a tampered case starts from a genuinely valid
 * token and mutates one byte, and an expired case is produced by signing with a {@code now} far in
 * the past rather than sleeping.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class PaymentProofIntegrationTest {

    private static final byte[] PROOF_CONTENT = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

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
        storageRoot = Files.createTempDirectory("payment-proofs-token-it");
        registry.add("billing.bank-transfer.proof.storage-root", () -> storageRoot.toString());
    }

    @Autowired private TestRestTemplate http;
    @Autowired private UserRepository userRepository;
    @Autowired private PaymentJpaRepository paymentRepository;
    @Autowired private PaymentProofJpaRepository paymentProofRepository;
    @Autowired private ProofAccessTokenSigner tokenSigner;

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

    private PaymentId seedPaymentWithProof(UUID ownerId) throws IOException {
        PaymentId paymentId = PaymentId.generate();
        Payment payment = Payment.awaitingManualVerification(
            paymentId, ownerId, Money.of(new BigDecimal("15000.00"), "ARS"), "SUB-" + paymentId,
            "0000003100000000000000", new PaymentTarget.Virtual("plan-1"), Instant.now()
        );
        paymentRepository.save(PaymentJpaMapper.toEntity(payment));

        String storageKey = paymentId.getValue() + "/proof.png";
        PaymentProof proof = new PaymentProof(
            PaymentProofId.generate(), paymentId, storageKey, "comprobante.png", "image/png",
            (long) PROOF_CONTENT.length, Instant.now()
        );
        paymentProofRepository.save(PaymentProofJpaMapper.toEntity(proof));

        Path blob = storageRoot.resolve(storageKey);
        Files.createDirectories(blob.getParent());
        Files.write(blob, PROOF_CONTENT);

        return paymentId;
    }

    private ResponseEntity<byte[]> fetchProof(PaymentId paymentId, String token) {
        String url = "/api/v1/billing/payments/" + paymentId + "/proof"
            + (token == null ? "" : "?token=" + token);
        return http.getForEntity(url, byte[].class);
    }

    /**
     * Returns the token with one bit of the HMAC flipped. Swapping the last Base64 character
     * instead is not a reliable tamper (#297): the unpadded signature's last character carries
     * only 4 significant bits, so for {@code A}-{@code D} the swap decodes to the very same bytes
     * and the verifier rightly accepts the token — about 1 run in 16.
     */
    private static String withFlippedSignatureBit(String token) {
        int separator = token.indexOf('.');
        byte[] mac = Base64.getUrlDecoder().decode(token.substring(separator + 1));
        mac[0] ^= 1;
        return token.substring(0, separator + 1)
            + Base64.getUrlEncoder().withoutPadding().encodeToString(mac);
    }

    // --- Scenario "Valid unexpired token serves the file" -------------------

    @Test
    void a_valid_signed_token_serves_the_file_within_15_minutes() throws IOException {
        PaymentId paymentId = seedPaymentWithProof(seedStudent());
        String token = tokenSigner.sign(paymentId, Instant.now());

        ResponseEntity<byte[]> response = fetchProof(paymentId, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(PROOF_CONTENT);
        assertThat(response.getHeaders().getFirst("Content-Disposition")).isEqualTo("inline");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    // --- Scenario "Absent token is rejected" ---------------------------------

    /**
     * Proposal risk #1: proves the path is genuinely matched by the new {@code SecurityConfig}
     * rule and rejected by the controller's own explicit branch — not accidentally granted by
     * {@code anyRequest()}'s fallthrough, which would never reach this controller logic at all.
     */
    @Test
    void an_anonymous_request_with_no_token_is_rejected_with_401() throws IOException {
        PaymentId paymentId = seedPaymentWithProof(seedStudent());

        ResponseEntity<byte[]> response = fetchProof(paymentId, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- Scenario "Tampered token is rejected" -------------------------------

    @Test
    void a_tampered_token_is_rejected_with_403() throws IOException {
        PaymentId paymentId = seedPaymentWithProof(seedStudent());
        String token = tokenSigner.sign(paymentId, Instant.now());
        String tampered = withFlippedSignatureBit(token);

        ResponseEntity<byte[]> response = fetchProof(paymentId, tampered);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- Scenario "Expired token is rejected" --------------------------------

    @Test
    void an_expired_token_is_rejected_with_403() throws IOException {
        PaymentId paymentId = seedPaymentWithProof(seedStudent());
        String expiredToken = tokenSigner.sign(paymentId, Instant.now().minus(Duration.ofHours(1)));

        ResponseEntity<byte[]> response = fetchProof(paymentId, expiredToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- cross-payment token ---------------------------------------------------

    @Test
    void a_token_valid_for_a_different_payment_is_rejected_with_403() throws IOException {
        PaymentId paymentId = seedPaymentWithProof(seedStudent());
        PaymentId otherPaymentId = seedPaymentWithProof(seedStudent());
        String tokenForOther = tokenSigner.sign(otherPaymentId, Instant.now());

        ResponseEntity<byte[]> response = fetchProof(paymentId, tokenForOther);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
