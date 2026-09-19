package com.menta.app.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.app.outbox.ActivationOutboxEventHandler;
import com.menta.auth.application.dto.ActivateAccountCommand;
import com.menta.auth.application.dto.LoginCommand;
import com.menta.auth.application.dto.RegisterUserCommand;
import com.menta.auth.application.port.in.ActivateAccountUseCase;
import com.menta.auth.application.port.in.LoginUseCase;
import com.menta.auth.application.port.in.RegisterUserUseCase;
import com.menta.auth.application.port.out.ActivationNotificationPort;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.ActivationTokenGenerator;
import com.menta.auth.application.port.out.ActivationTokenHasher;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.RateLimitDecision;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.auth.domain.exception.ActivationTokenInvalidException;
import com.menta.auth.domain.exception.InvalidCredentialsException;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.auth.infrastructure.persistence.entity.OutboxRowJpaEntity;
import com.menta.auth.infrastructure.persistence.repository.ActivationTokenJpaRepository;
import com.menta.auth.infrastructure.persistence.repository.OutboxRowJpaRepository;
import com.menta.shared.domain.vo.Email;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** MySQL-backed integration coverage for the public account-activation flow. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration-test")
@Testcontainers
class AccountActivationIntegrationTest {

    private static final String EMAIL = "activation.student@example.com";
    private static final String PASSWORD = "SecurePass123!";
    private static final String RAW_TOKEN = "integration-activation-token";
    private static final String CLIENT_FINGERPRINT = "0".repeat(64);

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

    @Autowired private RegisterUserUseCase registerUserUseCase;
    @Autowired private ActivateAccountUseCase activateAccountUseCase;
    @Autowired private LoginUseCase loginUseCase;
    @Autowired private UserRepository userRepository;
    @Autowired private ActivationTokenJpaRepository activationTokenRepository;
    @Autowired private OutboxRowJpaRepository outboxRowJpaRepository;

    @MockBean private ActivationTokenGenerator activationTokenGenerator;
    @MockBean private ActivationTokenHasher activationTokenHasher;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private com.menta.auth.application.port.out.LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationNotificationPort activationNotificationPort;
    /**
     * The real password-reset limiters sit on RedisTemplate, which this slice
     * does not provide. Password reset has its own dedicated coverage; mocking
     * the ports keeps this test about the activation lifecycle.
     */
    @MockBean
    private com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort
        passwordResetRequestRateLimitPort;
    @MockBean
    private com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort
        passwordResetAttemptRateLimitPort;
    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    /**
     * api:app now also assembles api:billing, whose plans rate limiter needs
     * a RedisTemplate this slice does not provide.
     */
    @MockBean
    private com.menta.billing.application.port.out.BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean
    private com.menta.billing.application.port.out.BankTransferRateLimitPort bankTransferRateLimitPort;
    /**
     * US-PHYSICAL-001: ProcessPhysicalCheckInUseCaseImpl needs a RedisTemplate
     * its bean factory would otherwise fail to resolve in this Redis-less slice.
     */
    @MockBean
    private com.menta.physical.application.port.in.ProcessPhysicalCheckInUseCase
        processPhysicalCheckInUseCase;

    @AfterEach
    void cleanUp() {
        outboxRowJpaRepository.deleteAll();
        activationTokenRepository.deleteAll();
        userRepository.findByEmail(Email.of(EMAIL)).ifPresent(user -> userRepository.deleteById(user.getId()));
    }

    @Test
    void registrationDeliveryActivationAndLoginFormOneDurableFlow() {
        allowRegistration();
        registerUserUseCase.register(new RegisterUserCommand(EMAIL, PASSWORD, Role.STUDENT, "client-fingerprint"));

        assertThat(userRepository.findByEmail(Email.of(EMAIL))).get()
            .extracting(user -> user.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        List<OutboxRowJpaEntity> outboxRows = outboxRowJpaRepository.findAll();
        assertThat(outboxRows).singleElement()
            .extracting(OutboxRowJpaEntity::getEventType).isEqualTo("auth.AccountActivationRequested");

        new ActivationOutboxEventHandler(activationNotificationPort).handle(outboxRows.get(0));
        verify(activationNotificationPort).sendActivationEmail(any());

        assertThatThrownBy(() -> loginUseCase.execute(new LoginCommand(EMAIL, PASSWORD, CLIENT_FINGERPRINT)))
            .isInstanceOf(InvalidCredentialsException.class);
        activateAccountUseCase.activate(new ActivateAccountCommand(RAW_TOKEN));
        assertThat(loginUseCase.execute(new LoginCommand(EMAIL, PASSWORD, CLIENT_FINGERPRINT)).accessToken()).isNotBlank();
    }

    @Test
    void tokenPersistenceFailureRollsBackUserTokenAndOutbox() {
        allowRegistration();
        // A duplicate generated hash is rejected by MySQL; no partial public registration may survive.
        activationTokenRepository.save(new com.menta.auth.infrastructure.persistence.entity.ActivationTokenJpaEntity(
            java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "0".repeat(64), new byte[] {1},
            new byte[12], (short) 1, java.time.Instant.now().plusSeconds(60), java.time.Instant.now(), null, null
        ));
        when(activationTokenGenerator.generate()).thenReturn("collision-token");

        // This asserts the transactional boundary with a forced persistence failure.
        assertThatThrownBy(() -> registerUserUseCase.register(
            new RegisterUserCommand(EMAIL, PASSWORD, Role.STUDENT, "client-fingerprint")
        )).isInstanceOf(RuntimeException.class);
        assertThat(userRepository.findByEmail(Email.of(EMAIL))).isEmpty();
        assertThat(outboxRowJpaRepository.findAll()).isEmpty();
    }

    /**
     * Issue #216 (methodological finding): {@code executor.invokeAll}
     * imposes no barrier — the first activation can run to completion
     * before the second is scheduled, in which case this test proves only
     * that a sequential second activation is rejected, which is a
     * different and much weaker claim than the one its name makes. The
     * {@link CountDownLatch} starting gun is what makes the two threads
     * actually contend, following {@code SubscriptionCheckoutIntegrationTest
     * .two_simultaneous_checkouts_for_the_same_user_produce_exactly_one_subscription}.
     */
    @Test
    void concurrentDoubleActivationAllowsExactlyOneTransition() throws Exception {
        allowRegistration();
        registerUserUseCase.register(new RegisterUserCommand(EMAIL, PASSWORD, Role.STUDENT, "client-fingerprint"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Boolean> activate = () -> {
                start.await();
                try {
                    activateAccountUseCase.activate(new ActivateAccountCommand(RAW_TOKEN));
                    return true;
                } catch (ActivationTokenInvalidException expected) {
                    return false;
                }
            };
            List<Future<Boolean>> submitted = List.of(
                executor.submit(activate), executor.submit(activate)
            );
            start.countDown();
            assertThat(submitted.stream().filter(future -> get(future)).count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private void allowRegistration() {
        when(activationRateLimitPort.consume(any(), any())).thenReturn(RateLimitDecision.allowed());
        // Login failure budgets have dedicated coverage; keep this flow about
        // the register -> activate -> login lifecycle.
        when(loginRateLimitPort.check(any(), any())).thenReturn(RateLimitDecision.allowed());
        when(activationTokenGenerator.generate()).thenReturn(RAW_TOKEN);
        when(activationTokenHasher.hash(RAW_TOKEN)).thenReturn("0".repeat(64));
        when(authDegradedGuard.isDegraded()).thenReturn(false);
    }

    private static boolean get(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
